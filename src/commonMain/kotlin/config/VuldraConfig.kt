package config

import io.localUserConfigDirectory
import io.writeAllText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

@Serializable
data class VuldraConfig (
    var openaiApiKey: String? = null
)

fun readVuldraConfig(verbose: Boolean = false): VuldraConfig? {
    return if (doesVuldraConfigFileExists()) {
        if (verbose) println("Reading config from file ${vuldraConfigPath()}")
        io.readDataFromJsonFile<VuldraConfig>(vuldraConfigPath())
    }
    else {
        null
    }
}

private fun doesVuldraConfigFileExists(): Boolean =
    io.isFileReadable(vuldraConfigPath())

private fun vuldraConfigPath() =
    "${localUserConfigDirectory()}/.vuldra.json".toPath().toString()

fun writeVuldraConfig(vuldraConfig: VuldraConfig, verbose: Boolean = false) {
    if (verbose) println("Saving config to file ${vuldraConfigPath()}")
    writeAllText(vuldraConfigPath(), Json.encodeToString(vuldraConfig))
}