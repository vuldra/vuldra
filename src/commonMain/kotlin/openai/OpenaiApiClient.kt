package openai

import cli.CliConfig
import cli.LOGIN_COMMAND
import cli.OPENAI_COMMAND
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.Model
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import config.readVuldraConfig
import io.getEnvironmentVariable
import kotlin.time.Duration.Companion.seconds

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
                    content = """
                        You always determine the most likely programming language for provided source code in less than 10 words.
                        
                        Examples:
                        
                        int n = 10;
                        int[] array = new int[n];
                        array[0] = 5;
                        
                        Source code is likely Java.
                        
                        let user = database.find(email, pwd);
                        console.log("Hello " + user.name);
                        
                        Source code is likely JavaScript.
                        
                        def sayHello():
                            print "Hello!"
                        
                        Source code is likely Python.
                    """.trimIndent()
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = """
                        Determine the programming language of this source code:
                        
                        $preparedSourceCode
                    """.trimIndent()
                )
            ),
        )
        val completion: ChatCompletion = openaiClient!!.chatCompletion(chatCompletionRequest)
        // TODO deal with finish_reason before decoding from JSON
        return completion.choices.first().message
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
                timeout = Timeout(30.seconds),
                logging = LoggingConfig(logLevel = if (verbose) LogLevel.All else LogLevel.Info),
            )
        }
    }

    private fun readOpenApiKey(): String? =
        readVuldraConfig(verbose)?.openaiApiKey ?: getEnvironmentVariable(OPENAI_API_KEY_ENV_NAME)
}