# RMF (Renly Management Platform) 项目全量研发轨迹与上下文总览 (PROJECT HISTORY & CONTEXT)

> 💡 **给新加入协作的 AI Agent / 开发者**：  
> 本文档详尽记录了项目发起人 **Renly** 从 Day 1 至今提出的所有核心需求、迭代背景、架构决策及功能实现细节。阅读本文档可瞬间理解 RMF 的灵魂与全貌，确保跨设备多 Agent 协同开发时方向高度一致。

---

## 一、 项目背景与核心定位

* **全称**：RMF（Renly Management Platform，Renly 个人全生命周期效能中枢）。
* **作者与版权**：`Made By Renly 2026`（联系方式：`renly20061108@gmail.com`）。
* **核心使命**：为 Renly 量身打造的集**日程排期、大学课程表（Wakeup 级）、深度专注番茄钟、专业健身体能管理、自然语言收支记账、以及 Google Gemini AI 智能监管**于一体的高性能多端协同软件。
* **目标终端**：
  1. **手机端**：OPPO ColorOS 17（Kotlin + Jetpack Compose，深度适配状态栏、流体云胶囊通知）。
  2. **平板端**：联想小新 Pad Pro 12.7 2025（ZUI OS，自适应大屏三列分栏、横屏、手写笔交互）。
  3. **桌面端**：Windows 11（C# .NET 10 + WPF Fluent Design，毫秒级响应、超低内存、全局桌面 HUD/小组件）。
