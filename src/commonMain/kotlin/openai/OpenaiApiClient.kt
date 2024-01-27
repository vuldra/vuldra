package openai

import cli.CliConfig
import cli.LOGIN_COMMAND
import cli.OPENAI_COMMAND
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.Model
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.github.ajalt.mordant.rendering.TextColors
import config.readVuldraConfig
import data.MinimizedRun
import data.ReasonedVulnerabilities
import data.SourceCodeContext
import io.getEnvironmentVariable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import unstrictJson
import kotlin.time.Duration.Companion.minutes

const val GPT3_5_TURBO_1106 = "gpt-3.5-turbo-1106"
const val GPT4_0125_PREVIEW = "gpt-4-0125-preview"
const val CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 = 16385
const val CONTEXT_WINDOW_TOKENS_GPT4_1106_PREVIEW = 128000
const val MAX_OUTPUT_TOKENS = 4096
const val APPROXIMATE_CHARACTERS_PER_TOKEN = 4
const val OPENAI_API_KEY_ENV_NAME = "OPENAI_API_KEY"

class OpenaiApiClient(
    private var openaiApiKey: String? = null,
    private val verbose: Boolean = false,
) {
    private var openaiClient: OpenAI? = null

    suspend fun gatherSourceCodeContext(sourceCode: String): SourceCodeContext {
        ensureOpenaiApiClientConfigured()
        val sourceCodeSnippet = cutExcessSourceCode(
            sourceCode,
            gatherSourceCodeContextPrompt.length,
            CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106
        )
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(GPT3_5_TURBO_1106),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = gatherSourceCodeContextPrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = "Source code:\n$sourceCodeSnippet"
                )
            ),
            responseFormat = ChatResponseFormat.JsonObject,
        )
        return unstrictJson.decodeFromString(request(chatCompletionRequest))
    }

    private fun cutExcessSourceCode(sourceCode: String, nonSourceCodeInputLength: Int, contextWindow: Int): String {
        var sourceCodeSnippet = sourceCode
        val maxInputTokens = contextWindow - MAX_OUTPUT_TOKENS
        if ((sourceCode.length + nonSourceCodeInputLength) / APPROXIMATE_CHARACTERS_PER_TOKEN > maxInputTokens) {
            sourceCodeSnippet = sourceCode.substring(0, maxInputTokens * APPROXIMATE_CHARACTERS_PER_TOKEN)
            println(TextColors.yellow("Input source code exceeds context window of $contextWindow tokens and will be truncated."))
        }
        return sourceCodeSnippet
    }

    private suspend fun request(chatCompletionRequest: ChatCompletionRequest): String {
        val chatChoice = openaiClient!!.chatCompletion(chatCompletionRequest).choices.first()
        if (chatChoice.finishReason == FinishReason.Length) {
            error("Input tokens plus JSON output exceed context window of model ${chatCompletionRequest.model.id}!")
        }
        return chatChoice.message.content!!
    }

    suspend fun reasonVulnerabilities(
        sourceCode: String,
        sourceCodeContext: SourceCodeContext?,
        runs: List<MinimizedRun>,
    ): ReasonedVulnerabilities {
        ensureOpenaiApiClientConfigured()
        var userMessageContent = ""
        sourceCodeContext?.let {
            userMessageContent += "Source code context:\n${unstrictJson.encodeToString(it)}"
        }
        val runsWithResults = runs.filter { it.results?.isNotEmpty() ?: false }
        if (runsWithResults.isNotEmpty()) {
            userMessageContent += "\n\nAlleged discoveries of other tools:\n${unstrictJson.encodeToString(runsWithResults)}"
        }
        val sourceCodeSnippet = cutExcessSourceCode(
            sourceCode,
            userMessageContent.length + reasonVulnerabilitiesPrompt.length,
            CONTEXT_WINDOW_TOKENS_GPT4_1106_PREVIEW
        )
        userMessageContent += "\n\nSource code:\n$sourceCodeSnippet"

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(GPT4_0125_PREVIEW),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = reasonVulnerabilitiesPrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = userMessageContent
                ),
            ),
            responseFormat = ChatResponseFormat.JsonObject,
        )
        return unstrictJson.decodeFromString(request(chatCompletionRequest))
    }

    suspend fun listModels(): List<Model> {
        ensureOpenaiApiClientConfigured()
        return openaiClient!!.models()
    }

    private fun ensureOpenaiApiClientConfigured() {
        if (openaiClient == null) {
            if (openaiApiKey == null) {
                openaiApiKey = readOpenApiKey()
                    ?: error("OpenAI API key not found! \nPlease first set it using `${CliConfig.VULDRA_COMMAND} $OPENAI_COMMAND $LOGIN_COMMAND` or by exposing the environment variable $OPENAI_API_KEY_ENV_NAME.")
            }
            openaiClient = OpenAI(
                token = openaiApiKey!!,
                timeout = Timeout(5.minutes),
                logging = LoggingConfig(logLevel = if (verbose) LogLevel.All else LogLevel.Info),
            )
        }
    }

    private fun readOpenApiKey(): String? =
        readVuldraConfig(verbose)?.openaiApiKey ?: getEnvironmentVariable(OPENAI_API_KEY_ENV_NAME)
}