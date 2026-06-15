package com.example.smartphoneagent.plugin

data class PluginConfig(
    val id: String,
    val name: String,
    val description: String,
    val actionType: String,
    val enabled: Boolean = true,
    val params: Map<String, String> = emptyMap(),
    val isBuiltIn: Boolean = false
)
