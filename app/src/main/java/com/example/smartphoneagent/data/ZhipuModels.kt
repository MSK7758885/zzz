package com.example.smartphoneagent.data

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val role: String,
    val content: String
)

data class ZhipuRequest(
    val model: String = "glm-4-flash",
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ZhipuResponse(
    val id: String?,
    val objectField: String?,
    val created: Long?,
    val model: String?,
    val choices: List<Choice>?,
    @SerializedName("usage")
    val usage: UsageInfo?
)

data class Choice(
    val index: Int?,
    val message: ChatMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class UsageInfo(
    @SerializedName("prompt_tokens")
    val promptTokens: Int?,
    @SerializedName("completion_tokens")
    val completionTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)
