package com.example.smartphoneagent.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smartphoneagent.data.SettingsViewModel
import com.example.smartphoneagent.llm.ModelProvider
import com.example.smartphoneagent.plugin.PluginConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val currentProvider by viewModel.currentProvider.collectAsState()
    val plugins by viewModel.plugins.collectAsState()
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    var apiKey by remember { mutableStateOf(viewModel.getApiKey(selectedProvider)) }
    var importJson by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProvider) {
        apiKey = viewModel.getApiKey(selectedProvider)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("模型设置", style = MaterialTheme.typography.titleMedium)
            }

            item {
                ModelSelector(
                    selectedProvider = selectedProvider,
                    onSelect = {
                        selectedProvider = it
                        viewModel.updateProvider(it)
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${selectedProvider.displayName} API Key") },
                    placeholder = { Text("请输入 API Key...") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveApiKey(selectedProvider, apiKey) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存 API Key")
                }
            }

            item {
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("插件管理", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Button(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("从 JSON 导入插件")
                }
            }

            items(plugins) { plugin ->
                PluginItem(
                    plugin = plugin,
                    onToggle = { viewModel.togglePlugin(plugin.id, it) },
                    onDelete = { viewModel.deletePlugin(plugin.id) },
                    showDelete = !plugin.isBuiltIn
                )
            }

            if (plugins.isEmpty()) {
                item {
                    Text(
                        "暂无插件。可使用 JSON 导入来添加自定义插件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("权限设置", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开无障碍服务设置")
                }
            }

            item {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开通知权限设置")
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入插件 JSON") },
            text = {
                Column {
                    Text("请粘贴 JSON 数组格式的插件配置：")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("[{\"id\":\"...\",\"name\":\"...\",...}]") },
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importPluginsJson(importJson)
                    showImportDialog = false
                    importJson = ""
                }) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedProvider: ModelProvider,
    onSelect: (ModelProvider) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModelProvider.entries.forEach { provider ->
            FilterChip(
                selected = selectedProvider == provider,
                onClick = { onSelect(provider) },
                label = { Text(provider.displayName) }
            )
        }
    }
}

@Composable
fun PluginItem(
    plugin: PluginConfig,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
                Text(plugin.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("类型：${plugin.actionType}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Switch(
                checked = plugin.enabled,
                onCheckedChange = onToggle
            )
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
