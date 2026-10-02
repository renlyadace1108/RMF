# RMF Google Drive API 同步协议与无服务器架构规范

RMF 采用 **“客户端本地优先 (Local-First) + Google Drive AppData 云端增量中继”** 的同步架构。
无需自建和维护任何云服务器，零运维成本，天然保障数据私密性与高可用性。

---

## 1. 认证与授权方案

各端统一采用 Google 官方 OAuth 2.0 规范，仅请求最小必要权限：
* **OAuth Scope**：`https://www.googleapis.com/auth/drive.appdata`
  * 仅允许应用读写属于自身的隐藏存储文件夹。
  * 用户在电脑或网页版 Google Drive 文件列表中**看不到**这些同步碎文件，杜绝用户误移、误删。
  * 卸载或清理应用云端数据时，可通过 Google 账户管理一键销毁。

### 平台认证实现：
* **Android (ColorOS & 小新 Pad ZUI)**：使用 Google Play Services / Credential Manager，实现无缝一键 Google 账号授权获取访问令牌。
* **Windows (C# .NET)**：使用 `Google.Apis.Drive.v3` 官方 SDK，支持系统内置默认浏览器完成 OAuth 2.0 回调并安全缓存在 Windows Credential Manager（凭据管理器）。

---

## 2. 云端目录与文件结构 (`drive.appdata`)

```text
Google Drive: [appDataFolder]/
├── sync_manifest.json          # 全局同步元数据（版本、最后同步时间戳、全量快照版本）
├── snapshots/
│   └── rmf_snapshot_v1.json    # 定期全量合并的压缩快照（减少回放开销）
└── changes/                    # 增量变更事件日志（Event Sourcing）
    ├── change_WIN_1727850000000.json
    ├── change_PHONE_1727851200000.json
    └── change_PAD_1727852400000.json
```

---

## 3. 同步工作流与冲突消解

### 3.1 同步流程（拉取 -> 合并 -> 推送）
1. **拉取阶段 (Pull)**：
   * 客户端向 Drive 发送请求，对比云端 `sync_manifest.json` 与本地保存的 `last_synced_version`。
   * 若有新变更，拉取所有尚未合并的 `changes/*.json`。
2. **合并阶段 (Merge)**：
   * 本地按 `updated_at` 时间戳依序回放变更。
   * **冲突裁决（LWW 策略）**：对于同一实体的并发修改，以 UTC 时间戳较新者为准；若为删除操作，`is_deleted = true` 优先持久化（墓碑机制 Tombstone）。
3. **推送阶段 (Push)**：
   * 将本地离线期间产生的变更集打包为一个增量文件（如 `change_WIN_{timestamp}.json`）上传到 `drive.appdata`。
   * 原子更新 `sync_manifest.json` 中的版本标识。
4. **归档与压缩 (Compaction)**：
   * 当增量变更文件累积超过一定阈值（如 50 个或一周一次），由活跃客户端发起合并，将所有实体状态压制为一个新的 `rmf_snapshot_v*.json`，并清理旧变更，避免网盘碎片堆积。

---

## 4. 离线支持与网络韧性

* **断网即用**：所有读取、创建、编辑均首先写入本地 SQLite，界面无任何网络阻塞等待（0ms 响应）。
* **后台排队与自动重试**：
  * Android：利用 `WorkManager`，在系统检测到网络恢复且满足电量策略时静默同步。
  * Windows：使用后台后台定时器 `PeriodicTimer`，并在检测到系统唤醒 / 网络可用时触发同步。
