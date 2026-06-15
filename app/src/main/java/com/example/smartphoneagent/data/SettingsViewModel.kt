package com.example.smartphoneagent.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.smartphoneagent.llm.ModelProvider
import com.example.smartphoneagent.plugin.PluginConfig
import com.example.smartphoneagent.plugin.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val pluginManager = PluginManager(application)

    private val _currentProvider = MutableStateFlow(
        try {
            ModelProvider.valueOf(prefs.currentProvider.uppercase())
        } catch (e: IllegalArgumentException) {
            ModelProvider.ZHIPU
        }
    )
    val currentProvider: StateFlow<ModelProvider> = _currentProvider.asStateFlow()

    private val _plugins = MutableStateFlow(pluginManager.getAllPlugins())
    val plugins: StateFlow<List<PluginConfig>> = _plugins.asStateFlow()

    fun updateProvider(provider: ModelProvider) {
        _currentProvider.value = provider
        prefs.currentProvider = provider.id
    }

    fun getApiKey(provider: ModelProvider): String {
        return prefs.getApiKeyForProvider(provider.id) ?: ""
    }

    fun saveApiKey(provider: ModelProvider, key: String) {
        when (provider) {
            ModelProvider.ZHIPU -> prefs.zhipuApiKey = key
            ModelProvider.OPENAI -> prefs.openaiApiKey = key
            ModelProvider.GEMINI -> prefs.geminiApiKey = key
            ModelProvider.DEEPSEEK -> prefs.deepseekApiKey = key
        }
    }

    fun togglePlugin(pluginId: String, enabled: Boolean) {
        pluginManager.setPluginEnabled(pluginId, enabled)
        _plugins.value = pluginManager.getAllPlugins()
    }

    fun importPluginsJson(json: String): Boolean {
        val success = pluginManager.importPluginsFromJson(json)
        if (success) {
            _plugins.value = pluginManager.getAllPlugins()
        }
        return success
    }

    fun deletePlugin(pluginId: String) {
        pluginManager.removeCustomPlugin(pluginId)
        _plugins.value = pluginManager.getAllPlugins()
    }
}
