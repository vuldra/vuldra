package openai

import cli.CliConfig
import cli.LOGIN_COMMAND
import cli.Provider
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import config.readVuldraConfig
import io.getEnvironmentVariable
import kotlin.time.Duration.Companion.seconds

const val CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 = 16385
const val CONTEXT_WINDOW_TOKENS_GPT4_1106_PREVIEW = 128000
const val MAX_OUTPUT_TOKENS = 4096
const val APPROXIMATE_CHARACTERS_PER_TOKEN = 4
const val OPENAI_API_KEY_ENV_NAME = "OPENAI_API_KEY"

class OpenaiApiClient {
    var openaiApiKey = readOpenApiKey()
    var openaiClient: OpenAI? = null

    suspend fun determineSourceCodeLanguage(sourceCode: String): ChatMessage {
        ensureOpenaiApiClientConfigured()

        var preparedSourceCode = sourceCode
        val maxInputTokens = CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 - MAX_OUTPUT_TOKENS
        if (sourceCode.length / APPROXIMATE_CHARACTERS_PER_TOKEN > maxInputTokens) {
            preparedSourceCode = sourceCode.substring(0, maxInputTokens * APPROXIMATE_CHARACTERS_PER_TOKEN)
        }

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo-1106"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You can determine the programming language of source code! You always output JSON in a concise manner."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = "Determine the programming language of this source code:\n$preparedSourceCode"
                )
            ),
            responseFormat = ChatResponseFormat.JsonObject
        )
        val completion: ChatCompletion = openaiClient!!.chatCompletion(chatCompletionRequest)
        // TODO deal with finish_reason before decoding from JSON
        return completion.choices.first().message
    }

    suspend fun testApiConnection() {
        ensureOpenaiApiClientConfigured()
        openaiClient!!.models()
    }

    private fun ensureOpenaiApiClientConfigured() {
        if (openaiClient == null) {
            if (openaiApiKey == null) {
                openaiApiKey = readOpenApiKey()
                    ?: error("OpenAI API key is not set. Please first set it using `${CliConfig.VULDRA_COMMAND} $LOGIN_COMMAND ${Provider.OPENAI}` or by exposing the environment variable $OPENAI_API_KEY_ENV_NAME.")
            }
            openaiClient = OpenAI(
                token = openaiApiKey!!,
                timeout = Timeout(30.seconds)
            )
        }
    }

    private fun readOpenApiKey(): String? =
        readVuldraConfig()?.openaiApiKey ?: getEnvironmentVariable(OPENAI_API_KEY_ENV_NAME)
}