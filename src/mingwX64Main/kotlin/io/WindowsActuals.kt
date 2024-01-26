package io

import kotlinx.cinterop.toKString
import platform.posix.getenv

actual suspend fun findExecutable(executable: String): String =
    executable

actual suspend fun pwd(options: ExecuteCommandOptions): String {
    return when (platform) {
        Platform.WINDOWS -> executeExternalCommandAndCaptureOutput(listOf("echo", "%cd%"), options).trim('"', ' ')
        else -> executeExternalCommandAndCaptureOutput(listOf("pwd"), options).trim()
    }
}

actual fun getEnvironmentVariable(name: String): String? =
    getenv(name)?.toKString()

actual fun localUserConfigDirectory(): String =
    getEnvironmentVariable("%LOCALAPPDATA%") ?: error("%LOCALAPPDATA% environment variable not defined")

