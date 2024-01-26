package io

import kotlinx.cinterop.toKString
import platform.posix.getenv

actual suspend fun findExecutable(executable: String): String =
    executable

actual suspend fun pwd(options: ExecuteCommandOptions): String {
    return executeExternalCommandAndCaptureOutput(listOf("pwd"), options).trim()
}

actual fun getEnvironmentVariable(name: String): String? =
    getenv(name)?.toKString()

actual fun localUserConfigDirectory(): String =
    getEnvironmentVariable("HOME") ?: error("HOME environment variable not defined")