package config

import io.localUserConfigDirectory
import kotlinx.serialization.Serializable
import okio.Path
import okio.Path.Companion.toPath

@Serializable
data class VuldraConfig (
    var openaiApiKey: String? = null
)

fun readVuldraConfig(): VuldraConfig? =
    if (doesVuldraConfigFileExists())
        io.readDataFromJsonFile(vuldraConfigPath().name)
    else
        null

fun doesVuldraConfigFileExists(): Boolean =
    io.fileIsReadable(vuldraConfigPath().name)

fun vuldraConfigPath(): Path =
    "${localUserConfigDirectory()}/vuldra.json".toPath()

fun writeVuldraConfig(vuldraConfig: VuldraConfig) = io.writeDataToJsonFile(
    vuldraConfigPath().name,
    vuldraConfig
)