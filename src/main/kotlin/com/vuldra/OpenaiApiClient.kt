package com.vuldra

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlin.time.Duration.Companion.seconds
import io.github.cdimascio.dotenv.dotenv
import java.io.File

const val CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 = 16385
const val CONTEXT_WINDOW_TOKENS_GPT4_1106_PREVIEW = 128000
const val MAX_OUTPUT_TOKENS = 4096
const val APPROXIMATE_CHARACTERS_PER_TOKEN = 4
class OpenaiApiClient {
    private val dotenv = dotenv()

    val openaiClient = OpenAI(
        token = dotenv["OPENAI_API_KEY"],
        timeout = Timeout(socket = 60.seconds),

    )

    fun determineSourceCodeLanguage(file: File) {
        var sourceCode = file.readText()
        val maxInputTokens = CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 - MAX_OUTPUT_TOKENS
        if (sourceCode.length / APPROXIMATE_CHARACTERS_PER_TOKEN > maxInputTokens) {
            sourceCode = sourceCode.substring(0, maxInputTokens * APPROXIMATE_CHARACTERS_PER_TOKEN)
        }

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo-1106"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are a professional programmer that can determine the language of source code! You always output JSON in a concise manner."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = "Determine the programming language of this source code:\n$sourceCode"
                )
            ),
            responseFormat = ChatResponseFormat.JsonObject
        )
    }
}