package io

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import java.io.File

actual val fileSystem: FileSystem = FileSystem.SYSTEM

actual suspend fun findExecutable(executable: String): String = when (platform) {
    Platform.WINDOWS -> executeExternalCommandAndCaptureOutput(listOf("where", executable),
        ExecuteCommandOptions(".", true, false, true))
    else -> executeExternalCommandAndCaptureOutput(listOf("which", executable),
        ExecuteCommandOptions(".", true, false, true))
}

actual suspend fun pwd(options: ExecuteCommandOptions): String {
    return File(".").absolutePath
}

actual fun getEnvironmentVariable(name: String): String? =
    System.getenv(name)

actual fun localUserConfigDirectory(): String =
    getEnvironmentVariable("user.home") ?: error("user.home environment variable not defined")

actual val compilationTarget = CompilationTarget.JVM
actual val platform: Platform by lazy {
    val osName = System.getProperty("os.name").lowercase()

    when {
        osName.startsWith("windows") -> {
            val uname = runBlocking {
                try {
                    executeExternalCommandAndCaptureOutput(
                        listOf("where", "uname"),
                        ExecuteCommandOptions(
                            directory = ".",
                            abortOnError = true,
                            redirectStderr = false,
                            trim = true,
                        ),
                    )
                    executeExternalCommandAndCaptureOutput(
                        listOf("uname", "-a"),
                        ExecuteCommandOptions(
                            directory = ".",
                            abortOnError = true,
                            redirectStderr = true,
                            trim = true,
                        ),
                    )
                } catch (e: Exception) {
                    ""
                }
            }
            if(uname.isNotBlank()) {
                println("uname: $uname")
            }
            when {
                uname.startsWith("MSYS") -> Platform.LINUX
                uname.startsWith("MINGW") -> Platform.LINUX
                uname.startsWith("CYGWIN") -> Platform.LINUX
                else -> Platform.WINDOWS
            }.also {
                println("platform is $it")
            }
        }
        osName.startsWith("linux") -> Platform.LINUX
        osName.startsWith("mac") -> Platform.MACOS
        osName.startsWith("darwin") -> Platform.MACOS
        else -> error("unknown osName: $osName")
    }
}