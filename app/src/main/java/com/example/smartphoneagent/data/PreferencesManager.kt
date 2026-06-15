package com.example.smartphoneagent.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_phone_agent_prefs", Context.MODE_PRIVATE)

    var zhipuApiKey: String?
        get() = prefs.getString("zhipu_api_key", null)
        set(value) = prefs.edit().putString("zhipu_api_key", value).apply()

    var openaiApiKey: String?
        get() = prefs.getString("openai_api_key", null)
        set(value) = prefs.edit().putString("openai_api_key", value).apply()

    var geminiApiKey: String?
        get() = prefs.getString("gemini_api_key", null)
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    var deepseekApiKey: String?
        get() = prefs.getString("deepseek_api_key", null)
        set(value) = prefs.edit().putString("deepseek_api_key", value).apply()

    var currentProvider: String
        get() = prefs.getString("current_provider", "zhipu") ?: "zhipu"
        set(value) = prefs.edit().putString("current_provider", value).apply()

    fun getApiKeyForProvider(provider: String): String? {
        return when (provider) {
            "zhipu" -> zhipuApiKey
            "openai" -> openaiApiKey
            "gemini" -> geminiApiKey
            "deepseek" -> deepseekApiKey
            else -> null
        }
    }

    var hasCompletedSetup: Boolean
        get() = prefs.getBoolean("has_completed_setup", false)
        set(value) = prefs.edit().putBoolean("has_completed_setup", value).apply()
}
