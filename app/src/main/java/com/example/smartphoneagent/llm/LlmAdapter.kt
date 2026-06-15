package com.example.smartphoneagent.llm

import com.example.smartphoneagent.data.ChatMessage

interface LlmAdapter {
    val modelName: String
    suspend fun chat(messages: List<ChatMessage>): String
}

data class LlmConfig(
    val modelId: String,
    val displayName: String,
    val apiKey: String,
    val baseUrl: String
)

enum class ModelProvider(val id: String, val displayName: String, val defaultBaseUrl: String) {
    ZHIPU("zhipu", "Zhipu GLM", "https://open.bigmodel.cn/"),
    OPENAI("openai", "OpenAI", "https://api.openai.com/"),
    GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/"),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/")
}

fun createLlmAdapter(provider: ModelProvider, apiKey: String): LlmAdapter {
    return when (provider) {
        ModelProvider.ZHIPU -> ZhipuAdapter(apiKey)
        ModelProvider.OPENAI -> OpenAiAdapter(apiKey)
        ModelProvider.GEMINI -> GeminiAdapter(apiKey)
        ModelProvider.DEEPSEEK -> DeepSeekAdapter(apiKey)
    }
}
