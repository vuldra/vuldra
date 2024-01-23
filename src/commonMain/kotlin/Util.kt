import io.ExecuteCommandOptions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val unstrictJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

val externalCommandOptions =
    ExecuteCommandOptions(directory = ".", abortOnError = true, redirectStderr = true, trim = true)