# AI Phone Agent 技术设计文档

## 1. 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │  ChatScreen  │  │SettingsScreen│  │ ApiKeyDialog  │ │
│  │  (Compose)   │  │  (Compose)   │  │   (Compose)   │ │
│  └──────┬───────┘  └──────┬───────┘  └───────────────┘ │
│         │                 │                              │
├─────────┼─────────────────┼──────────────────────────────┤
│         │    ViewModel Layer                             │
│  ┌──────┴───────┐  ┌──────┴───────┐                     │
│  │ ChatViewModel│  │SettingsVM   │                      │
│  └──────┬───────┘  └──────┬───────┘                     │
├─────────┼─────────────────┼──────────────────────────────┤
│         │    Domain / Data Layer                         │
│  ┌──────┴───────────────────┐                           │
│  │     LlmAdapter (I/F)     │ (适配器模式)               │
│  │  Zhipu│OpenAI│Gemini│DS  │                           │
│  └──────┬───────────────────┘                           │
│  ┌──────┴───────┐  ┌──────────────────┐                │
│  │ActionExecutor│  │  PluginManager   │                │
│  └──────┬───────┘  └──────────────────┘                │
│  ┌──────┴──────────────────────────────┐               │
│  │   AccessibilityService (System)     │               │
│  └─────────────────────────────────────┘               │
├─────────────────────────────────────────────────────────┤
│                   Persistence Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │PreferencesMgr│  │  Room (Tasks)│  │ WorkManager   │ │
│  └──────────────┘  └──────────────┘  └───────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 模块详设

### 2.1 表示层 (Presentation)

#### MainActivity (`app/src/main/java/com/example/smartphoneagent/MainActivity.kt`)
- 唯一入口 Activity，持有 ChatViewModel 和 SettingsViewModel
- 通过 StateFlow 驱动界面状态切换（对话 / 设置）
- Android 13 (API 33) 以上启动时请求 POST_NOTIFICATIONS 权限

#### ChatScreen (`app/src/main/java/com/example/smartphoneagent/ui/ChatScreen.kt`)
- 顶部：标题 "AI 手机助手" + 设置图标按钮
- 主体：LazyColumn 消息列表，LaunchedEffect 自动滚动到底部
- 底部：OutlinedTextField + 发送 IconButton
- 空状态提示文案："AI 助手已就绪，请输入消息开始对话"
- API Key 未配置时弹出 ApiKeyDialog
- 加载中显示 CircularProgressIndicator

#### SettingsScreen (`app/src/main/java/com/example/smartphoneagent/ui/SettingsScreen.kt`)
- 模型设置：FilterChip 横向选择（智谱/OpenAI/Gemini/DeepSeek），加密输入框填 Key
- 插件管理：JSON 导入按钮，插件 Card 列表（Switch 启用/禁用，非内置可删除）
- 权限入口：跳转系统无障碍设置页、通知权限设置页

#### ApiKeyDialog (`app/src/main/java/com/example/smartphoneagent/ui/ApiKeyDialog.kt`)
- 首次启动弹窗，要求输入智谱 API Key
- 非空校验，支持跳过

---

### 2.2 ViewModel 层

#### ChatViewModel (`app/src/main/java/com/example/smartphoneagent/data/ChatViewModel.kt`)

核心消息处理流程：

1. 用户输入 -> sendMessage()
2. buildMessages() 构建上下文（System Prompt + 历史对话）
3. getCurrentAdapter() 按当前模型获取 LlmAdapter
4. adapter.chat() 调用 LLM API
5. extractActions() 从回复中解析操作指令 JSON
6. 有操作 -> ActionExecutor.execute() + 二次 LLM 调用（传执行结果）
7. 无操作 -> 直接展示回复

**System Prompt 生成**：从 PluginManager 动态获取已启用插件列表，注入提示词，要求 AI 以 JSON 格式嵌入操作指令：
```json
{"action": "操作名", "params": {"参数名": "参数值"}}
```

**JSON 操作块解析** (`extractJsonBlocks`)：使用括号计数状态机遍历文本，正确处理嵌套 JSON、字符串转义，当括号深度归零时截取完整 JSON 块。解析失败通过 Log.w 记录日志。