* **数据存储与隐私安全原则**：
  * **代码与数据绝对隔离**：所有动态运行数据（日程、打卡、配置、密钥）**必须保存在系统专属私有目录**（Windows 为 `%APPDATA%\RMF\`，Android 为 App 私有沙盒），**绝不污染或写入 Git 项目源码目录**！
  * **零硬编码密钥**：所有 API Key（Gemini / OpenAI）与 Google OAuth Token 必须由用户在运行时动态输入并私有持久化。

---

## 二、 完整需求演进时间线与用户意图溯源

### 阶段 1：多端工程初始化与核心数据模型设计
* **用户初衷**：“RMF 是我自己使用的多端协同管理软件，包含日程管理规划、AI 监管、日程表、时间管理、收入支出等，目标是 COLOROS17、windows、联想小新 Pad Pro 12.7 (ZUIOS)。”
* **落地成果**：
  * 搭建 Android 原生工程（`rmf-android`）与 Windows 原生工程（`rmf-windows`）。
  * 确立统一数据模型规范（`docs/DATA_MODELS.md`）与云端同步协议（`docs/SYNC_SPEC.md`，基于 Google Drive AppData 私有空间）。
  * 接入 Google Gemini 智能监管大脑（`docs/GEMINI_SPEC.md`），实现计划偏离预警与每日复盘。

---

### 阶段 2：Wakeup 级大学课程表系统研发
* **用户要求**：深度复刻并超越知名大学课程表应用（WakeUp 课程表），支持大学复杂的单双周排课、节次换算与桌面显示。
* **落地成果**：
  * **学期与周次引擎**：支持开学日期设定、当前学周计算、单周/双周/全周过滤。
  * **节次与作息映射**：支持自定义每小节上课时间、休息间隔。
  * **智能调课与课表投影**：支持临时调课/停课，一键将课程表投影进主页时间轴日程。
  * **桌面小组件与提前上课提醒**：提前 15 分钟发出通知与闹钟提醒。

---

### 阶段 3：ColorOS 17 流体云胶囊系统深度适配
* **用户要求**：在 ColorOS 17 系统上拥有像系统级原生功能一样的流体云动态通知体验。
* **落地成果**：
  * 自定义 Notification 远程视图（`RemoteViews`），实现胶囊态（Capsule）与展开态（Expanded）。
  * 课程即将开始、番茄钟专注倒计时、实时健身组间休息均可通过流体云实时流转，支持通知栏常驻与快捷打卡。

---

### 阶段 4：无损升级与纯净数据治理（重要转折）
* **用户要求**：
  1. *“Android 端崩了”* ➔ 排查解决冷启动与兼容性异常。
  2. *“删除测试数据，我要全部都是自己填写的”* ➔ **彻底废除并删除一切假数据/Mock 数据**！
* **落地成果**：
  * **无损升级保障（Lossless Upgrade）**：版本递增或覆盖安装时，底层数据库通过 SQLite 增量脚本与 Room 迁移平滑过渡，历史数据 100% 留存，每次冷启动前自动建立本地安全快照。
  * **纯净数据原则**：设置页提供「一键清空所有业务测试数据」，数据库初始化不再预装任何假日程、假动作、假账单，所有内容必须且只能由用户实际输入驱动。

---

### 阶段 5：专业健身体能模块全面重构
* **用户要求**：*“你参考专业的健身 App 帮我重做这个功能”*
* **落地成果**（参考主流专业健身 App 如训记/Hevy/Strong）：
  * **60+ 权威动作库（`ExerciseLibrary`）**：按胸、背、肩、腿臀、手臂、核心分类，每个动作包含专业发力要领、器械类型（杠铃/哑铃/器械/自重）。
  * **科学分化训练模版**：内置经典推日（Push Day）、拉日（Pull Day）、腿臀日（Leg Day）、上肢黄金复合训练等经典模版，支持一键套用。
  * **精细化按组记录（WorkoutSet）**：支持热身组（W）、正式组（N）、递减组（D）、力竭组（F），即时计算预估 1RM 与总训练容量（Volume kg）。
  * **智能组间休息计时器**：勾选完成一组后，自动弹出 60s/90s/120s 浮动倒计时器，支持 `+30s`、`-15s` 快速调节与自动提示。

---

### 阶段 6：健身计划与主页日程的实时自动双向同步
* **用户要求**：*“健身能不能也同步到主页的日程里面去”*
* **落地成果**：
  * **全场景自动无感同步**：添加动作、修改组数负重、勾选完成、删除动作、套用模版、打卡结算任一操作，均通过 `FitnessScheduleSyncManager`（Android）与 `SyncFitnessPlanToSchedule`（Windows）自动更新主页日程。
  * **确定性 ID 去重保护**：使用 `fitness_schedule_{YYYY-MM-DD}` 唯一 ID，无论编辑多少次，始终在原地更新，**绝不产生重复日程项**。
  * **详细进度呈现**：日程卡片清晰列出训练动作清单与完成组数进度（如 `✓ 杠铃平板卧推 (4/4组完成)`）。
  * **一键直达跳转**：主页日程详情弹窗新增 **「🏋️ 直达健身模块查看与打卡」** 按钮，点击一键跳转至健身页面开始记录。

---

### 阶段 7：开源准备、代码上传与作者署名
* **用户要求**：
  1. *“把版权那个删掉，换成 Made By Renly 2026，然后下一行写 renly20061108@gmail.com”*
  2. *“你帮我上传到 github 了吗 / 你能帮我弄吗”*
  3. *“里面有没有透露个人信息的地方，比如我的 api 什么是的”*
* **落地成果**：
  * 更新双端界面底部署名。
  * 自动在 GitHub 创建公开仓库：[https://github.com/renlyadace1108/RMF](https://github.com/renlyadace1108/RMF)。
  * 完善 `.gitignore`，完成全量安全审计，确认 100% 零 API Key 泄露、零个人数据泄露，全量源码推送到 GitHub `main` 分支。

---

## 三、 代码仓库工程目录总览

```text
d:\RMF\
├── docs/                       # 架构规范与历史记录文档
│   ├── ARCHITECTURE.md         # 总体三端技术栈与协同设计
│   ├── DATA_MODELS.md          # 跨平台通用数据实体模型定义
│   ├── GEMINI_SPEC.md          # Google Gemini AI 智能监管集成规范
│   ├── SYNC_SPEC.md            # Google Drive API AppData 同步协议
│   └── PROJECT_HISTORY.md      # [本文档] 完整需求历史与研发决策上下文
├── rmf-android/                # Android 原生应用 (Kotlin + Jetpack Compose)
│   ├── app/src/main/java/com/renly/rmf/
│   │   ├── MainActivity.kt     # 主入口与全局导航路由
│   │   ├── data/local/         # Room 数据库、DAO 接口与各业务实体
│   │   ├── domain/             # 健身库、课程表计算、流体云、Gemini服务等业务中枢
│   │   └── ui/screens/         # 各功能界面（日程、课表、健身、专注、设置等）
│   └── build.gradle.kts        # Android 构建配置
└── rmf-windows/                # Windows 桌面应用 (C# .NET 10 + WPF Fluent)
    └── RMF.Windows/
        ├── App.xaml / MainWindow.xaml # 主界面 Fluent UI 布局
        ├── MainWindow.xaml.cs   # 核心交互与业务联动（单文件集中高性能调度）
        ├── Models/             # 桌面端实体模型（与 Android 严格对齐）
        ├── Services/           # 数据库、配置服务、Gemini、全局热键、健身库等
        └── setup.iss           # Inno Setup 桌面端打包安装脚本
```

---

## 四、 后续开发准则（给所有协作 Agent 的指引）

1. **坚持纯净无假数据**：绝不要在代码里引入 Mock 测试日程或测试动作，让应用启动时保持真实干净。
2. **保持双端模型对齐**：修改 Android 实体或字段时，同步更新 Windows 对应的 Model 和 SQLite 表结构。
3. **保持无损数据升级**：数据库字段有增改时，编写增量平滑迁移（`ALTER TABLE`），严禁破坏用户的已存数据。
4. **Git 先拉后推**：每次开始编码前先执行 `git pull origin main`，完成后提交清晰语义的 Commit 并 push。
