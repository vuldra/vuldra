package com.vuldra

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.client.OpenAI
import kotlin.time.Duration.Companion.seconds

class OpenAIClient {
    val openai = OpenAI(
        token = "your-api-key",
        timeout = Timeout(socket = 60.seconds),
        // additional configurations...
    )

}