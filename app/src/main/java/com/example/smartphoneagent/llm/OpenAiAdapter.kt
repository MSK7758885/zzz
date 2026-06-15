package com.example.smartphoneagent.llm

import com.example.smartphoneagent.data.ChatMessage
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

class OpenAiAdapter(private val apiKey: String) : LlmAdapter {
    override val modelName = "gpt-4o-mini"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class OpenAiRequest(
        val model: String,
        val messages: List<Map<String, String>>
    )

    data class OpenAiResponse(
        val choices: List<OpenAiChoice>?
    )

    data class OpenAiChoice(
        val message: OpenAiMessage?
    )

    data class OpenAiMessage(
        val content: String?
    )

    override suspend fun chat(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val msgList = messages.map { mapOf("role" to it.role, "content" to it.content) }
            val requestBody = OpenAiRequest(model = modelName, messages = msgList)
            val jsonBody = gson.toJson(requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "No response"
            if (response.isSuccessful) {
                val openAiResponse = gson.fromJson(responseBody, OpenAiResponse::class.java)
                openAiResponse.choices?.firstOrNull()?.message?.content ?: "No response"
            } else {
                "OpenAI Error: $responseBody"
            }
        }
    }
}
