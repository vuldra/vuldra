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
import config.readVuldraConfig
import io.getEnvironmentVariable
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

    suspend fun determineSourceCodeLanguage(targetFile: String, sourceCode: String): String? {
        ensureOpenaiApiClientConfigured()
        var sourceCodeSnippet = sourceCode
        val maxInputTokens = 1000 // should be enough tokens to determine language
        if (maxInputTokens > CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 - MAX_OUTPUT_TOKENS) {
            error("MaxInputTokens $maxInputTokens to large for context window of model $GPT3_5_TURBO_1106")
        }
        if (sourceCode.length / APPROXIMATE_CHARACTERS_PER_TOKEN > maxInputTokens) {
            sourceCodeSnippet = sourceCode.substring(0, maxInputTokens * APPROXIMATE_CHARACTERS_PER_TOKEN)
        }

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(GPT3_5_TURBO_1106),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = determineSourceCodeLanguagePrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = "$targetFile\n\n$sourceCodeSnippet"
                )
            ),
        )
        val completion: ChatCompletion = openaiClient!!.chatCompletion(chatCompletionRequest)
        // TODO deal with finish_reason before decoding from JSON
        return completion.choices.first().message.content
    }

    suspend fun determineCommonVulnerabilities(programmingLanguage: String): ChatMessage {
        ensureOpenaiApiClientConfigured()
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(GPT3_5_TURBO_1106),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = determineCommonVulnerabilitiesPrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = programmingLanguage
                )
            ),
        )
        val completion: ChatCompletion = openaiClient!!.chatCompletion(chatCompletionRequest)
        return completion.choices.first().message
    }

    suspend fun determineSourceCodeVulnerabilities(
        targetFile: String,
        sourceCode: String,
        programmingLanguage: String,
        commonVulnerabilitiesMessage: ChatMessage,
        sastResult: String
    ): SourceCodeVulnerabilities {
        ensureOpenaiApiClientConfigured()
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(GPT4_1106_PREVIEW),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.Assistant,
                    content = programmingLanguage
                ),
                commonVulnerabilitiesMessage,
                ChatMessage(
                    role = ChatRole.User,
                    content = sastResult
                ),
                ChatMessage(
                    role = ChatRole.System,
                    content = determineSourceCodeVulnerabilitiesPrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = "$targetFile\n\n$sourceCode"
                )
            ),
            responseFormat = ChatResponseFormat.JsonObject,
        )
        val chatChoice = openaiClient!!.chatCompletion(chatCompletionRequest).choices.first()
        if (chatChoice.finishReason == FinishReason.Length) {
            error("Input tokens plus JSON output exceed context window of model $GPT4_1106_PREVIEW")
        }
        try {
            return unstrictJson.decodeFromString(chatChoice.message.content!!)
        } catch (e: Exception) {
            error("Failed to parse JSON output from OpenAI API: ${e.message}")
        }
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
                timeout = Timeout(2.minutes),
                logging = LoggingConfig(logLevel = if (verbose) LogLevel.All else LogLevel.Info),
            )
        }
    }

    private fun readOpenApiKey(): String? =
        readVuldraConfig(verbose)?.openaiApiKey ?: getEnvironmentVariable(OPENAI_API_KEY_ENV_NAME)
}