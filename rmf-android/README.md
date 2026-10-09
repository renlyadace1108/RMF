# RMF (Resonance Moment Flow) Android 客户端

RMF 移动端原生应用，基于现代 Android 技术栈（Kotlin + Jetpack Compose + Room + Material 3）构建，与 Windows 桌面端实现 100% 数据库与跨端同步对齐。

---

## 🌟 核心特性与架构

1. **跨端零偏差数据底座 (Cross-Platform Sync Engine)**
   - 数据库表结构直接镜像 Windows 端 SQLite 规范 (`rmf.db`)。
   - 通过 Google Drive 专属同步文件夹 `RMF_CloudSync` 交换 `rmf_cloud_backup.db` 快照。
   - 采用快照版本计数器 `snapshot_metadata` 与 `updated_at` (LWW) 机制解决多端冲突。

2. **九大核心业务模块**
   - 📅 **日程时间块 (Schedule)**: 深度工作/浅层事务/恢复缓冲三级认知负荷、DoD 验收标准、精力收支。
   - 🎓 **智能课程表 (Timetable)**: 1~20 周、单双周、自定义精确起止时间模式、临时调课/停课单周微调、课表时间流投影。
   - 📚 **学习专题与进度 (Study)**: 支持学时 (HOURS)、节数 (LESSONS)、页数 (PAGES)、自定义单位 (CUSTOM) 多模式量化与打卡，内置艾宾浩斯复盘算法。
   - 🧘 **心流专注空间 (Focus)**: 倒计时时钟与被打断日志记录 (外部/自扰/突发/技术)。
   - 🎯 **OKR 目标管理 (Goals)**: 北极星目标标记与四层分解。
   - 💰 **极简日常记账 (Expenses)**: 结合时间流动的日常开销追踪。
   - ☁️ **云端漫游与同步 (Settings)**: Google Drive 备份恢复管理。

---

## 🛠️ 在 Android Studio 中打开与运行

1. 打开 **Android Studio**。
2. 选择 **File -> Open...**。
3. 浏览并选择目录：`d:\RMF\rmf-android`。
4. Android Studio 将自动识别 Gradle 并进行同步（Gradle Version Catalog `libs.versions.toml`）。
5. 点击 **Run** 即可在真机或模拟器上启动体验！
