package cli.scanner

import cli.CliConfig
import commandOptionsAbortOnError
import data.MinimizedRun
import data.MinimizedSarifResult
import io.ExecuteCommandOptions
import io.executeExternalCommandAndCaptureOutput
import io.github.detekt.sarif4k.SarifSchema210
import unstrictJson

enum class Scanner {
    SEMGREP {
        override suspend fun scanTarget(target: String) = semgrepScan(target)
    },
    SNYK {
        override suspend fun scanTarget(target: String) = snykScan(target)
    },
    FLAWFINDER {
        override suspend fun scanTarget(target: String) = flawfinderScan(target)
    },
    OPENAI {
        // TODO refactor
        override suspend fun scanTarget(target: String) = error("Not supported")
    };

    abstract suspend fun scanTarget(target: String): List<MinimizedRun>
}

private suspend fun semgrepScan(target: String) =
    commandProducingSarifFormat(
        listOf(
            CliConfig.SEMGREP,
            "scan",
            "--config",
            "auto",
            "--quiet",
//            "--severity=WARNING", //TODO option to set severity
//            "--severity=ERROR",
            "--sarif",
            target
        )
    )

private suspend fun snykScan(target: String) =
    commandProducingSarifFormat(
        listOf(CliConfig.SNYK, "code", "test", "--sarif", target),
        ExecuteCommandOptions(
            directory = ".",
            abortOnError = true,
            redirectStderr = true,
            trim = true,
            successExitCodes = setOf(0, 1)
        ),
    )

private suspend fun flawfinderScan(target: String) =
    commandProducingSarifFormat(
        listOf(
            CliConfig.FLAWFINDER,
            "--dataonly",
            "--quiet",
            "--sarif",
//            "--minlevel", //TODO option to set severity
//            "3",
            target
        )
    )

private suspend fun commandProducingSarifFormat(
    command: List<String>,
    commandOptions: ExecuteCommandOptions = commandOptionsAbortOnError
): List<MinimizedRun> {
    val semgrepSarifResponse =
        executeExternalCommandAndCaptureOutput(command, commandOptions)
    if (semgrepSarifResponse.isBlank()) {
        error("Command sarif JSON output was empty from command: $command")
    }
    val minimizedSarifResult = MinimizedSarifResult(
        unstrictJson.decodeFromString<SarifSchema210>(
            semgrepSarifResponse
        )
    )
    return minimizedSarifResult.runs
}