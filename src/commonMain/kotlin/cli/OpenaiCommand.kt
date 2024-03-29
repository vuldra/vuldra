package cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.mordant.rendering.TextColors
import config.VuldraConfig
import config.readVuldraConfig
import config.writeVuldraConfig
import echoError
import io.runBlocking
import openai.OpenaiApiClient

const val OPENAI_COMMAND = "openai"
const val LOGIN_COMMAND = "login"

class OpenaiCommand : CliktCommand(
    help = """
       Interact with OpenAI
    """.trimIndent(),
    name = OPENAI_COMMAND
) {
    init {
        subcommands(LoginCommand())
    }

    override fun run() {}
}


class LoginCommand : CliktCommand(
    help = """
       Login to OpenAI
    """.trimIndent(),
    epilog = """
        The command will prompt your for an OpenAI API key that is saved locally in a config file.
        Alternatively you can set the environment variable OPENAI_API_KEY and skip this step.
    """.trimIndent(),
    name = LOGIN_COMMAND
) {
    private val verbose by option("-v", "--verbose", help = "Verbose logging").flag(defaultForHelp = "disabled")
    private val apiKey: String by option(help = "Enter your OpenAI API key").prompt(
        hideInput = true
    )

    override fun run() {
        try {
            runBlocking {
                echo()
                val openaiApiClient = OpenaiApiClient(apiKey, verbose)
                val models = openaiApiClient.listModels().map { model -> model.id.id }
                echo()
                echo("With your API key, you have access to following models:")
                echo(TextColors.cyan(models.joinToString("\n")))
            }
            val vuldraConfig = readVuldraConfig(verbose) ?: VuldraConfig()
            vuldraConfig.openaiApiKey = apiKey
            writeVuldraConfig(vuldraConfig, verbose)
            echo(TextColors.green("Login successful!"))
        } catch (e: Exception) {
            echoError("Login failed: ${e.message}")
        }
    }
}
