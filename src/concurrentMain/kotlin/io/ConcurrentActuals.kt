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
    val status = output.status
    var stdout = output.stdout
    var stderr = output.stderr
    if (!options.successExitCodes.contains(status) && options.abortOnError) {
        throw Exception("Command `$command` failed with status $status: $stderr")
    }
    if (options.trim) {
        stdout = stdout?.trim()
        stderr = stderr?.trim()
    }
    if (options.redirectStderr) {
        stdout += stderr
    }
    return stdout ?: ""
}

actual fun <T> runBlocking(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = kotlinx.coroutines.runBlocking(context, block)