package io

import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use

expect val platform: Platform
expect val compilationTarget: CompilationTarget
expect val fileSystem: FileSystem

fun readAllText(filePath: String): String =
    fileSystem.read(filePath.toPath()) {
        readUtf8()
    }

fun readLinesInRange(filePath: String, startLine: Int, endLine: Int): String {
    val snippet = ""
    fileSystem.read(filePath.toPath()) {
        this.buffer.use { reader ->
            for (i in 1.. endLine) {
                val line = reader.readUtf8Line() ?: break
                if (i >= startLine) {
                    snippet.plus(line)
                }
            }
        }
    }
    return snippet
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

fun fileIsReadable(filePath: String): Boolean =
    fileSystem.exists(filePath.toPath())

expect suspend fun executeExternalCommandAndCaptureOutput(
    command: List<String>,
    options: ExecuteCommandOptions
): String

data class ExecuteCommandOptions(
    val directory: String,
    val abortOnError: Boolean,
    val redirectStderr: Boolean,
    val trim: Boolean
)

expect suspend fun pwd(options: ExecuteCommandOptions): String

expect fun getEnvironmentVariable(name: String): String?

expect fun localUserConfigDirectory(): String

// call $ which $executable on the JVM
expect suspend fun findExecutable(executable: String): String

// runBlocking doens't exist on JavaScript therefore in common multiplatform code
// https://github.com/jmfayard/kotlin-cli-starter/issues/9
expect fun runBlocking(block: suspend () -> Unit): Unit
