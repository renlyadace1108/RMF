# RMF (Renly Manage Platform) 架构设计规范

## 1. 项目简介与定位
RMF（Renly Manage Platform）是一款面向个人全方位效能与生活管理的多端协同中枢软件。涵盖：
* **日程规划与排期**（日程表 / Timetable / 时间块）
* **时间管理与专注记录**（番茄钟、设备活动捕获、专注度打分）
* **收入支出管理**（多账户、分类预算、自然语言记账、报表统计）
* **Gemini AI 智能监管**（偏离预警、执行复盘、自动排程、财务洞察）

---

## 2. 目标平台与技术选型

| 平台 / 终端 | 运行系统 | 核心技术栈 | 终端形态与特色能力 |
| :--- | :--- | :--- | :--- |
| **手机端** | ColorOS 17 (Android) | Kotlin + Jetpack Compose | 竖屏紧凑视图、即时记账、便携通知、通知栏常驻 |
| **平板端** | 联想小新 Pad Pro 12.7 (ZUI OS / Android) | Kotlin + Compose (Adaptive Layout) | 12.7 寸大屏分栏 (Master-Detail)、横屏 PC 模式、多任务浮窗、手写笔标记 |
| **桌面端** | Windows 10/11 | C# (.NET 8/9 + WinUI 3 / Modern WPF) | 极致性能、Native AOT、常驻极低内存、Win32 活跃窗口与空闲监控 (AI监管数据源) |

---

## 3. 数据同步协议（Google Drive API）

* **模式**：本地优先（Local-First）+ 云端增量快照同步。
* **存储位置**：Google Drive 私有应用存储区（`drive.appdata`），对普通用户在网盘网页中不可见，防止误删，且具备最高隐私隔离。
* **同步策略**：
  1. **本地存储**：
     * Android/ZUI 端：SQLite (Room / SQLDelight)
     * Windows 端：SQLite (Entity Framework Core / SQLite-net)
  2. **云端文件结构**：
     * `meta.json`：记录最后同步时间戳、设备指纹、Schema 版本。
     * `delta_logs/`：按设备+时间戳追加的变更操作集（Event Sourcing / Change Data Capture）。
     * `snapshots/rmf_backup_latest.json`：定期的全量归档压缩快照。
  3. **冲突解决**：采用 LWW（Last-Write-Wins，基于 UTC 微秒级时间戳）或 CRDT 思维解决并发变更冲突。

---

## 4. Gemini AI 监管与智能中枢

* **接入方式**：直接集成 Google Gemini REST/SDK (Gemini 2.5 Flash / 1.5 Pro)。
* **三大核心 AI 职能**：
  1. **执行监督 (Supervisor)**：
     * 定期分析 Windows 捕获的实际应用活动（如 VS Code 50分钟，浏览器刷视频 30分钟）与 Android 端亮屏时长；
     * 对比当前时段规划的「日程时间块」；
     * 发现严重偏离时生成温柔或严厉的“拉回提醒”；每晚生成《每日执行力与效能复盘报告》。
  2. **智能排程 (Planner)**：
     * 支持输入模糊诉求（“本周安排看两部电影、周三前把报告初稿完成”），AI 分析空闲时隙推荐时间块。
  3. **智能记账 (Finance)**：
     * 自然语言输入（“中午和同事吃牛肉火锅一共180AA每人90微信付的”）直接抽取：金额、币种、类别、账户、参与人、时间。

---

## 5. 项目源码目录规划

```text
d:/RMF/
├── docs/                     # 架构方案、同步协议、数据字典、Gemini Prompt 设计
├── schemas/                  # 多端统一的 JSON Schema / 数据交换模型规范
├── rmf-mobile/               # Android 工程 (Kotlin) - 兼顾 ColorOS 手机与 ZUI 平板自适应 UI
│   ├── app/
│   │   ├── src/main/java/com/renly/rmf/
│   │   │   ├── core/         # 数据库(Room)、网络、Google Drive 同步引擎、Gemini 客户端
│   │   │   ├── domain/       # 业务逻辑 (Schedule, Finance, Focus, AISupervisor)
│   │   │   └── ui/           # Compose 界面 (adaptive 适配手机与平板)
├── rmf-windows/              # Windows 原生工程 (C# .NET 9 + WinUI 3)
│   ├── RMF.Windows/
│   │   ├── Core/             # SQLite 仓储、Google Drive 同步客户端、Gemini 客户端
│   │   ├── Services/         # 活跃窗口监控、系统空闲检测、系统托盘
│   │   ├── ViewModels/
│   │   └── Views/            # Fluent Design 界面
```
