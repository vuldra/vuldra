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
import externalCommandOptions
import io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import openai.OpenaiApiClient

const val SCAN_COMMAND = "scan"

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
                            scanTargetFile(targetFile, openaiApiClient)
                        } catch (e: Exception) {
                            echoError("Failed to scan file $targetFile: ${e.message}")
                            null
                        }
                    }
                }.forEach { deferred ->
                    deferred.await()?.let {
                        fileScanResults.add(it)
                        if (verbose) echo("${currentTime()} Finished scanning file ${it.filepath}")
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
            val sastRuns = mutableListOf<MinimizedRun>()

            scanners.filter { it != Scanner.OPENAI }.map { scanner ->
                async {
                    if (verbose) echo("${currentTime()} Started scanning file $targetFile with $scanner")
                    scanner.scanFile(targetFile)
                }
            }
                .forEach {
                    try {
                        val sastResult = it.await()
                        if (sastResult.isEmpty()) error("SAST scan did not produce any runs")
                        sastRuns.addAll(sastResult)
                        if (verbose) echo("${currentTime()} Finished scanning file $targetFile with ${sastResult.first().tool}")
                    } catch (e: Exception) {
                        echoError("Failed to scan file $targetFile: ${e.message}")
                    }
                }

            if (scanners.contains(Scanner.OPENAI)) {
                val targetFileContextTask = async {
                    if (verbose) echo("${currentTime()} Started gathering context of $targetFile with OpenAI API")
                    gatherContextOfTargetFile(targetFile, openaiApiClient)
                }
                val targetFileContext = targetFileContextTask.await()
                if (verbose) echo("${currentTime()} Finished gathering context of $targetFile with OpenAI API")
                if (verbose) echo("${currentTime()} Started scanning file $targetFile with OpenAI API")
                FileScanResult(
                    filepath = targetFile,
                    openaiApiClient.reasonedVulnerabilities(
                        targetFile,
                        targetFileContext.sourceCode,
                        targetFileContext.programmingLanguage,
                        targetFileContext.commonVulnerabilitiesMessage,
                        sastRuns
                    ),
                    sastRuns,
                )
            } else {
                FileScanResult(
                    filepath = targetFile,
                    sastVulnerabilities = sastRuns,
                    finalizedVulnerabilities = sastRuns.toList(),
                    isVulnerable = sastRuns.isNotEmpty(),
                )
            }
        }
    }

    private suspend fun gatherContextOfTargetFile(
        targetFile: String,
        openaiApiClient: OpenaiApiClient
    ): TargetFilContext {
        val sourceCode = readAllText(targetFile)
        val programmingLanguage = openaiApiClient.determineSourceCodeLanguage(targetFile.toPath().name, sourceCode)
            ?: error("Failed to determine programming language of $targetFile")
        val commonVulnerabilitiesMessage = openaiApiClient.determineCommonVulnerabilities(programmingLanguage)
        return TargetFilContext(sourceCode, programmingLanguage, commonVulnerabilitiesMessage)
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
            it.finalizedVulnerabilities.sumOf { run -> run.results?.size ?: 0 }
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
        fileScanResult.finalizedVulnerabilities.forEach { run ->
            run.results?.forEach { result ->
                markdown += "\n\n${result.message}"
                result.regions?.forEach {
                    markdown += generateMarkdownForCodeRegion(fileScanResult, it)
                }
            }
        }
        return markdown
    }

    private fun generateMarkdownForCodeRegion(
        vulnerabilities: FileScanResult,
        region: MinimizedRegion
    ): String {
        var markdown = ""
        var snippet: String? = null
        if (!region.snippet.isNullOrBlank()) snippet = region.snippet
        else if (region.startLine != null && region.endLine != null) {
            snippet = readLinesInRange(
                vulnerabilities.filepath,
                region.startLine.toInt(),
                region.endLine.toInt()
            )
        }
        if (!snippet.isNullOrBlank()) {
            snippet = snippet.trimIndent()
            markdown += "\n\n```\n${snippet}\n```"
        }
        return markdown
    }
}

data class TargetFilContext(
    val sourceCode: String,
    val programmingLanguage: String,
    val commonVulnerabilitiesMessage: ChatMessage
)