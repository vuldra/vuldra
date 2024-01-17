package cli

import cli.CliConfig.FIND
import cli.CliConfig.GIT
import cli.CliConfig.SEMGREP
import com.github.ajalt.clikt.core.subcommands
import io.ExecuteCommandOptions
import io.findExecutable
import io.pwd

suspend fun runVuldra(args: Array<String>) {
    var options = ExecuteCommandOptions(directory = ".", abortOnError = true, redirectStderr = true, trim = true)

    //TODO: move section into nodejs actual code ?
    val jsPackage = "/build/js/packages/vuldra"
    val pwd = pwd(options)
    if (pwd.contains(jsPackage)) {
        options = options.copy(directory = pwd.removeSuffix(jsPackage))
    }
    GIT = findExecutable(GIT)
    FIND = findExecutable(FIND)
    SEMGREP = findExecutable(SEMGREP)

    val command = CliCommand()
    val currentDirectory = pwd(options)

    command.main(args)

    // TODO perform scan
}