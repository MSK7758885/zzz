package com.example.smartphoneagent.llm

import com.example.smartphoneagent.data.ChatMessage
import com.example.smartphoneagent.data.ZhipuRequest
import com.example.smartphoneagent.data.ZhipuResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ZhipuAdapter(private val apiKey: String) : LlmAdapter {
    override val modelName = "glm-4-flash"

    private val apiService: com.example.smartphoneagent.data.ZhipuApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://open.bigmodel.cn/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(com.example.smartphoneagent.data.ZhipuApiService::class.java)
    }

    override suspend fun chat(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val request = ZhipuRequest(
                model = modelName,
                messages = messages,
                stream = false
            )
            val response: ZhipuResponse = apiService.chatCompletion(
                "Bearer $apiKey", request
            )
            response.choices?.firstOrNull()?.message?.content ?: "No response"
        }
    }
}
