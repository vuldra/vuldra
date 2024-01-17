package cli

import cli.CliConfig.FIND
import cli.CliConfig.SEMGREP
import cli.CliConfig.VULDRA_COMMAND
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors
import io.ExecuteCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.github.detekt.sarif4k.SarifSerializer
import io.runBlocking
import sarif.MinimizedSarifResult

const val SCAN_COMMAND = "scan"

class ScanCommand : CliktCommand(
    help = """
       Scan source code for vulnerabilities
    """.trimIndent(),
    epilog = """
        By default all files of the current directory are scanned recursively, unless arguments are provided to specify targets.
        
        Examples:
            $VULDRA_COMMAND $SCAN_COMMAND Vulnerable.java
            $VULDRA_COMMAND $SCAN_COMMAND src/main/java src/main/kotlin
    """.trimIndent(),
    name = SCAN_COMMAND
) {
    val verbose by option("-v", "--verbose", help = "Verbose logging").flag(defaultForHelp = "disabled")
    val depth: Int? by option("--depth", "-d", help = "Specify the depth of recursive directory search").int()
    val pattern: String? by option("--pattern", "-p", help = "Specify a shell pattern to match filenames")
    val targets: List<String> by argument(completionCandidates = CompletionCandidates.Path).multiple()

    private val externalCommandOptions =
        ExecuteCommandOptions(directory = ".", abortOnError = true, redirectStderr = true, trim = true)


    override fun run() {
        val targetFiles: List<String>
        try {
            targetFiles = identifyTargetFiles()
        } catch (e: Exception) {
            echo(TextColors.red("Failed to identify files for scanning: ${e.message}"))
            return
        }
        try {
            runBlocking {
                targetFiles.forEach { scanTargetFile(it) }
            }
        } catch (e: Exception) {
            echo(TextColors.red("Failed to scan files: ${e.message}"))
        }
    }

    private fun identifyTargetFiles(): List<String> {
        var targetFiles = listOf<String>()
        runBlocking {
            targetFiles =
                executeExternalCommandAndCaptureOutput(findCommand(targets), externalCommandOptions).split("\n")
        }
        echo(TextColors.brightBlue("Identified ${targetFiles.size} files to scan."))
        if (verbose) {
            echo(targetFiles.joinToString("\n"))
        }
        return targetFiles
    }

    private suspend fun scanTargetFile(targetFile: String) {
        val semgrepSarifResponse =
            executeExternalCommandAndCaptureOutput(semgrepScanCommand(targetFile), externalCommandOptions)
        if (semgrepSarifResponse.isNotBlank()) {
            val minimizedSarifResult = MinimizedSarifResult(SarifSerializer.fromJson(semgrepSarifResponse))
            if (verbose) echo(minimizedSarifResult)
        }
    }

    private fun semgrepScanCommand(targetFile: String): List<String> =
        listOf(SEMGREP, "scan", "--config", "auto", "--quiet", "--sarif", targetFile)

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

