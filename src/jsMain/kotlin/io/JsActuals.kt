package io

import NodeJS.get
import child_process.ExecOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import okio.FileSystem
import okio.NodeJsFileSystem
import process
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


actual val fileSystem: FileSystem = NodeJsFileSystem

actual suspend fun findExecutable(executable: String): String =
    executable


actual suspend fun executeExternalCommandAndCaptureOutput(
    command: List<String>, // "find . -name .git"
    options: ExecuteCommandOptions
): String {
    val commandToExecute = command.joinToString(separator = " ") { arg ->
        if (arg.contains(" ")) "'$arg'" else arg
    }
    val redirect = if (options.redirectStderr) "2>&1 " else ""
    val execOptions = object : ExecOptions {
        init {
            cwd = options.directory
        }
    }
    return suspendCoroutine<String> { continuation ->
        child_process.exec("$commandToExecute $redirect", execOptions) { error, stdout, stderr ->
            if (error != null) {
                println(stderr)
                continuation.resumeWithException(error)
            } else {
                continuation.resume(if (options.trim) stdout.trim() else stdout)
            }
        }
    }
}


actual suspend fun pwd(options: ExecuteCommandOptions): String {
    return when(platform) {
        Platform.WINDOWS -> executeExternalCommandAndCaptureOutput(listOf("echo", "%cd%"), options).trim()
        else -> executeExternalCommandAndCaptureOutput(listOf("pwd"), options).trim()
    }
}

actual fun getEnvironmentVariable(name: String): String? =
    process.env[name]

actual fun localUserConfigDirectory(): String =
    os.homedir()

actual fun <T> runBlocking(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): dynamic = GlobalScope.promise(context) { block() }

actual val compilationTarget = CompilationTarget.NODEJS

actual val platform: Platform by lazy {
    //  https://nodejs.org/api/os.html
    when(os.platform()) {
        "win32" -> Platform.WINDOWS
        "darwin" -> Platform.MACOS
        else -> Platform.LINUX
    }
}