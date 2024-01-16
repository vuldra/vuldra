package cli

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
object CliConfig {
    val COMMAND_NAME = "vuldra"

    /** The Java API ProcessBuilder, used in executeCommandAndCaptureOutput()
     * needs an absolute path to the executable,
     * which we can find with the function findExecutable()
     * **/
    // CUSTOMIZE_ME: every subcommand that you are using should be declared here
    var FIND = "find"
    var GIT = "git"
    var SEMGREP = "semgrep"
}