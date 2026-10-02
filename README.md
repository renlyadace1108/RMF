# RMF - Renly Manage Platform

> 个人多端协同全功能智能管理中枢（Windows / ColorOS 17 手机 / 小新 Pad Pro 12.7 ZUI 平板）

> 📋 **多 AI 协同与开发日志**：所有架构决策、开发进度与待办请见根目录下的 [PROJECT_LOG.md](file:///d:/RMF/PROJECT_LOG.md)。

## 🌟 核心功能模块
1. **日程规划与日程表 (Schedule & Timetable)**：时间块、日程安排、全天/周期性任务、日历视图。
2. **时间管理与专注记录 (Time & Focus)**：番茄钟、正向计时、设备前台活动记录。
3. **收入支出管理 (Finance & Bookkeeping)**：多账户、智能分类、Gemini 一句话自然语言记账。
4. **Gemini AI 监管与复盘 (AI Supervision)**：
   * 自动对比规划日程与设备前台实际活跃记录；
   * 拖延与注意力分散警报；
   * 每晚自动化生成《每日执行力与效能复盘报告》。

---

## 📱 终端平台架构
* **Windows 桌面端 (`rmf-windows/`)**：
  * **技术栈**：C# (.NET 10 + Modern Fluent UI)
  * **特性**：毫秒级启动、极低内存驻留、Win32 活跃窗口与空闲状态自动监测（为 AI 监管提供高保真数据）。
* **移动与平板端 (`rmf-mobile/`)**：
  * **技术栈**：Kotlin + Jetpack Compose
  * **ColorOS 17 (手机)**：紧凑单列流、状态栏小组件、即时记账与打卡。
  * **小新 Pad Pro 12.7 (ZUI 2025 平板)**：自适应大屏布局（Master-Detail 分栏）、多任务浮窗/分屏、手写笔标注。

---

## ☁️ 同步与 AI 基础设施
* **多端同步**：Google Drive API（隐藏空间 `drive.appdata`），免自建服务器，离线优先 (Local-First)。
* **AI 大脑**：Google Gemini API（Gemini 2.5 Flash / 1.5 Pro）。

---

## 📂 规范文档指引
* [架构总览 (docs/ARCHITECTURE.md)](file:///d:/RMF/docs/ARCHITECTURE.md)
* [统一数据模型 (docs/DATA_MODELS.md)](file:///d:/RMF/docs/DATA_MODELS.md)
* [Google Drive 同步协议 (docs/SYNC_SPEC.md)](file:///d:/RMF/docs/SYNC_SPEC.md)
* [Gemini AI 监管与智能化规范 (docs/GEMINI_SPEC.md)](file:///d:/RMF/docs/GEMINI_SPEC.md)
* [数据校验 Schema (schemas/rmf_schema.json)](file:///d:/RMF/schemas/rmf_schema.json)
