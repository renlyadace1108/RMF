# RMF 核心数据模型规范 (Unified Data Models)

为了保证 **Android (Kotlin)**、**Windows (C#)** 以及 **Google Drive 云同步** 之间的数据完全一致且容易序列化，所有模型均遵循统一的字段规范（UTC 时间戳、UUID 唯一主键、软删除机制）。

---

## 1. 通用元数据字段 (Base Model)
所有实体均包含以下基础审计与同步字段：
* `id` (String / UUID)：全局唯一 ID，生成于本地客户端，无需等待服务端。
* `created_at` (String / ISO 8601 UTC)：创建时间。
* `updated_at` (String / ISO 8601 UTC)：最后修改时间，用于冲突检测（LWW）。
* `device_id` (String)：记录最后变更该记录的设备识别码。
* `is_deleted` (Boolean)：软删除标记（防止多端同步时误复活）。

---

## 2. 日程与规划模块 (`ScheduleItem`)
管理用户的待办、日程安排与日历时间块。

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "编写 RMF 核心同步逻辑",
  "description": "完成 Google Drive API 本地离线队列对接",
  "category": "WORK", // WORK, STUDY, LIFE, HEALTH, FINANCE, OTHER
  "priority": "HIGH", // LOW, MEDIUM, HIGH, URGENT
  "status": "IN_PROGRESS", // PENDING, IN_PROGRESS, COMPLETED, POSTPONED, CANCELLED
  "start_time": "2026-10-03T09:00:00Z",
  "end_time": "2026-10-03T11:30:00Z",
  "is_all_day": false,
  "recurrence_rule": null, // RFC 5545 RRULE 格式（如 "FREQ=DAILY;COUNT=5"）
  "reminders_minutes_before": [15, 60],
  "estimated_minutes": 150,
  "actual_minutes": 130,
  "tags": ["Coding", "RMF", "Architecture"],
  "created_at": "2026-10-02T16:00:00Z",
  "updated_at": "2026-10-02T16:05:00Z",
  "device_id": "WIN-DESKTOP-01",
  "is_deleted": false
}
```

---

## 3. 时间管理与行为监控模块 (`TimeSession` & `ActivityLog`)
记录番茄钟、自主专注计时以及由 Windows 后台捕获的进程活动。

### 3.1 专注会话 (`TimeSession`)
```json
{
  "id": "713a2a6b-bc54-47f9-8431-1e9678129a01",
  "schedule_item_id": "550e8400-e29b-41d4-a716-446655440000", // 可选关联的具体日程
  "session_type": "POMODORO", // POMODORO, STOPWATCH, BACKGROUND_TRACK
  "source_device": "WINDOWS", // WINDOWS, PHONE, TABLET
  "start_time": "2026-10-03T09:00:00Z",
  "end_time": "2026-10-03T09:25:00Z",
  "duration_seconds": 1500,
  "interruptions_count": 1,
  "user_rating": 5, // 1-5 专注自评
  "created_at": "2026-10-03T09:25:05Z",
  "updated_at": "2026-10-03T09:25:05Z",
  "device_id": "WIN-DESKTOP-01",
  "is_deleted": false
}
```

### 3.2 设备行为活动打点 (`DeviceActivityRecord`) —— Windows 专属采集源
```json
{
  "id": "893c5d6e-f2a1-4321-b123-abcdef012345",
  "device_id": "WIN-DESKTOP-01",
  "start_time": "2026-10-03T09:00:00Z",
  "end_time": "2026-10-03T09:30:00Z",
  "process_name": "devenv.exe",
  "window_title": "RMF.sln - Microsoft Visual Studio",
  "category_tag": "PRODUCTIVE", // PRODUCTIVE, NEUTRAL, DISTRACTING, IDLE
  "idle_seconds": 0
}
```

---

## 4. 收入支出财务模块 (`FinanceTransaction` & `Account`)

### 4.1 财务流水条目 (`FinanceTransaction`)
```json
{
  "id": "184b8400-e29b-41d4-a716-998877665544",
  "type": "EXPENSE", // EXPENSE, INCOME, TRANSFER
  "amount": 35.50,
  "currency": "CNY",
  "category": "餐饮美食", // 子类如 "午餐"
  "sub_category": "工作餐",
  "account_id": "ACC-ALIPAY-01",
  "to_account_id": null, // 转账时填入目的账户
  "transaction_time": "2026-10-03T12:15:00Z",
  "note": "午餐麻辣烫",
  "raw_input_text": "今天中午吃了35.5块麻辣烫，支付宝付的", // Gemini 解析前的原句
  "is_ai_parsed": true,
  "created_at": "2026-10-03T12:16:00Z",
  "updated_at": "2026-10-03T12:16:00Z",
  "device_id": "COLOROS-PHONE-01",
  "is_deleted": false
}
```

---

## 5. Gemini AI 监管与复盘模块 (`AISupervisionReport`)
由 Gemini 基于用户一天的日程计划 vs 实际时间投入分析生成。

```json
{
  "id": "901f4c12-3456-789a-bcde-f0123456789a",
  "report_date": "2026-10-03",
  "adherence_score": 85, // 计划执行契合度分值 (0-100)
  "planned_focus_minutes": 360,
  "actual_focus_minutes": 310,
  "distraction_minutes": 50,
  "ai_summary": "今日上午代码开发专注度极高，但在下午 15:00-15:40 出现计划外的网页浏览（检测到 Bilibili）。整体目标达成率良好，建议将深度工作任务固定在早间精力旺盛期。",
  "actionable_suggestions": [
    "在下午 14:30 安排一次 10 分钟眼保健或咖啡休息，避免因疲倦直接滑入无意识摸鱼",
    "明日待办中的'编写测试用例'粒度过大，建议拆分为两个 45 分钟时间块"
  ],
  "created_at": "2026-10-03T23:00:00Z",
  "device_id": "SYSTEM_GEMINI"
}
```
