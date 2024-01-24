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
import currentTime
import data.*
import echoError
import externalCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.readAllText
import io.readLinesInRange
import io.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        .plus("\n$VULDRA_COMMAND $SCAN_COMMAND Vulnerable.java")
        .plus("\n$VULDRA_COMMAND $SCAN_COMMAND --pattern *.java src/main/java")
        .plus("\n$VULDRA_COMMAND $SCAN_COMMAND --scanners semgrep openai"),
    name = SCAN_COMMAND
) {
    val targets: List<String> by argument(completionCandidates = CompletionCandidates.Path).multiple()

    val verbose: Boolean by option("-v", "--verbose", help = "Verbose logging").flag(defaultForHelp = "disabled")
    val depth: Int? by option("--depth", "-d", help = "Specify the depth of recursive directory search").int()
    val include: String? by option("--include", "-i", help = "Specify a shell pattern to match filenames")
    val scanners: List<Scanner> by option(
        "--scanners",
        "-s",
        completionCandidates = CompletionCandidates.Fixed(scannerChoices)
    )
        .enum<Scanner> { it.name.lowercase() }
        .varargValues()
        .default(listOf(Scanner.SEMGREP, Scanner.OPENAI))

    val vulnerableFileRegex: String? by option(
        "--vulnerable-files",
        help = "Specify a regex to match vulnerable files and evaluate vuldra results based on that."
    )

    private val jsonPretty = Json { prettyPrint = true }

    override fun run() {
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
        val aggregatedScanResult = AggregatedScanResult(fileScanResults)
        vulnerableFileRegex?.let {
            aggregatedScanResult.evaluation = Evaluation(fileScanResults, it)
        }
        outputScanResults(aggregatedScanResult, targetFiles)
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
                val targetFileContextTask = async { gatherContextOfTargetFile(targetFile, openaiApiClient) }
                val targetFileContext = targetFileContextTask.await()
                FileScanResult(
                    targetFile,
                    openaiApiClient.determineSourceCodeVulnerabilities(
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
        if (verbose) echo(TextColors.cyan(jsonPretty.encodeToString(aggregatedScanResult)))

        val fileScanResults = aggregatedScanResult.fileScanResults
        val vulnerabilityCount = fileScanResults.sumOf {
            it.finalizedVulnerabilities.sumOf { run -> run.results?.size ?: 0 }
        }
        val scanningSummaryMessage =
            "Scanning completed! Found $vulnerabilityCount vulnerabilities in ${targetFiles.size} files."

        var resultsInMarkdown = "# Vulnerabilities"
        resultsInMarkdown += "\n\n".plus(
            if (vulnerabilityCount == 0) TextColors.green(scanningSummaryMessage)
            else TextColors.yellow(scanningSummaryMessage)
        )
        if (aggregatedScanResult.evaluation != null) {
            resultsInMarkdown += generateMarkdownForEvaluation(aggregatedScanResult.evaluation!!)
        }

        fileScanResults.forEach {
            if (it.isVulnerable) {
                resultsInMarkdown += generateMarkdownForVulnerabilities(it)
            }
        }
        if (verbose) echo(resultsInMarkdown)
        echo(Markdown(resultsInMarkdown))
    }

    private fun generateMarkdownForEvaluation(evaluation: Evaluation): String {
        var markdown = "\n\n**Evaluation**"
        markdown += "\nPositives: ${evaluation.positives}"
        markdown += "\nNegatives: ${evaluation.negatives}"
        markdown += "\nTrue Positives: ${evaluation.truePositives}"
        markdown += "\nFalse Positives: ${evaluation.falsePositives}"
        markdown += "\nTrue Negatives: ${evaluation.trueNegatives}"
        markdown += "\nFalse Negatives: ${evaluation.falseNegatives}"
        markdown += "\nAccuracy: ${evaluation.accuracy}"
        markdown += "\nPrecision: ${evaluation.precision}"
        markdown += "\nRecall: ${evaluation.recall}"
        markdown += "\nF1: ${evaluation.f1}"
        return markdown
    }

    private fun generateMarkdownForVulnerabilities(vulnerabilities: FileScanResult): String {
        var markdown = "\n\n## ${vulnerabilities.filepath}"
        vulnerabilities.finalizedVulnerabilities.forEach { run ->
            run.results?.forEach { result ->
                markdown += "\n\n${result.message}"
                result.regions?.forEach {
                    markdown += generateMarkdownForCodeRegion(vulnerabilities, it)
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