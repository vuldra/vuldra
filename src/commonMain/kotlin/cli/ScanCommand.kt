package cli

import cli.CliConfig.FIND
import cli.CliConfig.VULDRA_COMMAND
import cli.scanner.Scanner
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.options.varargValues
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import currentTime
import data.*
import echoError
import echoWarn
import commandOptionsAbortOnError
import io.executeExternalCommandAndCaptureOutput
import io.readAllLines
import io.runBlocking
import io.writeAllText
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import openai.OpenaiApiClient
import throwExitError

const val SCAN_COMMAND = "scan"
const val MAX_CODE_LINES_PER_REGION = 10
const val MAX_CODE_CHARACTERS_PER_LINE = 2048

val scannerChoices = Scanner.entries.map { it.name.lowercase() }.toSet()

class ScanCommand : CliktCommand(
    help = """
       Scan source code for vulnerabilities
    """.trimIndent(),
    epilog = """
        By default all files of the current directory are scanned recursively, unless arguments are provided to specify targets.
        
        Examples:
    """.trimIndent()
        .plus("\n\n$VULDRA_COMMAND $SCAN_COMMAND Vulnerable.java")
        .plus("\n\n$VULDRA_COMMAND $SCAN_COMMAND --include *.java src/main/java")
        .plus("\n\n$VULDRA_COMMAND $SCAN_COMMAND --scanners openai"),
    name = SCAN_COMMAND,
) {
    private val targets: List<String> by argument(completionCandidates = CompletionCandidates.Path).multiple()

    private val verbosity: Verbosity by option(help = "Control the verbosity of output").switch(
        "--quiet" to Verbosity.Quiet,
        "-q" to Verbosity.Quiet,
        "--verbose" to Verbosity.Verbose,
        "-v" to Verbosity.Verbose,
    ).default(Verbosity.Standard)
    private val verbose by lazy { verbosity == Verbosity.Verbose }
    private val quiet by lazy { verbosity == Verbosity.Quiet }

    private val depth: Int? by option("--depth", "-d", help = "Specify the depth of recursive directory search").int()
    private val include: String? by option("--include", "-i", help = "Specify a shell pattern to match filenames")
    private val scanners: List<Scanner> by option(
        "--scanners",
        "-s",
        completionCandidates = CompletionCandidates.Fixed(scannerChoices),
        help = "Specify which scanners to use. Defaults to try all scanners.",
    )
        .enum<Scanner> { it.name.lowercase() }
        .varargValues()
        .default(listOf(Scanner.SEMGREP, Scanner.FLAWFINDER, Scanner.OPENAI))

    private val evaluationRegex: String? by option(
        "--evaluation-regex",
        help = """
            Specify a regex to match known vulnerable filenames and evaluate scan results.
            The regex is only matched against the filename, not the full path.
            This does not affect the actual scanning process.
        """.trimIndent(),
    )
    private val output: String? by option(
        "--output",
        "-o",
        help = "Specify a full filepath to write JSON formatted scan results to.",
    )

    private val jsonPretty = Json { prettyPrint = true }
    private val terminal = Terminal()
    private val openaiApiClient by lazy { OpenaiApiClient(verbose = verbose) }

    override fun run() {
        val scanStartTime = Clock.System.now()
        val targets = targets.ifEmpty { listOf(".") }
        val targetFiles = try {
            identifyTargetFiles(targets)
        } catch (e: Exception) {
            throwExitError("Failed to identify files for scanning: ${e.message}")
        }
        val fileScanResults = runBlocking(Dispatchers.Default) {
            ensureOpenaiApiAccess()
            val sastRuns = createSastScannerTasks(targets).awaitAll().flatten()
            targetFiles.map { targetFile ->
                async {
                    if (verbose) echo("${currentTime()} Started scanning file $targetFile")
                    try {
                        scanTargetFile(targetFile, sastRuns).also {
                            if (verbose) echo("${currentTime()} Finished scanning file $targetFile")
                        }
                    } catch (e: Exception) {
                        echoError("Failed to scan file $targetFile: ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val scanEndTime = Clock.System.now()
        val aggregatedScanResult = AggregatedScanResult(
            fileScanResults,
            Statistics(targetFiles, fileScanResults, scanStartTime, scanEndTime),
        )
        try {
            evaluationRegex?.let {
                aggregatedScanResult.evaluation = Evaluation(fileScanResults, it)
            }
        } catch (e: Exception) {
            echoError("Failed to evaluate results: ${e.message}")
        }
        outputScanResults(aggregatedScanResult, targetFiles)
    }

    private suspend fun ensureOpenaiApiAccess() {
        if (scanners.contains(Scanner.OPENAI)) {
            try {
                openaiApiClient.listModels()
            } catch (e: Exception) {
                throwExitError("Failed to query OpenAI API: ${e.message}")
            }
        }
    }

    private fun identifyTargetFiles(targets: List<String>): List<String> {
        var targetFiles = listOf<String>()
        runBlocking {
            targetFiles =
                executeExternalCommandAndCaptureOutput(findCommand(targets), commandOptionsAbortOnError).split("\n")
        }
        echo(TextColors.cyan("Identified ${targetFiles.size} files to scan."))
        if (verbose) {
            echo(targetFiles.joinToString("\n"))
        }
        return targetFiles
    }

    private suspend fun scanTargetFile(
        targetFile: String,
        runs: List<MinimizedRun>,
    ): FileScanResult {
        return coroutineScope {
            val sourceCodeLines = try {
                readAllLines(targetFile, MAX_CODE_CHARACTERS_PER_LINE)
            } catch (e: Exception) {
                error("Failed to read lines: ${e.message}")
            }
            val runsRelevantToTargetFile = createRunsRelevantForTargetFile(runs, targetFile)

            if (scanners.contains(Scanner.OPENAI)) {
                if (verbose) echo("${currentTime()} Started reasoning vulnerabilities of file $targetFile with ${Scanner.OPENAI}")
                val sourceCode = sourceCodeLines.joinToString("\n")
                val fileScanResult = FileScanResult(
                    filepath = targetFile,
                    runs = runsRelevantToTargetFile,
                    reasonedVulnerabilities = openaiApiClient.reasonVulnerabilities(
                        sourceCode,
                        runsRelevantToTargetFile
                    ),
                )
                if (verbose) echo("${currentTime()} Finished reasoning vulnerabilities of file $targetFile with ${Scanner.OPENAI}")
                enrichRunsWithSourceCodeSnippets(fileScanResult.vulnerabilities, sourceCodeLines)
                fileScanResult
            } else {
                enrichRunsWithSourceCodeSnippets(runsRelevantToTargetFile, sourceCodeLines)
                FileScanResult(
                    filepath = targetFile,
                    vulnerabilities = runsRelevantToTargetFile,
                    isVulnerable = runsRelevantToTargetFile.sumOf { it.results?.size ?: 0 } > 0,
                )
            }
        }
    }

    private fun createRunsRelevantForTargetFile(
        runs: List<MinimizedRun>,
        targetFile: String
    ) = runs.map {
        val relevantRun = MinimizedRun(it.tool, listOf())
        it.results?.forEach { result ->
            val relevantLocations = mutableListOf<MinimizedLocation>()
            result.locations?.forEach { location ->
                if (location.uri != null) {
                    if (location.uri!!.toPath() == targetFile.toPath()) {
                        relevantLocations.add(
                            MinimizedLocation(
                                region = MinimizedRegion(
                                    location.region?.startLine,
                                    location.region?.endLine
                                ),
                            )
                        )
                    }
                }
            }
            if (relevantLocations.isNotEmpty()) {
                relevantRun.results = relevantRun.results?.plus(
                    MinimizedRunResult(
                        relevantLocations,
                        result.message,
                        result.ruleId,
                    )
                )
            }
        }
        relevantRun
    }.filter { it.results?.isNotEmpty() ?: false }

    private fun CoroutineScope.createSastScannerTasks(targets: List<String>) =
        scanners.filter { it != Scanner.OPENAI }.map { scanner ->
            targets.map { target ->
                async {
                    try {
                        if (!quiet) echo("${currentTime()} Started scanning target $target with $scanner")
                        val runResult = scanner.scanTarget(target)
                        if (verbose) echo(jsonPretty.encodeToString(runResult))
                        if (runResult.isEmpty()) error("Scan of $target did not produce any runs")
                        if (!quiet) echo("${currentTime()} Finished scanning target $target with ${runResult.first().tool}")
                        runResult
                    } catch (e: Exception) {
                        echoError("Failed to scan target $target with $scanner: ${e.message}")
                        emptyList()
                    }
                }
            }
        }.flatten()

    private fun enrichRunsWithSourceCodeSnippets(
        runs: List<MinimizedRun>,
        sourceCodeLines: List<String>,
    ) {
        runs.forEach {
            it.results?.forEach { result ->
                result.locations?.forEach { location ->
                    location.region?.let { region ->
                        if (region.snippet.isNullOrBlank() && region.startLine != null && region.endLine != null) {
                            if (region.endLine - region.startLine > MAX_CODE_LINES_PER_REGION) {
                                echoWarn("A discovered vulnerability region has more than $MAX_CODE_LINES_PER_REGION lines. Saving only the first $MAX_CODE_LINES_PER_REGION lines.")
                                region.snippet = sourceCodeLines.subList(
                                    region.startLine.toInt() - 1,
                                    region.startLine.toInt() - 1 + MAX_CODE_LINES_PER_REGION
                                ).joinToString("\n")
                            } else {
                                region.snippet = sourceCodeLines.subList(
                                    region.startLine.toInt() - 1,
                                    region.endLine.toInt()
                                ).joinToString("\n")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findCommand(targets: List<String>): List<String> {
        val args = mutableListOf(FIND)
        args += targets
        args += listOf("-type", "f")
        args += listOf("-mindepth", "0")
        if (depth != null) {
            args += "-maxdepth"
            args += (depth!! + 1).toString()
        }
        if (include != null) {
            args += "-name"
            args += include!!
        } else {
            // exclude dotfiles and dot directories
            args += listOf("!", "-path", "*/.*")
        }
        return args.also { if (verbose) println("$ $it") }
    }

    private fun outputScanResults(
        aggregatedScanResult: AggregatedScanResult,
        targetFiles: List<String>
    ) {
        val aggregatedScanResultJson = jsonPretty.encodeToString(aggregatedScanResult)
        if (verbose) echo(TextColors.cyan(aggregatedScanResultJson))
        output?.let {
            try {
                writeAllText(it, aggregatedScanResultJson)
            } catch (e: Exception) {
                echoError("Failed to write scan results to $it: ${e.message}")
            }
        }

        val fileScanResults = aggregatedScanResult.fileScanResults
        outputMarkdownHeadingAndSummary(fileScanResults, targetFiles)
        aggregatedScanResult.evaluation?.let {
            terminal.println(it.generateTerminalTable())
        }
        fileScanResults.forEach {
            if (it.isVulnerable) {
                outputMarkdownForFileScanResult(it)
            }
        }
    }

    private fun outputMarkdownHeadingAndSummary(
        fileScanResults: List<FileScanResult>,
        targetFiles: List<String>
    ): String {
        val vulnerabilityCount = fileScanResults.sumOf {
            it.vulnerabilities.sumOf { run -> run.results?.size ?: 0 }
        }
        val scanningSummaryMessage =
            "Scanning completed! Found $vulnerabilityCount vulnerabilities in ${targetFiles.size} files."

        var markdown = "# Vulnerabilities"
        markdown += "\n\n".plus(
            if (vulnerabilityCount == 0) TextColors.green(scanningSummaryMessage)
            else TextColors.yellow(scanningSummaryMessage)
        )
        terminal.println(Markdown(markdown))
        return markdown
    }

    private fun outputMarkdownForFileScanResult(fileScanResult: FileScanResult) {
        var markdown = "\n\n## ${fileScanResult.filepath}"
        fileScanResult.vulnerabilities.forEach { run ->
            run.results?.forEach { result ->
                markdown += "\n\n${result.message}"
                result.locations?.forEach { location ->
                    if (!location.region?.snippet.isNullOrBlank()) {
                        markdown += "\n\n```\n${location.region!!.snippet}\n```"
                    }
                }
            }
        }
        try {
            terminal.println(Markdown(markdown))
        } catch (e: Exception) {
            echoError("Failed to output markdown for file ${fileScanResult.filepath}: ${e.message}")
        }
    }
}

enum class Verbosity {
    Quiet,
    Standard,
    Verbose,
}