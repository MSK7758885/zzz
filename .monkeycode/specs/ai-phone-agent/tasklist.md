# AI Phone Agent 实现任务清单

## 阶段 1：空项目骨架 + 基础编译环境

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 1.1 | 创建根目录 build.gradle.kts (Kotlin DSL, AGP 8.2.0, Kotlin 1.9.20, KSP) | `/build.gradle.kts` | Done |
| 1.2 | 创建 settings.gradle.kts (插件仓库, 项目名 AIPhoneAgent) | `/settings.gradle.kts` | Done |
| 1.3 | 配置 app/build.gradle.kts (Compose, Retrofit, 协程, Room, WorkManager, minSdk 24, targetSdk 34) | `/app/build.gradle.kts` | Done |
| 1.4 | 创建 AndroidManifest.xml (INTERNET 权限, 启动 Activity) | `/app/src/main/AndroidManifest.xml` | Done |
| 1.5 | 创建 MainActivity.kt (Compose 界面, "AI 助手已就绪") | `/app/src/main/java/.../MainActivity.kt` | Done |
| 1.6 | 创建 themes.xml (Material 3 基础主题) | `/app/src/main/res/values/themes.xml` | Done |
| 1.7 | 执行编译验证 `./gradlew assembleDebug` | - | Done |

## 阶段 2：聊天界面 + 智谱 AI 集成

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 2.1 | 创建 ChatScreen (LazyColumn 消息列表 + 输入框 + 发送按钮) | `/app/.../ui/ChatScreen.kt` | Done |
| 2.2 | 创建 ApiKeyDialog (首次启动弹窗输入 API Key) | `/app/.../ui/ApiKeyDialog.kt` | Done |
| 2.3 | 创建 PreferencesManager (SharedPreferences, API Key 存储) | `/app/.../data/PreferencesManager.kt` | Done |
| 2.4 | 创建数据模型 (ChatMessage, ZhipuRequest/Response) | `/app/.../data/ZhipuModels.kt` | Done |
| 2.5 | 创建 ZhipuApiService (Retrofit 接口定义) | `/app/.../data/ZhipuApiService.kt` | Done |
| 2.6 | 创建 ZhipuAdapter (GLM-4-Flash API 调用) | `/app/.../llm/ZhipuAdapter.kt` | Done |
| 2.7 | 创建 LlmAdapter 接口 + ModelProvider 枚举 | `/app/.../llm/LlmAdapter.kt` | Done |
| 2.8 | 创建 ChatViewModel (消息管理, API 调用, JSON 解析) | `/app/.../data/ChatViewModel.kt` | Done |
| 2.9 | 创建 ActionExecutor 空壳 (日志级别) | `/app/.../plugin/ActionExecutor.kt` | Done |
| 2.10 | 执行编译验证 | - | Done |

## 阶段 3：多模型热切换 + 插件化框架

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 3.1 | 创建 OpenAiAdapter | `/app/.../llm/OpenAiAdapter.kt` | Done |
| 3.2 | 创建 GeminiAdapter | `/app/.../llm/GeminiAdapter.kt` | Done |
| 3.3 | 创建 DeepSeekAdapter | `/app/.../llm/DeepSeekAdapter.kt` | Done |
| 3.4 | 创建 PluginConfig 数据类 | `/app/.../plugin/PluginConfig.kt` | Done |
| 3.5 | 创建 PluginManager (内置插件 + JSON 导入 + 启用/禁用) | `/app/.../plugin/PluginManager.kt` | Done |
| 3.6 | 完善 ActionExecutor (openApp, setVolume, screenshot, getSensor) | 同 2.9 | Done |
| 3.7 | 创建 SettingsScreen (模型选择, Key 配置, 插件管理, 权限入口) | `/app/.../ui/SettingsScreen.kt` | Done |
| 3.8 | 创建 SettingsViewModel | `/app/.../data/SettingsViewModel.kt` | Done |
| 3.9 | 更新 MainActivity (ChatScreen / SettingsScreen 切换) | 同 1.5 | Done |
| 3.10 | 执行编译验证 | - | Done |

## 阶段 4：长期任务 + 无障碍服务

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 4.1 | 创建 TaskEntity (Room 实体) | `/app/.../task/TaskEntity.kt` | Done |
| 4.2 | 创建 TaskDao | `/app/.../task/TaskDao.kt` | Done |
| 4.3 | 创建 AppDatabase (Room 数据库) | `/app/.../task/AppDatabase.kt` | Done |
| 4.4 | 创建 TaskRepository | `/app/.../task/TaskRepository.kt` | Done |
| 4.5 | 创建 TaskForegroundService (前台服务 + 通知) | `/app/.../service/TaskForegroundService.kt` | Done |
| 4.6 | 创建 LongRunningWorker (WorkManager 后台任务) | `/app/.../service/LongRunningWorker.kt` | Done |
| 4.7 | 创建 AgentAccessibilityService (UI树, 手势, 输入) | `/app/.../service/AgentAccessibilityService.kt` | Done |
| 4.8 | 创建 accessibility_service_config.xml | `/app/src/main/res/xml/accessibility_service_config.xml` | Done |
| 4.9 | 更新 ActionExecutor 集成无障碍服务 (click_on_text, click_position, swipe, input_text, get_ui_tree) | 同 2.9 | Done |
| 4.10 | 更新 AndroidManifest.xml (Service 声明, 前台服务权限) | 同 1.4 | Done |
| 4.11 | 执行编译验证 | - | Done |

## 阶段 5：最终打包与测试

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 5.1 | 执行 `./gradlew clean assembleRelease` 生成签名 APK | - | Pending |
| 5.2 | 编写安装与权限配置测试指南 | 本文档 | Done |

---

## 测试指南：OPPO Reno13 安装步骤

1. **安装 APK**
   - 将生成的 APK 文件传输到手机
   - 在文件管理器中点击 APK 文件进行安装
   - 如遇"禁止安装未知来源应用"提示，进入 设置 > 安全 > 允许安装未知应用

2. **授予通知权限**
   - 首次启动应用时会自动请求通知权限，点击"允许"
   - 或手动进入 设置 > 通知与状态栏 > 应用通知管理 > AI 手机助手 > 开启

3. **开启无障碍服务**
   - 进入应用设置页，点击"打开无障碍服务设置"
   - 或在系统 设置 > 其他设置 > 无障碍 > 已安装的服务 中找到 "AI 手机助手"
   - 开启服务并确认授权

4. **配置 API Key**
   - 首次启动输入智谱 API Key，或在设置页配置其他模型
   - API Key 获取地址：https://open.bigmodel.cn

5. **功能验证**
   - 发送 "打开设置" → 验证 open_app
   - 发送 "音量调到 50%" → 验证 set_volume
   - 发送 "截图" → 验证 screenshot（需要无障碍服务已开启）
   - 切换到 OpenAI/DeepSeek 模型，发送消息验证 API 调用
   - 在设置中导入测试 JSON 插件验证插件系统

### 自定义插件 JSON 格式示例

```json
[
  {
    "id": "send_dingtalk",
    "name": "发送钉钉消息",
    "description": "通过钉钉机器人发送消息",
    "actionType": "send_dingtalk",
    "enabled": true,
    "params": {"webhook_url": "https://oapi.dingtalk.com/robot/send"}
  }
]
```
