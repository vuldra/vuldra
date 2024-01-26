package io

import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

actual suspend fun executeExternalCommandAndCaptureOutput(
    command: List<String>, // "find . -name .git"
    options: ExecuteCommandOptions
): String {
    // TODO refactor to use separate val for command name
    val child = Command(command.first())
        .args(command.drop(1))
        .cwd(options.directory)
        .stdout(Stdio.Pipe)
        .stderr(Stdio.Pipe)
        .spawn()
    val output = child.waitWithOutput()
    if (output.status != 0 && options.abortOnError) {
        throw Exception("Command `$command` failed with status ${output.status}: ${output.stderr}")
    }
    return when {
        output.status != 0 && options.redirectStderr -> when {
            output.stderr.isNullOrEmpty() -> ""
            options.trim -> output.stderr!!.trim()
            else -> output.stderr!!
        }
        else -> when {
            output.stdout.isNullOrEmpty() -> ""
            options.trim -> output.stdout!!.trim()
            else -> output.stdout!!
        }
    }
}

actual fun <T> runBlocking(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = kotlinx.coroutines.runBlocking(context, block)