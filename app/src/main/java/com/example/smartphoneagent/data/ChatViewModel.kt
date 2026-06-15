package com.example.smartphoneagent.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartphoneagent.llm.createLlmAdapter
import com.example.smartphoneagent.llm.LlmAdapter
import com.example.smartphoneagent.llm.ModelProvider
import com.example.smartphoneagent.plugin.ActionExecutor
import com.example.smartphoneagent.plugin.PluginManager
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MessageItem(
    val role: String,
    val content: String,
    val isUser: Boolean
)

data class ActionBlock(
    val action: String,
    val params: Map<String, String> = emptyMap()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val prefs = PreferencesManager(application)
    private val actionExecutor = ActionExecutor(application)
    private val pluginManager = PluginManager(application)

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private fun buildSystemPrompt(): ChatMessage {
        val enabledPlugins = pluginManager.getAllPlugins().filter { it.enabled }
        val actionList = mutableListOf<String>()
        for (plugin in enabledPlugins) {
            val params = when (plugin.actionType) {
                "open_app" -> "（需要 packageName 参数，如 com.android.settings）"
                "set_volume" -> "（需要 level 参数，0-100）"
                "click_on_text" -> "（需要 text 参数）"
                "click_position" -> "（需要 x, y 参数）"
                "swipe" -> "（需要 startX, startY, endX, endY 参数）"
                "input_text" -> "（需要 text 参数）"
                else -> ""
            }
            actionList.add("- ${plugin.actionType}: ${plugin.description}$params")
        }
        return ChatMessage(
            role = "system",
            content = """你是一个 AI 手机助手，可以用自然语言控制 Android 手机的各种操作。
请保持回复简洁有帮助。

可用操作：
${actionList.joinToString("\n")}

重要规则：
当用户要求执行手机操作时，在回复中嵌入 JSON 操作块，格式必须严格如下：
{"action": "操作名", "params": {"参数名": "参数值"}}

一次可以包含多个操作，每个写在单独一行。
例如用户说"打开设置"，你必须回复：
好的，帮你打开设置
{"action": "open_app", "params": {"packageName": "com.android.settings"}}"""
        )
    }

    init {
        val savedKey = prefs.zhipuApiKey
        if (savedKey.isNullOrBlank()) {
            _showApiKeyDialog.value = true
        }
    }

    fun updateInput(text: String) {
        _currentInput.value = text
    }

    fun checkApiKey() {
        _showSettings.value = true
    }

    fun dismissSettings() {
        _showSettings.value = false
    }

    fun saveApiKey(key: String) {
        prefs.zhipuApiKey = key
        prefs.currentProvider = "zhipu"
        _showApiKeyDialog.value = false
        prefs.hasCompletedSetup = true
    }

    fun dismissApiKeyDialog() {
        _showApiKeyDialog.value = false
    }

    private fun getCurrentAdapter(): LlmAdapter? {
        val provider = ModelProvider.entries.find { it.id == prefs.currentProvider }
            ?: ModelProvider.ZHIPU
        val apiKey = prefs.getApiKeyForProvider(provider.id)
        return if (!apiKey.isNullOrBlank()) {
            createLlmAdapter(provider, apiKey)
        } else null
    }

    fun sendMessage() {
        val text = _currentInput.value.trim()
        if (text.isEmpty() || _isLoading.value) return

        val adapter = getCurrentAdapter()
        if (adapter == null) {
            _showApiKeyDialog.value = true
            return
        }

        _currentInput.value = ""
        val userMessage = MessageItem(role = "user", content = text, isUser = true)
        _messages.value = _messages.value + userMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val llmMessages = buildMessages(includeSystemPrompt = true)
                val reply = withContext(Dispatchers.IO) {
                    adapter.chat(llmMessages)
                }

                val actions = extractActions(reply)
                if (actions.isNotEmpty()) {
                    _messages.value = _messages.value + MessageItem(
                        role = "assistant", content = stripJsonFromReply(reply), isUser = false
                    )

                    val actionResults = StringBuilder()
                    for (action in actions) {
                        val result = withContext(Dispatchers.IO) {
                            actionExecutor.execute(action.action, action.params)
                        }
                        actionResults.appendLine("[${action.action}] $result")
                    }
                    val resultText = actionResults.toString().trim()

                    _messages.value = _messages.value + MessageItem(
                        role = "system", content = "执行结果:\n$resultText", isUser = false
                    )

                    val followupMessages = llmMessages +
                        ChatMessage(role = "assistant", content = reply) +
                        ChatMessage(role = "user",
                            content = "系统已执行上述操作，结果如下：\n$resultText\n\n请根据执行结果简洁地回复用户。"
                        )

                    val followupReply = withContext(Dispatchers.IO) {
                        adapter.chat(followupMessages)
                    }

                    _messages.value = _messages.value + MessageItem(
                        role = "assistant", content = followupReply, isUser = false
                    )
                } else {
                    _messages.value = _messages.value + MessageItem(
                        role = "assistant", content = reply, isUser = false
                    )
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + MessageItem(
                    role = "assistant", content = "错误：${e.message}", isUser = false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun extractActions(text: String): List<ActionBlock> {
        val actions = mutableListOf<ActionBlock>()
        val jsonBlocks = extractJsonBlocks(text)
        for (json in jsonBlocks) {
            try {
                val obj = JsonParser.parseString(json).asJsonObject
                val action = obj.get("action")?.asString ?: continue
                val paramsObj = obj.getAsJsonObject("params")
                val params = mutableMapOf<String, String>()
                if (paramsObj != null) {
                    for ((key, value) in paramsObj.entrySet()) {
                        params[key] = value.asString
                    }
                }
                actions.add(ActionBlock(action, params))
            } catch (e: Exception) {
                Log.w(TAG, "JSON 解析失败: $json", e)
            }
        }
        return actions
    }

    private fun extractJsonBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val start = text.indexOf('{', i)
            if (start == -1) break
            var depth = 0
            var j = start
            var inString = false
            var escape = false
            while (j < text.length) {
                val c = text[j]
                if (escape) {
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    inString = !inString
                } else if (!inString) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            val json = text.substring(start, j + 1).trim()
                            if (json.contains("\"action\"")) {
                                blocks.add(json)
                            }
                            i = j + 1
                            break
                        }
                    }
                }
                j++
            }
            if (j >= text.length) break
        }
        return blocks
    }

    private fun stripJsonFromReply(reply: String): String {
        return extractJsonBlocks(reply)
            .fold(reply) { acc, block -> acc.replace(block, "") }
            .trim()
    }

    private fun buildMessages(
        includeSystemPrompt: Boolean
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        if (includeSystemPrompt) {
            messages.add(buildSystemPrompt())
        }

        for (msg in _messages.value) {
            if (msg.content.startsWith("执行结果:")) continue
            messages.add(
                ChatMessage(role = if (msg.isUser) "user" else "assistant", content = msg.content)
            )
        }

        return messages
    }
}