#### SettingsViewModel (`app/src/main/java/com/example/smartphoneagent/data/SettingsViewModel.kt`)
- 管理 ModelProvider 选择状态
- 各模型 API Key 的读写
- 代理 PluginManager 的启用/禁用/导入/删除

---

### 2.3 领域层 (Domain)

#### LlmAdapter 适配器接口

```kotlin
interface LlmAdapter {
    val modelName: String
    suspend fun chat(messages: List<ChatMessage>): String
}
```

工厂函数 `createLlmAdapter(provider, apiKey)` 按 ModelProvider 枚举返回对应实例。

```
ModelProvider 枚举:
  ZHIPU   -> ZhipuAdapter   (glm-4-flash, Retrofit + Gson, https://open.bigmodel.cn)
  OPENAI  -> OpenAiAdapter  (gpt-4o-mini, OkHttp + Gson, https://api.openai.com)
  GEMINI  -> GeminiAdapter  (gemini-1.5-flash, OkHttp + Gson, https://generativelanguage.googleapis.com)
  DEEPSEEK-> DeepSeekAdapter(deepseek-chat, OkHttp + Gson, https://api.deepseek.com)
```

统一消息格式 `List<ChatMessage>`，各适配器内部自行转换 API 请求体。

#### PluginConfig & PluginManager (`app/src/main/java/com/example/smartphoneagent/plugin/`)

```kotlin
data class PluginConfig(
    val id: String,          // 唯一标识
    val name: String,        // 显示名称
    val description: String, // 描述
    val actionType: String,  // 操作类型，对应 ActionExecutor.when 分支
    val enabled: Boolean,    // 启用状态
    val params: Map<String, String>, // 默认参数
    val isBuiltIn: Boolean   // true=内置, false=自定义
)
```

**内置插件（9 个）**：

| ID | 功能 | 系统依赖 |
|----|------|----------|
| open_app | 通过包名打开应用 | PackageManager |
| set_volume | 调节媒体音量 | AudioManager |
| screenshot | 截取屏幕 | AccessibilityService |
| get_sensor | 读取加速度传感器 | SensorManager |
| get_ui_tree | 获取界面 UI 树 | AccessibilityService |
| click_on_text | 按文字点击 | AccessibilityService |
| click_position | 按坐标点击 | AccessibilityService |
| swipe | 滑动操作 | AccessibilityService |
| input_text | 输入文本 | AccessibilityService |

**插件管理**：
- 内置插件状态持久化到 `plugin_builtin_states` (SharedPreferences)
- 自定义插件通过 JSON 导入，保存到 `plugin_configs` (SharedPreferences)
- 支持运行时启用/禁用/删除（仅自定义）

#### ActionExecutor (`app/src/main/java/com/example/smartphoneagent/plugin/ActionExecutor.kt`)

```kotlin
fun execute(action: String, params: Map<String, String> = emptyMap()): String
```

执行前校验插件启用状态，按 action 分发到对应方法，返回结果字符串。无障碍相关操作通过 `AgentAccessibilityService.instance` 单例调用。

---

### 2.4 服务层 (Service)

#### AgentAccessibilityService (`app/src/main/java/com/example/smartphoneagent/service/AgentAccessibilityService.kt`)

配置（`app/src/main/res/xml/accessibility_service_config.xml`）：
```xml
<accessibility-service
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true"
    android:notificationTimeout="100" />
```

核心能力：

| 方法 | 用途 | 实现 |
|------|------|------|
| getUiTree() | 递归遍历 UI 树输出可读文本 | rootInActiveWindow 深度遍历 |
| performClick(x,y) | 坐标模拟点击 | GestureDescription.StrokeDescription |
| performSwipe(sx,sy,ex,ey) | 模拟滑动 | GestureDescription |
| inputText(text) | 向焦点输入框输入文字 | ACTION_SET_TEXT |
| clickOnText(text) | 按文字查找并点击 | 递归遍历 + ACTION_CLICK |

生命周期：onServiceConnected 设置单例 instance，onDestroy/onUnbind 清空。

#### TaskForegroundService (`app/src/main/java/com/example/smartphoneagent/service/TaskForegroundService.kt`)
- Android 前台 Service，显示持久通知含"停止"按钮
- foregroundServiceType 声明为 specialUse（Android 14+）

