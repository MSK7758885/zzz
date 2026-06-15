# AI Phone Agent 技术设计文档

## 1. 架构概览

```
\```
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
│  │     LlmAdapter (I/F)     │ ← 适配器模式              │
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
\```
```

---

## 2. 模块详设

### 2.1 表示层 (Presentation)

#### MainActivity (`MainActivity.kt`)
- 作为唯一入口 Activity
- 持有 `ChatViewModel` 和 `SettingsViewModel`
- 通过 `StateFlow` 驱动界面状态切换（对话界面 / 设置界面）
- Android 13+ 启动时请求通知权限

#### ChatScreen (`ui/ChatScreen.kt`)
- 顶部栏：标题 "AI 手机助手" + 设置图标
- 主体：LazyColumn 消息列表，含自动滚动到底部
- 底部：OutlinedTextField + 发送 IconButton
- 空状态提示："AI 助手已就绪，请输入消息开始对话"
- API Key 缺失时弹出 ApiKeyDialog
- 加载中显示 CircularProgressIndicator

#### SettingsScreen (`ui/SettingsScreen.kt`)
- **模型设置区域**：
  - FilterChip 横向选择模型（智谱、OpenAI、Gemini、DeepSeek）
  - 加密输入框填写对应 API Key
  - 保存按钮持久化
- **插件管理区域**：
  - JSON 导入按钮
  - 插件列表（Card + Switch + 内置标识 / 删除按钮）
- **权限设置区域**：
  - 打开无障碍服务设置（跳转系统设置页）
  - 打开通知权限设置

#### ApiKeyDialog (`ui/ApiKeyDialog.kt`)
- 首次启动弹窗，要求输入智谱 API Key
- 输入校验（非空检查）
- "跳过"按钮允许延后配置

---

### 2.2 ViewModel 层

#### ChatViewModel (`data/ChatViewModel.kt`)
```
核心流程:
用户输入 → sendMessage()
  → buildMessages() 构建上下文消息
  → getCurrentAdapter() 获取当前模型适配器
  → adapter.chat() 调用 LLM API
  → extractActions() 从回复中解析 JSON 操作块
  → 如有操作 → ActionExecutor.execute() + 二次 LLM 调用（传执行结果）
  → 无操作 → 直接展示回复
  → 更新 messages StateFlow
```

**消息上下文构建**：
- System Prompt 从 PluginManager 动态获取已启用插件列表
- 指示 AI 以 JSON 格式在回复中嵌入操作块：`{"action":"xxx","params":{"k":"v"}}`
- 过滤历史消息中的执行结果，仅传递用户-AI 对话

**JSON 解析机制**：
- 使用正则 `\{[\s\S]*?"action"\s*:\s*"[^"]+"[\s\S]*?\}` 匹配非贪婪 JSON
- 括号匹配校验（`{` 与 `}` 数量相等）

#### SettingsViewModel (`data/SettingsViewModel.kt`)
- 管理当前模型选择状态
- 提供各模型 API Key 的读写接口
- 代理 PluginManager 的启用/禁用/导入/删除操作

---

### 2.3 领域层 (Domain)

#### LlmAdapter 接口与适配器模式

```kotlin
interface LlmAdapter {
    val modelName: String
    suspend fun chat(messages: List<ChatMessage>): String
}
```

| 适配器 | 模型 | 端点 | 请求方式 |
|--------|------|------|----------|
| `ZhipuAdapter` | glm-4-flash | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | Retrofit + Gson |
| `OpenAiAdapter` | gpt-4o-mini | `https://api.openai.com/v1/chat/completions` | OkHttp 直连 + Gson |
| `GeminiAdapter` | gemini-1.5-flash | `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` | OkHttp + Gson |
| `DeepSeekAdapter` | deepseek-chat | `https://api.deepseek.com/v1/chat/completions` | OkHttp + Gson |

**统一消息格式**：所有适配器对外统一使用 `List<ChatMessage>`（role + content），内部自行处理不同 API 的请求体转换：
- 智谱/OpenAI/DeepSeek：标准 OpenAI 兼容格式
- Gemini：转换为 `{contents: [{parts: [{text}], role}]}` 格式，system 角色映射为 user

#### PluginConfig & PluginManager

```kotlin
data class PluginConfig(
    id: String,        // 唯一标识，如 "open_app"
    name: String,      // 显示名称，如 "打开应用"
    description: String,
    actionType: String, // 操作类型，匹配 ActionExecutor 的 when 分支
    enabled: Boolean,    // 启用状态
    params: Map<String, String>, // 默认参数
    isBuiltIn: Boolean   // 内置或自定义
)
```

**插件生命周期**：
1. 内置插件：启动时从硬编码列表加载，状态持久化到 SharedPreferences
2. 自定义插件：通过 JSON 字符串导入，保存到 SharedPreferences
3. 插件启用/禁用：内置插件单独状态存储，自定义插件更新 enabled 字段
4. 插件删除：仅支持自定义插件

**内置插件列表**（9 个）：
| ID | 功能 | 依赖 |
|----|------|------|
| open_app | 通过包名打开应用 | 无 |
| set_volume | 调节媒体音量 | AudioManager |
| screenshot | 截取屏幕 | AccessibilityService |
| get_sensor | 读取加速度传感器 | SensorManager |
| get_ui_tree | 获取 UI 树 | AccessibilityService |
| click_on_text | 按文字点击 | AccessibilityService |
| click_position | 按坐标点击 | AccessibilityService |
| swipe | 滑动操作 | AccessibilityService |
| input_text | 输入文本 | AccessibilityService |

#### ActionExecutor

```kotlin
fun execute(action: String, params: Map<String, String>): String
```

