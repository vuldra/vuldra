package com.vuldra

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlin.time.Duration.Companion.seconds
import okio.FileHandle

const val CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 = 16385
const val CONTEXT_WINDOW_TOKENS_GPT4_1106_PREVIEW = 128000
const val MAX_OUTPUT_TOKENS = 4096
const val APPROXIMATE_CHARACTERS_PER_TOKEN = 4
class OpenaiApiClient {

//    val openaiClient = OpenAI(
//        token = dotenv["OPENAI_API_KEY"],
//        timeout = Timeout(socket = 60.seconds),
//    )

//    suspend fun determineSourceCodeLanguage(file: File): ChatMessage {
//        var sourceCode = file.
//        val maxInputTokens = CONTEXT_WINDOW_TOKENS_GPT3_5_TURBO_1106 - MAX_OUTPUT_TOKENS
//        if (sourceCode.length / APPROXIMATE_CHARACTERS_PER_TOKEN > maxInputTokens) {
//            sourceCode = sourceCode.substring(0, maxInputTokens * APPROXIMATE_CHARACTERS_PER_TOKEN)
//        }
//
//        val chatCompletionRequest = ChatCompletionRequest(
//            model = ModelId("gpt-3.5-turbo-1106"),
//            messages = listOf(
//                ChatMessage(
//                    role = ChatRole.System,
//                    content = "You can determine the programming language of source code! You always output JSON in a concise manner."
//                ),
//                ChatMessage(
//                    role = ChatRole.User,
//                    content = "Determine the programming language of this source code:\n$sourceCode"
//                )
//            ),
//            responseFormat = ChatResponseFormat.JsonObject
//        )
//        val completion: ChatCompletion = openaiClient.chatCompletion(chatCompletionRequest)
//        return completion.choices.first().message
//    }
}