package cli

import cli.CliConfig.FIND
import cli.CliConfig.FLAWFINDER
import cli.CliConfig.GIT
import cli.CliConfig.SEMGREP
import cli.CliConfig.SNYK
import io.findExecutable

suspend fun runVuldra(args: Array<String>) {
    GIT = findExecutable(GIT)
    FIND = findExecutable(FIND)
    SEMGREP = findExecutable(SEMGREP)
    SNYK = findExecutable(SEMGREP)
    FLAWFINDER = findExecutable(FLAWFINDER)

    VuldraCommand().main(args)
}