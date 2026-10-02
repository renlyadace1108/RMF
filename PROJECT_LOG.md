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
* [ ] **Windows 桌面端工程脚手架 (.NET 10)** (0%)
* [ ] **Android 移动/平板端工程脚手架 (Kotlin Compose)** (0%)
* [ ] **Google Drive 同步引擎核心实现** (0%)
* [ ] **Gemini AI 监管与记账模块实现** (0%)

---

## 📝 3. 变更与行动历史 (Changelog & Session Records)

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
