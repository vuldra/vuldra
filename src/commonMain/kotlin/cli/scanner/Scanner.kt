package cli.scanner

import cli.CliConfig
import data.MinimizedRun
import data.MinimizedSarifResult
import externalCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.github.detekt.sarif4k.SarifSchema210
import unstrictJson

enum class Scanner {
    SEMGREP {
        override suspend fun scanFile(targetFile: String) =
            scanCommandProducingSarifFormat(semgrepScanCommand(targetFile))
    },
    SNYK {
        override suspend fun scanFile(targetFile: String) =
            scanCommandProducingSarifFormat(snykScanCommand(targetFile))
    },
    FLAWFINDER {
        override suspend fun scanFile(targetFile: String) =
            scanCommandProducingSarifFormat(flawfinderScanCommand(targetFile))
    },
    OPENAI {
        // TODO refactor
        override suspend fun scanFile(targetFile: String) = error("Not supported")
    };

    abstract suspend fun scanFile(targetFile: String): List<MinimizedRun>
}

suspend fun scanCommandProducingSarifFormat(command: List<String>): List<MinimizedRun> {
    val semgrepSarifResponse =
        executeExternalCommandAndCaptureOutput(command, externalCommandOptions)
    if (semgrepSarifResponse.isBlank()) {
        error("Command output is blank")
    }
    val minimizedSarifResult = MinimizedSarifResult(
        unstrictJson.decodeFromString<SarifSchema210>(
            semgrepSarifResponse
        )
    )
    return minimizedSarifResult.runs
}

private fun semgrepScanCommand(targetFile: String): List<String> =
    listOf(CliConfig.SEMGREP, "scan", "--config", "auto", "--quiet", "--severity=WARNING", "--severity=ERROR", "--sarif", targetFile)

private fun snykScanCommand(targetFile: String): List<String> =
    listOf(CliConfig.SNYK, "code", "test", "--sarif", targetFile)

private fun flawfinderScanCommand(targetFile: String): List<String> =
    listOf(CliConfig.FLAWFINDER, "--dataonly", "--quiet", "--sarif",  "--minlevel", "3", targetFile)