package cli

import cli.CliConfig.VULDRA_COMMAND
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/**
 * CliKt provides Kotlin Multiplatform command line interface parsing for Kotlin
 * https://ajalt.github.io/clikt/
 */
class VuldraCommand : CliktCommand(
    help = """
       Vuldra integrates with traditional SAST tools and OpenAI's GPT models, to scan source code for vulnerabilities.
    """.trimIndent(),
    epilog = """
        First make sure to login to OpenAI or expose an environment variable OPENAI_API_KEY:
            $VULDRA_COMMAND $OPENAI_COMMAND $LOGIN_COMMAND
            
        Then scan source code in the current directory for vulnerabilities:
            $VULDRA_COMMAND $SCAN_COMMAND
    """.trimIndent(),
    name = VULDRA_COMMAND
) {
    init {
        completionOption(help = "Generate shell completion script for one of the supported shells")
        subcommands(OpenaiCommand(), ScanCommand())
    }
    override fun run() {}
}

