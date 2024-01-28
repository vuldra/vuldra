package cli

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
object CliConfig {
    const val VULDRA_COMMAND = "vuldra"

    /** The Java API ProcessBuilder, used in executeCommandAndCaptureOutput()
     * needs an absolute path to the executable,
     * which we can find with the function findExecutable()
     *
     * Every external command that you are using should be declared here!
     * **/
    var FIND = "find"
    var GIT = "git"
    var SEMGREP = "semgrep"
    var SNYK = "snyk"
    var FLAWFINDER = "flawfinder"
}