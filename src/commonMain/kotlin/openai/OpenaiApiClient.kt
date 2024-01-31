package openai

import cli.CliConfig
import cli.LOGIN_COMMAND
import cli.OPENAI_COMMAND
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.Model
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.RetryStrategy
import com.github.ajalt.mordant.rendering.TextColors
import config.readVuldraConfig
import data.MinimizedRun
import data.ReasonedVulnerabilities
import io.getEnvironmentVariable
import kotlinx.serialization.encodeToString
import unstrictJson
import kotlin.time.Duration.Companion.minutes

// TODO make limits configurable or based on usage limits of user
const val GPT3_5_TURBO_1106 = "gpt-3.5-turbo-1106"
const val GPT4_0125_PREVIEW = "gpt-4-0125-preview"
const val CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 = 16385
const val CONTEXT_WINDOW_GPT4_TURBO_PREVIEW = 128000
const val REASON_VULNERABILITIES_MAX_OUTPUT_TOKENS = 300
const val APPROXIMATE_CHARACTERS_PER_TOKEN = 4
const val OPENAI_API_KEY_ENV_NAME = "OPENAI_API_KEY"

class OpenaiApiClient(
    private var openaiApiKey: String? = null,
    private val verbose: Boolean = false,
) {
    private var openaiClient: OpenAI? = null

    /**
     *  Exponential backoff is calculated based on: base ^ retryCount * 1000ms + [0..1000ms]
     *  So the first retry will be after ~8s and the second after additional ~64s.
     *  This ensures that the second retry hits a new RPM (Request Per Minute) & TPM (Token Per Minute) window of the OpenAI API.
     *  https://platform.openai.com/docs/guides/rate-limits/how-do-these-rate-limits-work
     */
    private val retryStrategyForRateLimiting = RetryStrategy(
        maxRetries = 2,
        base = 8.0,
        maxDelay = 2.minutes,
    )

    suspend fun reasonVulnerabilities(
        sourceCode: String,
        runs: List<MinimizedRun>,
    ): ReasonedVulnerabilities {
        ensureOpenaiApiClientConfigured()
        var userMessageContent = "\n\nAlleged discoveries of other tools:\n${unstrictJson.encodeToString(runs)}"
        val sourceCodeSnippet = cutExcessSourceCode(
            sourceCode,
            userMessageContent.length + reasonVulnerabilitiesPrompt.length,
            CONTEXT_WINDOW_GPT4_TURBO_PREVIEW - REASON_VULNERABILITIES_MAX_OUTPUT_TOKENS,
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
            maxTokens = REASON_VULNERABILITIES_MAX_OUTPUT_TOKENS,
            seed = 0,
            temperature = 0.0,
        )
        return unstrictJson.decodeFromString(request(chatCompletionRequest))
    }

    private fun cutExcessSourceCode(sourceCode: String, nonSourceCodeInputLength: Int, maxInputTokens: Int): String {
        var sourceCodeSnippet = sourceCode
        if ((sourceCode.length + nonSourceCodeInputLength) / APPROXIMATE_CHARACTERS_PER_TOKEN > maxInputTokens) {
            sourceCodeSnippet = sourceCode.substring(0, maxInputTokens * APPROXIMATE_CHARACTERS_PER_TOKEN)
            println(TextColors.yellow("Input source code exceeds context window of $maxInputTokens tokens and will be truncated."))
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
                logging = LoggingConfig(logLevel = if (verbose) LogLevel.Body else LogLevel.None),
                retry = retryStrategyForRateLimiting,
            )
        }
    }

    private fun readOpenApiKey(): String? =
        readVuldraConfig(verbose)?.openaiApiKey ?: getEnvironmentVariable(OPENAI_API_KEY_ENV_NAME)
}