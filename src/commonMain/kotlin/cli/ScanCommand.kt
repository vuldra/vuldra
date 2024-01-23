package cli

import cli.CliConfig.FIND
import cli.CliConfig.VULDRA_COMMAND
import cli.scanner.Scanner
import com.aallam.openai.api.chat.ChatMessage
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors
import externalCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.readAllText
import io.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import openai.OpenaiApiClient
import openai.SourceCodeVulnerabilities
import sarif.MinimizedRun

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
        .plus("\n$VULDRA_COMMAND $SCAN_COMMAND src/main/java --pattern *.java")
        .plus("\n$VULDRA_COMMAND $SCAN_COMMAND --scanners semgrep openai"),
    name = SCAN_COMMAND
) {
    val targets: List<String> by argument(completionCandidates = CompletionCandidates.Path).multiple()

    val verbose: Boolean by option("-v", "--verbose", help = "Verbose logging").flag(defaultForHelp = "disabled")
    val depth: Int? by option("--depth", "-d", help = "Specify the depth of recursive directory search").int()
    val pattern: String? by option("--pattern", "-p", help = "Specify a shell pattern to match filenames")
    val scanners: List<Scanner> by option(
        "--scanners",
        "-s",
        completionCandidates = CompletionCandidates.Fixed(scannerChoices)
    )
        .enum<Scanner> { it.name.lowercase() }
        .varargValues()
        .default(listOf(Scanner.SEMGREP, Scanner.OPENAI))

    private val jsonPretty = Json { prettyPrint = true }

    override fun run() {
        val targetFiles = try {
            identifyTargetFiles()
        } catch (e: Exception) {
            echo(TextColors.red("Failed to identify files for scanning: ${e.message}"))
            return
        }
        val openaiApiClient = OpenaiApiClient(verbose = verbose)
        val sourceCodeVulnerabilities = mutableListOf<SourceCodeVulnerabilities>()
        runBlocking {
            try {
                openaiApiClient.listModels()
            } catch (e: Exception) {
                echo(TextColors.red("Failed to query OpenAI API: ${e.message}"))
                return@runBlocking
            }
            targetFiles.forEach {
                try {
                    sourceCodeVulnerabilities.add(scanTargetFile(it, openaiApiClient))
                } catch (e: Exception) {
                    echo(TextColors.red("Failed to scan file $it: ${e.message}"))
                }
            }
        }
        val vulnerabilityCount = sourceCodeVulnerabilities.sumOf {
            it.finalizedVulnerabilities.sumOf { run -> run.results?.size ?: 0 }
        }
        echo(TextColors.cyan(jsonPretty.encodeToString(sourceCodeVulnerabilities)))
        val scanningSummaryMessage =
            "Scanning completed! Found $vulnerabilityCount vulnerabilities in ${targetFiles.size} files."
        if (vulnerabilityCount == 0) echo(TextColors.green(scanningSummaryMessage))
        else echo(TextColors.yellow(scanningSummaryMessage))
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
    ): SourceCodeVulnerabilities {
        return coroutineScope {
            val sastRuns = mutableListOf<MinimizedRun>()

            scanners.filter { it != Scanner.OPENAI }.map { scanner -> async { scanner.scanFile(targetFile) } }
                .forEach {
                    try {
                        val sastResult = it.await()
                        sastRuns.addAll(sastResult)
                    } catch (e: Exception) {
                        echo(TextColors.red("Failed to scan file $targetFile: ${e.message}"))
                    }
                }

            if (scanners.contains(Scanner.OPENAI)) {
                val targetFileContextTask = async { gatherContextOfTargetFile(targetFile, openaiApiClient) }
                val targetFileContext = targetFileContextTask.await()

                val sourceCodeVulnerabilities = SourceCodeVulnerabilities(
                    openaiApiClient.determineSourceCodeVulnerabilities(
                        targetFile,
                        targetFileContext.sourceCode,
                        targetFileContext.programmingLanguage,
                        targetFileContext.commonVulnerabilitiesMessage,
                        sastRuns
                    )
                )
                sourceCodeVulnerabilities.sastVulnerabilities = sastRuns
                sourceCodeVulnerabilities
            } else {
                SourceCodeVulnerabilities(
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
        if (pattern != null) {
            args += "-name"
            args += pattern!!
        }
        return args.also { if (verbose) println("$ $it") }
    }
}

data class TargetFilContext(
    val sourceCode: String,
    val programmingLanguage: String,
    val commonVulnerabilitiesMessage: ChatMessage
)