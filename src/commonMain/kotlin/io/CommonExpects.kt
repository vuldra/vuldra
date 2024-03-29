package io

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

expect val platform: Platform
expect val compilationTarget: CompilationTarget
expect val fileSystem: FileSystem

fun readAllText(filePath: String): String =
    fileSystem.read(filePath.toPath()) {
        readUtf8()
    }

fun readAllLines(filePath: String, maxCharactersPerLine: Int): List<String> {
    val lines = mutableListOf<String>()
    fileSystem.read(filePath.toPath()) {
        while (true) {
            val line = readUtf8Line() ?: break
            if (line.length > maxCharactersPerLine) {
                throw IOException("Line character count ${line.length} exceeds limit of $maxCharactersPerLine")
            }
            lines.add(line)
        }
    }
    return lines
}

inline fun <reified T> readDataFromJsonFile(filePath: String) =
    Json.decodeFromString<T>(readAllText(filePath))

fun writeAllText(filePath: String, text: String): Unit =
    fileSystem.write(filePath.toPath()) {
        writeUtf8(text)
    }

fun writeAllLines(
    filePath: String,
    lines: List<String>
) = writeAllText(filePath, lines.joinToString(separator = "\n"))

fun isFileReadable(filePath: String): Boolean =
    fileSystem.exists(filePath.toPath())

expect suspend fun executeExternalCommandAndCaptureOutput(
    command: List<String>,
    options: ExecuteCommandOptions
): String

data class ExecuteCommandOptions(
    val directory: String,
    val abortOnError: Boolean,
    val redirectStderr: Boolean,
    val trim: Boolean,
    val successExitCodes: Set<Int> = setOf(0),
)

expect suspend fun pwd(options: ExecuteCommandOptions): String

expect fun getEnvironmentVariable(name: String): String?

expect fun localUserConfigDirectory(): String

// call $ which $executable on the JVM
expect suspend fun findExecutable(executable: String): String

// runBlocking doens't exist on JavaScript therefore in common multiplatform code
// https://github.com/jmfayard/kotlin-cli-starter/issues/9
expect fun <T> runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T
