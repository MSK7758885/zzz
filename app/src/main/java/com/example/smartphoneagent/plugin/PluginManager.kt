package com.example.smartphoneagent.plugin

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PluginManager(private val context: Context) {

    companion object {
        private const val TAG = "PluginManager"
        private const val PREFS_CUSTOM_PLUGINS = "plugin_configs"
        private const val PREFS_BUILTIN_STATES = "plugin_builtin_states"
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("plugin_prefs", Context.MODE_PRIVATE)

    fun getBuiltInPlugins(): List<PluginConfig> {
        val builtinStates = getBuiltInPluginStates()
        return listOf(
            PluginConfig("open_app", "打开应用", "通过包名打开指定应用", "open_app",
                enabled = builtinStates["open_app"] ?: true, isBuiltIn = true),
            PluginConfig("set_volume", "调节音量", "调整设备音量大小", "set_volume",
                enabled = builtinStates["set_volume"] ?: true, isBuiltIn = true),
            PluginConfig("screenshot", "截屏", "截取当前屏幕画面", "screenshot",
                enabled = builtinStates["screenshot"] ?: true, isBuiltIn = true),
            PluginConfig("get_sensor", "传感器数据", "读取加速度传感器数据", "get_sensor",
                enabled = builtinStates["get_sensor"] ?: true, isBuiltIn = true),
            PluginConfig("get_ui_tree", "获取界面树", "获取当前屏幕UI元素结构", "get_ui_tree",
                enabled = builtinStates["get_ui_tree"] ?: true, isBuiltIn = true),
            PluginConfig("click_on_text", "点击文字", "点击屏幕上的指定文字", "click_on_text",
                enabled = builtinStates["click_on_text"] ?: true, isBuiltIn = true),
            PluginConfig("click_position", "点击坐标", "点击屏幕指定坐标位置", "click_position",
                enabled = builtinStates["click_position"] ?: true, isBuiltIn = true),
            PluginConfig("swipe", "滑动操作", "在屏幕上执行滑动操作", "swipe",
                enabled = builtinStates["swipe"] ?: true, isBuiltIn = true),
            PluginConfig("input_text", "输入文本", "向输入框输入文字", "input_text",
                enabled = builtinStates["input_text"] ?: true, isBuiltIn = true),
        )
    }

    fun getCustomPlugins(): List<PluginConfig> {
        val json = prefs.getString(PREFS_CUSTOM_PLUGINS, "[]") ?: "[]"
        val type = object : TypeToken<List<PluginConfig>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse custom plugins", e)
            emptyList()
        }
    }

    fun getAllPlugins(): List<PluginConfig> {
        return getBuiltInPlugins() + getCustomPlugins()
    }

    fun addCustomPlugin(plugin: PluginConfig) {
        val current = getCustomPlugins().toMutableList()
        current.removeAll { it.id == plugin.id }
        current.add(plugin)
        saveCustomPlugins(current)
    }

    fun removeCustomPlugin(pluginId: String) {
        val current = getCustomPlugins().toMutableList()
        current.removeAll { it.id == pluginId }
        saveCustomPlugins(current)
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        val builtInIds = getBuiltInPlugins().map { it.id }.toSet()
        if (pluginId in builtInIds) {
            val states = getBuiltInPluginStates().toMutableMap()
            states[pluginId] = enabled
            saveBuiltInPluginStates(states)
        } else {
            val customPlugins = getCustomPlugins().map {
                if (it.id == pluginId) it.copy(enabled = enabled) else it
            }
            saveCustomPlugins(customPlugins)
        }
    }

    fun importPluginsFromJson(jsonString: String): Boolean {
        return try {
            val type = object : TypeToken<List<PluginConfig>>() {}.type
            val plugins: List<PluginConfig> = gson.fromJson(jsonString, type)
            val current = getCustomPlugins().toMutableList()
            for (plugin in plugins) {
                current.removeAll { it.id == plugin.id }
                current.add(plugin)
            }
            saveCustomPlugins(current)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import plugins from JSON", e)
            false
        }
    }

    private fun getBuiltInPluginStates(): Map<String, Boolean> {
        val json = prefs.getString(PREFS_BUILTIN_STATES, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Boolean>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveBuiltInPluginStates(states: Map<String, Boolean>) {
        val json = gson.toJson(states)
        prefs.edit().putString(PREFS_BUILTIN_STATES, json).apply()
    }

    private fun saveCustomPlugins(plugins: List<PluginConfig>) {
        val json = gson.toJson(plugins)
        prefs.edit().putString(PREFS_CUSTOM_PLUGINS, json).apply()
    }
}
