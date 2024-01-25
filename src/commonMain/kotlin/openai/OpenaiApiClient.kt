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
import config.readVuldraConfig
import data.MinimizedRun
import data.ReasonedVulnerabilities
import io.getEnvironmentVariable
import kotlinx.serialization.encodeToString
import unstrictJson
import kotlin.time.Duration.Companion.minutes

const val GPT3_5_TURBO_1106 = "gpt-3.5-turbo-1106"
const val GPT4_1106_PREVIEW = "gpt-4-1106-preview"
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

    suspend fun findVulnerabilities(
        sourceCode: String,
    ): MinimizedRun {
        ensureOpenaiApiClientConfigured()
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(GPT4_1106_PREVIEW),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = findVulnerabilitiesPrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = sourceCode
                )
            ),
            responseFormat = ChatResponseFormat.JsonObject,
        )
        return unstrictJson.decodeFromString(request(chatCompletionRequest))
    }

    private suspend fun request(chatCompletionRequest: ChatCompletionRequest): String {
        val chatChoice = openaiClient!!.chatCompletion(chatCompletionRequest).choices.first()
        if (chatChoice.finishReason == FinishReason.Length) {
            error("Input tokens plus JSON output exceed context window of model $GPT4_1106_PREVIEW")
        }
        return chatChoice.message.content!!
    }

    suspend fun reasonVulnerabilities(
        runs: List<MinimizedRun>
    ): ReasonedVulnerabilities {
        ensureOpenaiApiClientConfigured()
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(GPT4_1106_PREVIEW),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = reasonVulnerabilitiesPrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = unstrictJson.encodeToString(runs)
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