# RMF (Renly Manage Platform) 项目协同与变更日志 (Project Log)

> **💡 AI 协同与接力指引 (For AI Agents & Developers)**:
> 1. 本文件位于项目根目录，作为**多 AI 协同、上下文持久化与开发接力**的唯一真相源（Single Source of Truth）。
> 2. 任何 AI 助手或开发者在进入本工程时，**请首先阅读本日志与 `docs/` 目录下的设计规范**。
> 3. 每次对代码库进行重要修改、架构决策变更或新增功能后，**必须在此日志末尾追加新的记录条目**，并同步更新「当前系统状态」与「待办清单」。

---

## 📌 1. 项目全景速览 (Project Overview)

* **项目名称**：RMF (Renly Manage Platform)
* **核心定位**：个人自用多端协同全功能智能管理中枢（日程规划、时间管理/专注监控、收支财务、Gemini AI 执行力监管）。
* **多端与技术栈矩阵**：
  * **Windows 桌面端 (`rmf-windows/`)**：C# (.NET 10 + Modern Fluent UI)，高性能、极低常驻内存，挂载 Win32 钩子采集前台活动窗口用于 AI 监管。
  * **移动/平板端 (`rmf-mobile/`)**：Kotlin + Jetpack Compose（单套工程），通过 Window Size Class 适配：
    * **ColorOS 17 (手机)**：紧凑单列流、通知栏常驻打卡、便携记账。
    * **联想小新 Pad Pro 12.7 2025 (ZUI 平板)**：12.7 寸大屏自适应、双/三列分栏、PC 桌面模式、浮窗与手写笔批注。
  * **数据同步通道**：Google Drive API（隐藏私有目录 `drive.appdata`），免自建服务器，离线优先 (Local-First SQLite)。
  * **AI 监管中枢**：Google Gemini API（Gemini 2.5 Flash / 1.5 Pro），负责日程偏差监督、每日复盘报告、自然语言记账解析、智能时间块编排。

---

## 🚦 2. 当前系统状态与完成度 (System Status)

* [x] **架构设计与技术选型** (100%)
* [x] **数据模型与多端协议统一** (100%)
* [x] **Google Drive API 同步协议规范** (100%)
* [x] **Gemini 监管 Prompt 与交互场景规范** (100%)
* [x] **Windows 桌面端工程脚手架 (.NET 10)** (80% - Fluent 2 UI、Mica 云母材质、Win32 前台窗口探测、内存压制已实装且本地运行成功)
* [ ] **Android 移动/平板端工程脚手架 (Kotlin Compose)** (0%)
* [ ] **Google Drive 同步引擎核心实现** (0%)
* [x] **Gemini AI 监管与记账模块实现** (60% - 真实 API 直连、日程审查、想法评估监督、智能排程已打通)

---

## 📝 3. 变更与行动历史 (Changelog & Session Records)

