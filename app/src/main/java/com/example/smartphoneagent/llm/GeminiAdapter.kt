package com.example.smartphoneagent.llm

import com.example.smartphoneagent.data.ChatMessage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class GeminiAdapter(private val apiKey: String) : LlmAdapter {
    override val modelName = "gemini-1.5-flash"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class GeminiRequest(
        val contents: List<GeminiContent>,
        @SerializedName("generationConfig")
        val generationConfig: GeminiGenerationConfig? = null
    )

    data class GeminiContent(
        val parts: List<GeminiPart>,
        val role: String? = null
    )

    data class GeminiPart(
        val text: String
    )

    data class GeminiGenerationConfig(
        val temperature: Double = 0.7,
        @SerializedName("maxOutputTokens")
        val maxOutputTokens: Int = 1024
    )

    data class GeminiResponse(
        val candidates: List<GeminiCandidate>?
    )

    data class GeminiCandidate(
        val content: GeminiResponseContent?
    )

    data class GeminiResponseContent(
        val parts: List<GeminiPart>?
    )

    override suspend fun chat(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val contents = messages.map { msg ->
                val role = when (msg.role) {
                    "system" -> "user"
                    "assistant" -> "model"
                    else -> "user"
                }
                GeminiContent(
                    parts = listOf(GeminiPart(msg.content)),
                    role = role
                )
            }

            val requestBody = GeminiRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig()
            )
            val jsonBody = gson.toJson(requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "No response"
            if (response.isSuccessful) {
                val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
                geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No response"
            } else {
                "Gemini Error: $responseBody"
            }
        }
    }
}
