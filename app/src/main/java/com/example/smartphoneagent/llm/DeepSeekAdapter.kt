package com.example.smartphoneagent.llm

import com.example.smartphoneagent.data.ChatMessage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class DeepSeekAdapter(private val apiKey: String) : LlmAdapter {
    override val modelName = "deepseek-chat"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class DeepSeekRequest(
        val model: String,
        val messages: List<Map<String, String>>
    )

    data class DeepSeekResponse(
        val choices: List<DeepSeekChoice>?
    )

    data class DeepSeekChoice(
        val message: DeepSeekMessage?
    )

    data class DeepSeekMessage(
        val content: String?
    )

    override suspend fun chat(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val msgList = messages.map { mapOf("role" to it.role, "content" to it.content) }
            val requestBody = mapOf(
                "model" to modelName,
                "messages" to msgList
            )
            val jsonBody = gson.toJson(requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.deepseek.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "No response"
            if (response.isSuccessful) {
                val dsResponse = gson.fromJson(responseBody, DeepSeekResponse::class.java)
                dsResponse.choices?.firstOrNull()?.message?.content ?: "No response"
            } else {
                "DeepSeek Error: $responseBody"
            }
        }
    }
}
