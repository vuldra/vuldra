package cli

import cli.CliConfig.FIND
import cli.CliConfig.GIT
import cli.CliConfig.SEMGREP
import io.findExecutable

suspend fun runVuldra(args: Array<String>) {
    GIT = findExecutable(GIT)
    FIND = findExecutable(FIND)
    SEMGREP = findExecutable(SEMGREP)

    VuldraCommand().main(args)
}