执行前校验插件是否启用，然后根据 action 分发到对应方法。所有方法返回结果字符串。

---

### 2.4 服务层 (Service)

#### AgentAccessibilityService

**配置** (`accessibility_service_config.xml`)：
```xml
accessibilityEventTypes="typeAllMask"
accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
canPerformGestures="true"
canRetrieveWindowContent="true"
canTakeScreenshot="true"
```

**核心能力**：
| 方法 | 功能 | 实现方式 |
|------|------|----------|
| `getUiTree()` | 递归遍历 UI 树，输出可读文本 | rootInActiveWindow 遍历 |
| `performClick(x, y)` | 坐标点击 | GestureDescription |
| `performSwipe(s,e,x,y)` | 滑动 | GestureDescription Stroke |
| `inputText(text)` | 向焦点输入框输入 | ACTION_SET_TEXT |
| `clickOnText(text)` | 按文字查找并点击 | findNodeByText + ACTION_CLICK |

**生命周期**：
- `onServiceConnected`：设置 `instance` 静态引用
- `onDestroy` / `onUnbind`：清除 `instance`
- ActionExecutor 通过 `AgentAccessibilityService.instance` 单例访问

#### TaskForegroundService

- Android 前台 Service，显示持久通知
- 通知包含 "停止" 按钮（PendingIntent）
- 使用 `specialUse` foregroundServiceType（Android 14+ 要求）

#### LongRunningWorker

- CoroutineWorker 子类
- 从 TaskDao 读取任务，执行 ActionExecutor，更新任务状态
- 支持最多 3 次重试（`Result.retry()`）
- 执行期间通过 `setForeground()` 显示通知

---

### 2.5 持久化层 (Persistence)

#### PreferencesManager

| 键 | 类型 | 说明 |
|----|------|------|
| zhipu_api_key | String? | 智谱 API Key |
| openai_api_key | String? | OpenAI API Key |
| gemini_api_key | String? | Google Gemini API Key |
| deepseek_api_key | String? | DeepSeek API Key |
| current_provider | String | 当前模型（默认 "zhipu"） |
| has_completed_setup | Boolean | 是否完成初始设置 |

#### Room Database (AppDatabase)

```sql
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    actionType TEXT NOT NULL,
    actionParams TEXT NOT NULL,   -- JSON 字符串
    status TEXT NOT NULL DEFAULT 'pending',
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
)
```

TaskDao 提供 Flow 式的观察查询和挂起式的读写操作。

---

### 2.6 构建配置

**AGP**：8.2.0  
**Kotlin**：1.9.20  
**KSP**：1.9.20-1.0.14 (Room 编译时注解)  
**Compose BOM**：2023.10.01  
**Compose Compiler**：1.5.4  

**核心依赖版本**：
- Room：2.6.1
- WorkManager：2.9.0
- Retrofit：2.9.0
- OkHttp：4.12.0
- Gson：2.10.1
- Coroutines：1.7.3

**签名配置**：Release 构建使用 Debug 签名（`~/.android/debug.keystore`）

---

## 3. 数据流

```
用户输入自然语言
     │
     ▼
┌───────────┐    ┌──────────────┐    ┌──────────────────┐
│ChatViewModel│───→│  LlmAdapter  │───→│  LLM 云端 API    │
│  .sendMsg() │    │  .chat()     │    │                  │
└─────┬───────┘    └──────────────┘    └────────┬─────────┘
      │                                         │
      │  系统提示词(含插件列表+消息历史)          │
      │  ←──────────────────────────────────────│
      │         AI 回复 (含 JSON 操作块)         │
      │                                         │
      ▼                                         │
┌──────────────┐                               │
│ extractActions│                               │
│ (Regex 解析) │                               │
└──────┬───────┘                               │
       │                                        │
       │ 有操作? ──Yes──→ ActionExecutor.execute()
       │                      │
       │                      ├── open_app → PackageManager
       │                      ├── set_volume → AudioManager
       │                      ├── screenshot → AccessibilityService
       │                      ├── get_sensor → SensorManager
       │                      └── click/swipe/input → AccessibilityService
       │                      │
       │                      ▼
       │               执行结果字符串
       │                      │
       │                      ▼
       │              二次 LlmAdapter.chat(结果上下文)
       │                      │
       │                      ▼
       │               最终 AI 回复 → 展示给用户
       │
       │ 无操作? ──→ 直接展示 AI 回复给用户
```

---

## 4. 权限模型

| 权限 | 声明位置 | 用途 | 运行时请求 |
|------|----------|------|------------|
| INTERNET | Manifest | 全部 LLM API 调用 | 自动授予 |
| FOREGROUND_SERVICE | Manifest | 长期任务前台通知 | 自动授予 |
| FOREGROUND_SERVICE_SPECIAL_USE | Manifest | Android 14 前台服务合规 | 自动授予 |
| POST_NOTIFICATIONS | Manifest | Android 13+ 通知 | 启动时请求 |
| BIND_ACCESSIBILITY_SERVICE | Manifest Service 声明 | 无障碍服务 | 用户手动在系统设置开启 |

---

## 5. 错误处理策略

| 场景 | 处理方式 |
|------|----------|
| API Key 未配置 | 弹窗提示输入，不允许发送消息 |
| LLM API 调用失败 | 捕获异常，展示错误消息 Bubble |
| 插件未启用 | 返回提示 "插件 'xxx' 未启用，请在设置中开启" |
| 无障碍服务未开启 | 返回提示说明需要开启服务 |
| JSON 解析失败 | 静默跳过格式不正确的 JSON 块 |
| WorkManager 任务失败 | 最多重试 3 次，最终标记 failed |
| 网络超时 | OkHttp 超时（连接 30s，读取 60s） |
