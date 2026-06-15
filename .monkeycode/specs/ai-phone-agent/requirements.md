# AI Phone Agent 需求规格说明书

## 1. 项目概述

**应用名称**：AI Phone Agent  
**包名**：com.example.smartphoneagent  
**核心功能**：用户通过自然语言控制 Android 手机操作，支持多模型切换和插件化扩展  
**最低 SDK**：24  
**目标 SDK**：34  
**技术栈**：Kotlin + Jetpack Compose + MVVM + Room

---

## 2. 功能需求

### FR-01：基础项目骨架

| 编号 | 需求描述 | EARS 模式 |
|------|----------|-----------|
| FR-01-01 | 系统 MUST 使用 Kotlin DSL 构建（build.gradle.kts） | Ubiquitous |
| FR-01-02 | 系统 MUST 集成 Jetpack Compose、Retrofit、Kotlin 协程依赖 | Ubiquitous |
| FR-01-03 | 系统 MUST 提供 Material 3 主题的 Compose 启动界面，显示 "AI 助手已就绪" | Ubiquitous |
| FR-01-04 | 系统 MUST 声明 INTERNET 网络权限 | Ubiquitous |

### FR-02：聊天界面与智谱 AI 集成

| 编号 | 需求描述 | EARS 模式 |
|------|----------|-----------|
| FR-02-01 | 系统 MUST 提供对话历史列表（LazyColumn）和输入框/发送按钮 | Ubiquitous |
| FR-02-02 | 系统 MUST 通过 Retrofit + 协程调用智谱 GLM-4-Flash API | Ubiquitous |
| FR-02-03 | 系统 MUST 在首次启动时弹窗要求用户输入 API Key，并保存到 SharedPreferences | Event-driven |
| FR-02-04 | 系统 MUST 提供 ActionExecutor 模块的日志级别空壳实现 | Ubiquitous |
| FR-02-05 | 当用户发送消息时，系统 SHALL 调用 AI 接口获取回复并展示在对话列表中 | Event-driven |

### FR-03：多模型热切换与插件化框架

| 编号 | 需求描述 | EARS 模式 |
|------|----------|-----------|
| FR-03-01 | 系统 MUST 支持四家模型提供商：智谱(Zhipu GLM)、OpenAI(GPT-4o-mini)、Google Gemini(1.5 Flash)、DeepSeek(DeepSeek-Chat) | Ubiquitous |
| FR-03-02 | 系统 MUST 使用适配器模式（LlmAdapter 接口），统一请求/响应格式 | Ubiquitous |
| FR-03-03 | 系统 MUST 提供设置界面，允许用户选择当前模型并配置对应 API Key | Ubiquitous |
| FR-03-04 | 系统 MUST 实现 PluginManager，支持内置插件和自定义 JSON 插件导入 | Ubiquitous |
| FR-03-05 | 系统 MUST 支持插件的动态启用/禁用 | Ubiquitous |
| FR-03-06 | ActionExecutor MUST 至少支持：打开应用、调节音量、获取加速度传感器数据、截图 | Ubiquitous |
| FR-03-07 | 系统 MUST 兼容 Android 14 的隐式 Intent 和后台限制策略 | Ubiquitous |

### FR-04：长期任务与无障碍服务

| 编号 | 需求描述 | EARS 模式 |
|------|----------|-----------|
| FR-04-01 | 系统 MUST 使用 Room 数据库持久化任务队列 | Ubiquitous |
| FR-04-02 | 当有长时间任务时，系统 SHALL 通过 WorkManager 调度执行 | Event-driven |
| FR-04-03 | 系统 MUST 提供前台服务展示任务进度通知 | Ubiquitous |
| FR-04-04 | 系统 MUST 实现 AccessibilityService 子类，支持获取 UI 树、模拟点击/滑动、输入文本 | Ubiquitous |
| FR-04-05 | 设置界面 MUST 提供无障碍服务开启入口 | Ubiquitous |
| FR-04-06 | ActionExecutor SHALL 集成无障碍服务能力以执行 UI 交互操作 | Ubiquitous |

### FR-05：最终打包与测试

| 编号 | 需求描述 | EARS 模式 |
|------|----------|-----------|
| FR-05-01 | 系统 MUST 能通过 `./gradlew clean assembleRelease` 生成签名 APK | Ubiquitous |
| FR-05-02 | 系统 MUST 提供安装与权限配置测试指南 | Ubiquitous |

---

## 3. 非功能需求

| 编号 | 描述 |
|------|------|
| NFR-01 | 所有 LLM API 调用 MUST 包含超时机制（连接 30s，读取 60s） |
| NFR-02 | API Key MUST 通过 SharedPreferences 安全存储（加密传输） |
| NFR-03 | 无障碍服务声明 MUST 填写清晰的功能说明文字 |
| NFR-04 | 代码 MUST 遵循 MVVM 架构，View 与 ViewModel 职责分离 |
| NFR-05 | 每次代码修改后 MUST 执行编译验证 |

---

## 4. 约束与限制

1. 最低 Android API 24 (Android 7.0)，目标 API 34 (Android 14)
2. 使用 Java 17 编译目标
3. Compose 编译器扩展版本 1.5.4
4. 不允许硬编码 API Key，必须由用户输入
5. 前台服务类型声明为 `specialUse`
6. 无障碍服务必须声明 `canPerformGestures`、`canRetrieveWindowContent`、`canTakeScreenshot`