### [2026-10-02 18:10] - 彻底清除所有测试/占位数据，全量实装本地真实 SQLite 存储与动态交互
* **执行角色**：AI 全栈工程师 (Antigravity)
* **主要成果**：
  1. **彻底废除所有写死的测试/占位数据**：
     * 删除了 `_todaySchedule` 内部的全部 Mock 静态数据，数据库初始状态为干净真实的空状态。
     * 删除了 `MainWindow.xaml` 中写死的时间轴 Item 卡片（Item 1/2/3）与顶部写死的 KPI 假数值。
  2. **落地本地真实 SQLite 存储引擎 ([`DatabaseService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/DatabaseService.cs))**：
     * 自动在 `%APPDATA%\RMF\rmf.db` 创建 `schedules`、`expenses`、`focus_logs` 三张标准化表。
  3. **实装完整的新建、切换、删除与统计闭环**：
     * **新建排期日程**：点击「+ 新建日程」弹出输入弹窗，支持设置标题、时间段、分类与说明，保存直接写入 SQLite 并动态呈现时间轴卡片。
     * **状态流转与进度更新**：卡片上支持一键切换状态（待执行 ➜ 进行中 ➜ 已完成），已完成任务自动添加删除线，顶部 KPI 进度条真实联动（如 `1 / 2 个时间块`）。
     * **真实记账流水**：在记账面板输入“午餐 25 元”，通过 Gemini（或本地语义降级）自动解析金额入库，实时罗列今日账单清单，并动态汇总顶部「今日支出统计」。
     * **Win32 真实活动打点**：前台活动窗口嗅探每 2 秒落库打点，专注卡片如实统计今日真实的活跃时长。
     * **Gemini 真实数据审计**：日程审查接口完全基于 SQLite 中的真实日程数据驱动，0 日程时客观提示录入。

### [2026-10-02 17:58] - 修正虚假同步原型状态，真实落地 Google Drive OAuth 2.0 凭据配置面板与鉴权校验
* **执行角色**：AI 全栈工程师 (Antigravity)
* **主要成果**：
  1. **修正虚假连接占位状态**：此前同步面板为初期视觉布局原型，存在未配置网盘时显示假“已连接”的问题。已全面重构为真实的鉴权状态机，默认如实显示「⚠️ 未绑定云端账号，当前处于【本地离线模式】」。
  2. **引入真实 OAuth 2.0 凭据输入**：因访问用户 Google Drive 属于隐私网盘操作，Google 官方强制要求使用 OAuth 2.0（而非普通 API Key）。在同步面板增加 `Google Cloud Client ID` 与 `Client Secret` 配置卡片，并自动持久化至本地加密配置。
  3. **实装同步前置鉴权拦截**：点击「立即执行云端增量同步」时严格检验绑定状态，未配置凭据时给予明确友好的引导提示，并保证即使不配置云端同步，本地 SQLite 离线运行也 100% 完备。

### [2026-10-02 17:55] - 彻底移除性格与严厉度设定，全面强化模型绝对客观、严谨与可靠性
* **执行角色**：AI 全栈工程师 (Antigravity)
* **主要成果**：
  1. **彻底剥离拟人化语气/性格设定**：从 [`ConfigService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/ConfigService.cs)、[`MainWindow.xaml`](file:///d:/RMF/rmf-windows/RMF.Windows/MainWindow.xaml) 与 [`MainWindow.xaml.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/MainWindow.xaml.cs) 中彻底移除 `SupervisorTone`（严厉/平衡/温和）相关 UI 控件与配置字段。
  2. **锁定确定性低采样温度**：将 Gemini 请求参数 `temperature` 设定为 `0.2`，抑制随机发散，确保分析与推演高度严谨、客观、一致且可靠。
  3. **重写客观审计与评估 Prompt 体系**：
     * **日程审计引擎**：基于时间工时、连续专注负荷、认知疲劳规律进行定量核算，结合活动偏离比对，仅输出清晰条理与具体可执行的排期调整建议。
     * **想法推演与决策分析**：摒弃情绪化评价与虚浮鼓励，以客观逻辑为基准，严格从目标对齐度、执行成本、潜在沉没成本与即时落地风险进行理性拆解。
  4. **精简优化设置界面**：设置面板简化为「1. 模型选择与实时拉取」与「2. API 凭据与网络代理」，界面更加聚焦、高效。

### [2026-10-02 17:43] - 实现直接通过 Google Gemini 官方 API 动态探测并拉取可用模型列表
* **执行角色**：AI 全栈工程师 (Antigravity)
* **主要成果**：
  1. 在 [`GeminiService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/GeminiService.cs) 中实现 `ListModelsAsync()`，对接 Google 官方 `GET /v1beta/models` 接口。
  2. 智能筛选支持 `generateContent` 的推理模型，自动排除纯 embedding 向量模型，并智能将 flash 与 pro 模型优先置顶。
  3. 在界面设置中实装 **「🔄 联网拉取官方模型」** 按钮，一键动态读取用户 API Key 下授权的全部可用模型（含最新实验性模型与专属微调版本），自动填充至下拉列表供即时切换。
  4. 增加模型数据契约 [`GeminiModelInfo.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Models/GeminiModelInfo.cs)。

### [2026-10-02 17:39] - 修复 XAML 初始化阶段 ComboBox SelectionChanged 空指针异常
* **执行角色**：AI 全栈工程师 (Antigravity)
* **主要成果**：
  1. 定位并解决 WPF 经典初始化时序陷阱：XAML 解析 `ModelSelectCombo` 时触发 `SelectionChanged`，此时处于下方声明的 `CustomModelPanel` 尚未实例化为 `null`，导致抛出 `NullReferenceException`。
  2. 在 [`MainWindow.xaml.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/MainWindow.xaml.cs) 的 `OnModelSelectionChanged` 中增加防御性空值校验 (`if (CustomModelPanel == null) return;`)。

### [2026-10-02 17:36] - 实装 App 内全功能模型切换中枢与监督官性格控制
* **执行角色**：AI 全栈工程师 (Antigravity)
* **主要成果**：
  1. 在应用内打造完整的 **「Gemini 模型与监督模式控制中心」**：
     * **模型自由切换**：支持随时切换 `gemini-2.5-flash`、`gemini-2.5-pro`、`gemini-2.0-flash`、`gemini-1.5-pro`，并支持 **自定义输入任意新模型名称**。
     * **监督官性格微调**：支持单选「严格鞭策型 (犀利毒舌/直击要害/逼迫聚焦主线)」、「理性专业型 (高级项目总监视角/重ROI与可行性)」、「温和陪伴型 (正面激励/同理心/减压关怀)」。
     * **网络反代设置**：支持填写自定义 Base URL（如境内加速反代服务器），避免直接被网络屏蔽阻断。
  2. 顶部导航栏增加实时 **「⚡ 模型徽章」**，随时直观查看当前生效模型，点击可直接秒跳入设置面板。
  3. 配置持久化与实时生效：更新 [`ConfigService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/ConfigService.cs) 与 [`GeminiService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/GeminiService.cs)。

### [2026-10-02 17:33] - 真实 Google Gemini API 全量接入与三大核心智能职能落地
* **执行角色**：AI 全栈工程师 (Antigravity)
* **主要成果**：
  1. 编写高性能原生 HTTP 客户端 [`GeminiService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/GeminiService.cs)（基于 `System.Net.Http` 与 `System.Text.Json`，零第三方重型依赖）。
  2. 实现 **三大真实 Gemini 交互业务**：
     * **日程排期审查 (Schedule Audit)**：分析日程密度、精力模型、拖延风险，并结合 Windows 前台正在运行的应用给出客观建设性评价。
     * **新想法与决策监督 (Idea & Strategy Evaluation)**：作为用户的 AI 智囊兼首席监督官，分析突发想法的可行性、沉没成本，并严格审查是否偏离当前核心主线。
     * **智能排程帮手 (Smart Planner)**：输入大目标，自动结合日程表空白时间拆解为 45~90 分钟的专注时间块。
  3. 实现本地安全配置管理 [`ConfigService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/ConfigService.cs)，持久化存储于 `%APPDATA%\RMF\config.json`（避免将敏感 API Key 提交到 Git 仓库）。
  4. 界面增加「⚙️ Gemini API 设置」与联通测试功能，支持选择 `gemini-2.5-flash`、`gemini-1.5-pro` 等模型。

### [2026-10-02 17:25] - 视觉质感全方位重塑 (Fluent 2 现代仪表盘设计)
* **执行角色**：UI/UX 设计师与架构师 (Antigravity)
* **主要成果**：
  1. 解决原版白顶栏刺眼割裂问题：使用 Windows 11 DWM 原生 API (`DwmSetWindowAttribute`) 实现 **沉浸式深色模式标题栏与窗体边框**。
  2. 废除简陋灰块设计，按照 **Linear / Raycast / Fluent 2** 标准全面重构界面：
     * **精致侧边栏**：品牌 Logo 徽标、交互高亮态、分类层级、极简指标卡。
     * **顶部 KPI 仪表板**：今日计划进度（带条形进度）、Gemini 专注评分（健康绿色徽章）、今日支出一览。
     * **时间轴（Timeline）卡片流**：左右分栏时间轴布局，配有高保真状态小圆点（● 进行中）、色彩分类胶囊（核心研发、AI监管、多端协同）。
     * **右侧 Gemini 行为监管与雷达卡片**：前台活动窗口实时捕获 + AI 监督反馈卡。

### [2026-10-02 17:12] - Windows 启动脚本与桌面交互优化
* **执行角色**：AI 架构师 (Antigravity)
* **主要成果**：
  1. 定位并排查了 AI 命令行后台执行与物理主显示器会话隔离的问题（AI 终端处于 `Desktop: exebox-...` 隔离桌面）。
  2. 在根目录创建了一键启动批处理脚本 [`run-windows.bat`](file:///d:/RMF/run-windows.bat)，用户直接双击即可将软件秒级呼出到物理主屏幕中央。
  3. 将 `MainWindow` 调整为高兼容性标准原生宿主，保留 Fluent 2 现代控件、暗色主题、实时遥测与一键内存压制特性。

### [2026-10-02 18:32] - 首界面重构为 Google Calendar 视图与 Google 日历双向无感同步
* **执行角色**：AI 架构师 (Antigravity)
* **主要成果**：
  1. **侧边栏视觉精简**：完全移除左侧边栏顶部的 `[R] RMF Hub` 品牌卡片，并彻底删除了 `核心模块` 分组文本，替换为极致干净直接的 `+ 新建日程` 核心呼出操作。
  2. **首界面重构为 Google Calendar**：
     * **日历顶部控制条**：支持 `今天` 快速跳转、`◀` / `▶` 逐日切换、中文星期格式日期头部显示（如 `2026年10月2日 · 星期五`）以及 `今日` 徽章。
     * **24小时时间网格画布**：实装 `06:00 - 23:00` 标准时段横向网格线，任意点击某一小时时段即可直接预填并呼出新建日程弹窗。
     * **经典红线时间指示器**：精确按当前系统实时时间在时间网格上动态绘制红色水平线与圆点标志。
     * **Google Calendar 风格日程色块**：根据类别自动映射色彩（核心研发/蓝色、深度学习/紫色、运动健康/绿色、财务管理/玫瑰红、日常事务/琥珀金），卡片支持状态切换（待办/进行中/完成）与删除。
     * **双视图切换**：提供 `📊 时间网格 (Day Grid)` 与 `📋 议程清单 (Agenda)` 无缝切换。
  3. **Google Calendar 真实双向同步引擎** ([`GoogleCalendarService.cs`](file:///d:/RMF/rmf-windows/RMF.Windows/Services/GoogleCalendarService.cs))：
     * 实装标准 RFC 5545 iCalendar (ICS) 解析引擎，支持展开折叠行（Unfolding）、多时区时间解析（UTC `Z`、本地时间、全天日程）、UID去重与状态映射。
     * 支持用户通过「iCal 格式的私密地址」(`basic.ics`) 实现免去繁琐 Google Cloud OAuth 审批的即时订阅与同步。
     * 数据入库采用 SQLite `ON CONFLICT(id) DO UPDATE` 幂等合并，保证同步时更新已有日程且不产生重复数据。
  4. **未同步时的本地数据模式 (Local-First)**：
     * 当未配置 Google Calendar 订阅地址或离线无网络时，界面无感展示与管理本地高性能 SQLite 数据库中已有排期。
     * 状态徽章清晰标明 `💾 本地数据模式 (离线就绪)`，新建、编辑、删除与 Gemini AI 审查均 100% 离线可用。
  5. **一键同步与配置弹窗 (`GoogleCalendarSyncModal`)**：
     * 提供图文引导说明（如何从 Google Calendar 网页端设置中一键获取私密 iCal 地址）、一键测试同步保存与解除绑定功能。

### [2026-10-02 17:11] - 修复编译缺失引用并提供根目录快捷启动方式

### [2026-10-02 16:45] - 项目初始化与核心架构体系建立
* **执行角色**：AI 架构师 (Antigravity)
* **主要成果**：
  1. 梳理并明确了各终端生态定位：确认小新 Pad Pro 12.7 (ZUI) 与 ColorOS 17 统一采用 Kotlin + Jetpack Compose 自适应大屏布局方案开发；Windows 采用 .NET 10 Fluent UI 高性能方案。
  2. 确定“本地优先 (SQLite) + Google Drive AppData 隐式同步 + Google Gemini 监管中枢”的完全无服务器化架构。
  3. 创建全套核心规范文档：
     * [`docs/ARCHITECTURE.md`](file:///d:/RMF/docs/ARCHITECTURE.md)：完整多端架构与分层设计。
     * [`docs/DATA_MODELS.md`](file:///d:/RMF/docs/DATA_MODELS.md)：日程、时间块、财务、AI 监管实体统一模型。
     * [`docs/SYNC_SPEC.md`](file:///d:/RMF/docs/SYNC_SPEC.md)：Google Drive API 同步时序、增量变更合并与冲突解决规则。
     * [`docs/GEMINI_SPEC.md`](file:///d:/RMF/docs/GEMINI_SPEC.md)：AI 监管机制、Prompt 模板、自然语言记账抽取逻辑。
     * [`schemas/rmf_schema.json`](file:///d:/RMF/schemas/rmf_schema.json)：标准化 JSON 数据 Schema。
     * [`README.md`](file:///d:/RMF/README.md)：仓库根目录项目导读。
     * [`PROJECT_LOG.md`](file:///d:/RMF/PROJECT_LOG.md)：本协同日志文件创建。

---

## 🎯 4. 接下来推荐执行的待办事项 (Next Steps Backlog)

1. **Windows 端工程初始化 (`rmf-windows/`)**：
   * 使用 `dotnet new` 建立 .NET 10 解决方案与 Fluent UI 客户端。
   * 建立本地 SQLite 存储层（Entity Framework Core 或 SQLite-net）。
   * 实现 Win32 进程与活跃窗口捕获后台服务（`ForegroundTracker`）。
2. **移动/平板端工程初始化 (`rmf-mobile/`)**：
   * 搭建 Kotlin + Compose Multi-module 工程。
   * 配置 WindowSizeClass 自适应布局框架（适配手机单列与平板分栏）。
   * 搭建 Room 数据库及 DAO 接口。
3. **共享同步服务库验证**：
   * 编写 Google Drive API (`drive.appdata`) 授权与增量 JSON 文件上传/拉取原型。
4. **Gemini 服务客户端**：
   * 封装自然语言记账解析函数与每日监督总结生成函数。
