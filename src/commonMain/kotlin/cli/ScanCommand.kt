package cli

import cli.CliConfig.FIND
import cli.CliConfig.VULDRA_COMMAND
import cli.scanner.Scanner
import com.aallam.openai.api.chat.ChatMessage
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
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
import externalCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.readAllLines
import io.runBlocking
import io.writeAllText
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import openai.OpenaiApiClient

const val SCAN_COMMAND = "scan"
const val MAX_CODE_REGIONS_PER_VULNERABILITY = 3
const val MAX_CODE_LINES_PER_REGION = 30
const val MAX_CODE_CHARACTERS_PER_LINE = 512

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
    val targets: List<String> by argument(completionCandidates = CompletionCandidates.Path).multiple()

    val verbose: Boolean by option("-v", "--verbose", help = "Verbose logging").flag(defaultForHelp = "disabled")
    val depth: Int? by option("--depth", "-d", help = "Specify the depth of recursive directory search").int()
    val include: String? by option("--include", "-i", help = "Specify a shell pattern to match filenames")
    val scanners: List<Scanner> by option(
        "--scanners",
        "-s",
        completionCandidates = CompletionCandidates.Fixed(scannerChoices),
        help = "Specify which scanners to use. Defaults to try all scanners.",
    )
        .enum<Scanner> { it.name.lowercase() }
        .varargValues()
        .default(listOf(Scanner.SEMGREP, Scanner.OPENAI))
    val evaluationRegex: String? by option(
        "--evaluation-regex",
        help = """
            Specify a regex to match known vulnerable filenames and evaluate scan results.
            The regex is only matched against the filename, not the full path.
            This does not affect the actual scanning process.
        """.trimIndent(),
    )
    val output: String? by option(
        "--output",
        "-o",
        help = "Specify a full filepath to write JSON formatted scan results to.",
    )

    private val jsonPretty = Json { prettyPrint = true }

    override fun run() {
        val scanStartTime = Clock.System.now()
        val targetFiles = try {
            identifyTargetFiles()
        } catch (e: Exception) {
            echoError("Failed to identify files for scanning: ${e.message}")
            return
        }
        val openaiApiClient = OpenaiApiClient(verbose = verbose)
        val fileScanResults = mutableListOf<FileScanResult>()
        runBlocking {
            if (scanners.contains(Scanner.OPENAI)) {
                try {
                    openaiApiClient.listModels()
                } catch (e: Exception) {
                    echoError("Failed to query OpenAI API: ${e.message}")
                    return@runBlocking
                }
            }
            coroutineScope {
                targetFiles.map { targetFile ->
                    async {
                        if (verbose) echo("${currentTime()} Started scanning file $targetFile")
                        try {
                            scanTargetFile(targetFile, openaiApiClient).also {
                                if (verbose) echo("${currentTime()} Finished scanning file $targetFile")
                            }
                        } catch (e: Exception) {
                            echoError("Failed to scan file $targetFile: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().forEach {
                    it?.let {
                        fileScanResults.add(it)
                    }
                }
            }
        }
        val scanEndTime = Clock.System.now()
        try {
            val aggregatedScanResult = AggregatedScanResult(
                Statistics(targetFiles, fileScanResults, scanStartTime, scanEndTime),
                fileScanResults,
            )
            evaluationRegex?.let {
                aggregatedScanResult.evaluation = Evaluation(fileScanResults, it)
            }
            outputScanResults(aggregatedScanResult, targetFiles)
        } catch (e: Exception) {
            echoError("Failed to output scan results: ${e.message}")
        }
    }

    private fun identifyTargetFiles(): List<String> {
        var targetFiles = listOf<String>()
        runBlocking {
            targetFiles =
                executeExternalCommandAndCaptureOutput(findCommand(targets), externalCommandOptions).split("\n")
        }
        echo(TextColors.cyan("Identified ${targetFiles.size} files to scan."))
        if (verbose) {
            echo(targetFiles.joinToString("\n"))
        }
        return targetFiles
    }

    private suspend fun scanTargetFile(
        targetFile: String,
        openaiApiClient: OpenaiApiClient
    ): FileScanResult {
        return coroutineScope {
            val sourceCodeLines = try {
                readAllLines(targetFile, MAX_CODE_CHARACTERS_PER_LINE)
            } catch (e: Exception) {
                error("Failed to read lines: ${e.message}")
            }
            val asyncScannerTasks = createAsyncSastScannerTasks(targetFile)
            checkAndAddAsyncOpenaiTask(asyncScannerTasks, targetFile, sourceCodeLines, openaiApiClient)
            val runs = executeAsyncScannerTasks(asyncScannerTasks, targetFile)
            enrichRunsWithSourceCodeSnippets(runs, sourceCodeLines)

            if (scanners.contains(Scanner.OPENAI)) {
                if (verbose) echo("${currentTime()} Started reasoning vulnerabilities of file $targetFile with ${Scanner.OPENAI}")
                val fileScanResult = FileScanResult(
                    filepath = targetFile,
                    runs = runs,
                    reasonedVulnerabilities = openaiApiClient.reasonVulnerabilities(runs),
                )
                if (verbose) echo("${currentTime()} Finished reasoning vulnerabilities of file $targetFile with ${Scanner.OPENAI}")
                enrichRunsWithSourceCodeSnippets(fileScanResult.vulnerabilities, sourceCodeLines)
                fileScanResult
            } else {
                FileScanResult(
                    filepath = targetFile,
                    runs = runs,
                    vulnerabilities = runs,
                    isVulnerable = runs.sumOf { it.results?.size ?: 0 } > 0,
                )
            }
        }
    }

    private fun CoroutineScope.createAsyncSastScannerTasks(targetFile: String) =
        scanners.filter { it != Scanner.OPENAI }.map { scanner ->
            async {
                if (verbose) echo("${currentTime()} Started scanning file $targetFile with $scanner")
                scanner.scanFile(targetFile)
            }
        }.toMutableList()

    private fun CoroutineScope.checkAndAddAsyncOpenaiTask(
        asyncRunTasks: MutableList<Deferred<List<MinimizedRun>>>,
        targetFile: String,
        sourceCodeLines: List<String>,
        openaiApiClient: OpenaiApiClient
    ) {
        if (scanners.contains(Scanner.OPENAI)) {
            asyncRunTasks.add(
                async {
                    if (verbose) echo("${currentTime()} Started scanning file $targetFile with ${Scanner.OPENAI}")
                    listOf(openaiApiClient.findVulnerabilities(sourceCodeLines.joinToString("\n")))
                }
            )
        }
    }

    private suspend fun executeAsyncScannerTasks(
        tasks: MutableList<Deferred<List<MinimizedRun>>>,
        targetFile: String
    ) = tasks.mapNotNull {
        try {
            val runResult = it.await()
            if (runResult.isEmpty()) error("Scan of $targetFile did not produce any runs")
            if (verbose) echo("${currentTime()} Finished scanning file $targetFile with ${runResult.first().tool}")
            runResult
        } catch (e: Exception) {
            echoError("Failed to scan file $targetFile: ${e.message}")
            null
        }
    }.flatten()

    private fun enrichRunsWithSourceCodeSnippets(
        runs: List<MinimizedRun>,
        sourceCodeLines: List<String>,
    ) {
        runs.forEach {
            it.results?.forEach { result ->
                if ((result.regions?.size ?: 0) > MAX_CODE_REGIONS_PER_VULNERABILITY) {
                    result.regions = result.regions?.subList(0, MAX_CODE_REGIONS_PER_VULNERABILITY)
                    echoWarn("A discovered vulnerability has more than $MAX_CODE_REGIONS_PER_VULNERABILITY code regions. Saving only the first $MAX_CODE_REGIONS_PER_VULNERABILITY regions.")
                }
                result.regions?.forEach { region ->
                    if (region.snippet.isNullOrBlank() && region.startLine != null && region.endLine != null) {
                        if (region.endLine - region.startLine > MAX_CODE_LINES_PER_REGION) {
                            echoWarn("A discovered vulnerability region has more than $MAX_CODE_LINES_PER_REGION lines. Saving only the first $MAX_CODE_LINES_PER_REGION lines.")
                        }
                        region.snippet = sourceCodeLines.subList(
                            region.startLine.toInt() - 1,
                            region.endLine.toInt()
                        ).joinToString("\n")
                    }
                }
            }
        }
    }

    private fun findCommand(targets: List<String>): List<String> {
        val args = mutableListOf(FIND)

        if (targets.isEmpty()) args += "."
        else args += targets

        args += listOf("-type", "f")
        args += listOf("-mindepth", "0")
        if (depth != null) {
            args += "-maxdepth"
            args += (depth!! + 1).toString()
        }
        if (include != null) {
            args += "-name"
            args += include!!
        }
        return args.also { if (verbose) println("$ $it") }
    }

    private fun outputScanResults(
        aggregatedScanResult: AggregatedScanResult,
        targetFiles: List<String>
    ) {
        val aggregatedScanResultJson = jsonPretty.encodeToString(aggregatedScanResult)
        if (verbose) echo(TextColors.cyan(aggregatedScanResultJson))

        val fileScanResults = aggregatedScanResult.fileScanResults
        outputMarkdownHeadingAndSummary(fileScanResults, targetFiles)
        aggregatedScanResult.evaluation?.let {
            Terminal().println(it.generateTerminalTable())
        }

        var markdown = ""
        fileScanResults.forEach {
            if (it.isVulnerable) {
                markdown += generateMarkdownForFileScanResult(it)
            }
        }
        echo(Markdown(markdown))
        output?.let {
            try {
                writeAllText(it, aggregatedScanResultJson)
            } catch (e: Exception) {
                echoError("Failed to write scan results to $it: ${e.message}")
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
        echo(Markdown(markdown))
        return markdown
    }

    private fun generateMarkdownForFileScanResult(fileScanResult: FileScanResult): String {
        var markdown = "\n\n## ${fileScanResult.filepath}"
        fileScanResult.vulnerabilities.forEach { run ->
            run.results?.forEach { result ->
                markdown += "\n\n${result.message}"
                result.regions?.forEach {region ->
                    region.snippet?.let {
                        markdown += "\n\n```\n${it}\n```"
                    }
                }
            }
        }
        return markdown
    }
}

data class TargetFilContext(
    val sourceCode: String,
    val programmingLanguage: String,
    val commonVulnerabilitiesMessage: ChatMessage
)