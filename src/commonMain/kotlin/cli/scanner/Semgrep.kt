package cli.scanner

import cli.CliConfig
import data.MinimizedRun
import data.MinimizedSarifResult
import externalCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.github.detekt.sarif4k.SarifSchema210
import unstrictJson

suspend fun scanFileWithSemgrep(targetFile: String): List<MinimizedRun> {
    val semgrepSarifResponse =
        executeExternalCommandAndCaptureOutput(semgrepScanCommand(targetFile), externalCommandOptions)
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
    listOf(CliConfig.SEMGREP, "scan", "--config", "auto", "--quiet", "--sarif", targetFile)