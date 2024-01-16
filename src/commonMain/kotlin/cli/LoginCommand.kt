package cli

import cli.CliConfig.SEMGREP
import cli.CliConfig.VULDRA_COMMAND
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.choice
import config.VuldraConfig
import config.readVuldraConfig
import config.writeVuldraConfig

const val LOGIN_COMMAND = "login"

/**
 * CliKt provides Kotlin Multiplatform command line interface parsing for Kotlin
 * https://ajalt.github.io/clikt/
 */
class LoginCommand : CliktCommand(
    help = """
       Login to one of the supported providers
    """.trimIndent(),
    epilog = """
        For each provider you can either set an appropriate environment variable or login using this prompt.
        If you've already logged in to a provider using its native CLI, you can skip this step for that provider.
        
        Recognized environment variables:
            - OPENAI_API_KEY
        
        Examples:
            $VULDRA_COMMAND $LOGIN_COMMAND openai
    """.trimIndent(),
    name = LOGIN_COMMAND
) {
    init {
        completionOption()
    }

    val provider: Provider by argument(help = "Decide which provider you want to login to: ${Provider.entries}").choice(
        Provider.OPENAI.value to Provider.OPENAI,
        ignoreCase = true
    )
    val secret: String by option(help = "Enter the secret for the selected provider.").prompt(
        hideInput = true
    )

    override fun run() {
        when (provider) {
            Provider.OPENAI -> {
                val vuldraConfig = readVuldraConfig() ?: VuldraConfig()
                vuldraConfig.openaiApiKey = secret
                writeVuldraConfig(vuldraConfig)
//                val openaiApiClient = OpenaiApiClient()
//                openaiApiClient.listModels()
            }
        }
    }

    fun semgrepLoginCommand(targets: List<String>): List<String> {
        val args = mutableListOf<String>()
        args += listOf(SEMGREP, "login")
        args += targets
        return args
    }
}

enum class Provider(val value: String) {
    OPENAI("openai"),
}
