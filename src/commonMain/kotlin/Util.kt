import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.mordant.rendering.TextColors
import io.ExecuteCommandOptions
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

fun CliktCommand.echoWarn(message: String) = echo(message = TextColors.yellow(message))
fun CliktCommand.echoError(message: String) = echo(message = TextColors.red(message), err = true)

fun currentTime() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.toString()