#### LongRunningWorker (`app/src/main/java/com/example/smartphoneagent/service/LongRunningWorker.kt`)
- 继承 CoroutineWorker，从 TaskDao 读取任务
- 调用 ActionExecutor 执行，执行期间 setForeground() 显示通知
- 最多 3 次重试（Result.retry），最终标记 failed

---

### 2.5 持久化层 (Persistence)

#### PreferencesManager (`app/src/main/java/com/example/smartphoneagent/data/PreferencesManager.kt`)

SharedPreferences 存储键：

| 键 | 类型 | 说明 |
|----|------|------|
| zhipu_api_key | String? | 智谱 API Key |
| openai_api_key | String? | OpenAI API Key |
| gemini_api_key | String? | Gemini API Key |
| deepseek_api_key | String? | DeepSeek API Key |
| current_provider | String | 当前模型 (默认 "zhipu") |
| has_completed_setup | Boolean | 初始设置完成标记 |

#### Room Database (`app/src/main/java/com/example/smartphoneagent/task/`)

数据库名：`ai_phone_agent_db`，版本 1

TaskEntity (tasks 表)：
- id: Long (自增主键)
- title, description: String
- actionType, actionParams (JSON 字符串): String
- status: String (pending/completed/failed)
- createdAt, updatedAt: Long

TaskDao 提供 Flow 观察查询和挂起读写。AppDatabase 使用双检锁单例模式。

---

### 2.6 构建配置

| 项 | 版本 |
|----|------|
| AGP | 8.2.0 |
| Kotlin | 1.9.20 |
| KSP | 1.9.20-1.0.14 |
| Compose BOM | 2023.10.01 |
| Compose Compiler | 1.5.4 |
| Room | 2.6.1 |
| WorkManager | 2.9.0 |
| Retrofit | 2.9.0 |
| OkHttp | 4.12.0 |
| Gson | 2.10.1 |
| Coroutines | 1.7.3 |

minSdk = 24, targetSdk = 34, Java 17

Release 使用 Debug 签名密钥（`~/.android/debug.keystore`）

---

## 3. 数据流

```
用户输入自然语言
     │
     ▼
ChatViewModel.sendMessage()
     │
     ├─ buildMessages() ─ 构建 System Prompt + 历史上下文
     │
     ▼
getCurrentAdapter() ─ 获取当前模型适配器
     │
     ▼
LlmAdapter.chat() ──── LLM 云端 API
     │
     ▼
extractActions() ─ 括号计数状态机提取 JSON 操作块
     │
     ├── 有操作 ── ActionExecutor.execute()
     │                │
     │                ├── open_app ── PackageManager
     │                ├── set_volume ── AudioManager
     │                ├── screenshot ── AccessibilityService
     │                ├── get_sensor ── SensorManager
     │                └── click/swipe/input ── AccessibilityService
     │                │
     │                ▼
     │           执行结果 ── 二次 LlmAdapter.chat() ── 最终回复
     │
     └── 无操作 ── 直接展示回复
```

---

## 4. 权限模型

| 权限 | 用途 | 授予方式 |
|------|------|----------|
| INTERNET | LLM API 网络请求 | 自动 |
| FOREGROUND_SERVICE | 长期任务通知 | 自动 |
| FOREGROUND_SERVICE_SPECIAL_USE | Android 14 前台服务合规 | 自动 |
| POST_NOTIFICATIONS | Android 13+ 通知 | 启动时运行时请求 |
| BIND_ACCESSIBILITY_SERVICE | 无障碍服务绑定 | 用户手动在系统设置开启 |

---

## 5. 错误处理策略

| 场景 | 处理方式 |
|------|----------|
| API Key 未配置 | ApiKeyDialog 弹窗，拒绝发送消息 |
| LLM API 调用失败 | 捕获异常，展示包含错误消息的 Bubble |
| 插件未启用 | 返回提示文案，列出缺失插件名 |
| 无障碍服务未开启 | 返回提示说明需开启服务 |
| JSON 操作块解析失败 | Log.w 记录日志，继续按纯文本处理 |
| WorkManager 任务失败 | 最多重试 3 次，最终标记 failed |
| 网络超时 | OkHttp 超时机制 (连接 30s / 读取 60s) |
