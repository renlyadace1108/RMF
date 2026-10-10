package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

/**
 * 隐私政策与用户服务协议合规状态管理
 * 符合工信部双清单规范及各大应用商店 (OPPO 开放平台/华为/小米等) 审查要求
 */
object PrivacyPreferences {
    private const val PREFS_NAME = "rmf_privacy_compliance_prefs"
    private const val KEY_PRIVACY_ACCEPTED = "privacy_policy_accepted_v1"
    private const val KEY_ACCEPTED_TIME = "privacy_policy_accepted_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 用户是否已阅读并同意隐私政策
     */
    fun isPrivacyAccepted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PRIVACY_ACCEPTED, false)
    }

    /**
     * 设置用户同意状态
     */
    fun setPrivacyAccepted(context: Context, accepted: Boolean) {
        val editor = getPrefs(context).edit()
        editor.putBoolean(KEY_PRIVACY_ACCEPTED, accepted)
        if (accepted) {
            editor.putLong(KEY_ACCEPTED_TIME, System.currentTimeMillis())
        } else {
            editor.remove(KEY_ACCEPTED_TIME)
        }
        editor.apply()
    }

    /**
     * 隐私政策核心摘要 (用于首次弹窗显要提示)
     */
    val PRIVACY_SUMMARY = """
        感谢您信任并使用 RMF (Rhythm Matrix Flow)！
        
        在您开始使用之前，请认真阅读《用户服务协议》与《隐私政策》。为了向您提供个人日程管理、时间块规划、专注计时与习惯打卡等核心效能服务，我们需要向您说明：
        
        1. 【本地离线优先，数据自主可控】
        RMF 坚持「数据本地化」架构，您的日程、清单、课程表、记账及习惯打卡数据默认全部存储在您手机本地 SQLite 数据库中，不设云端数据中心，绝不会未经授权上传您的私人数据。
        
        2. 【设备权限最小化与使用场景】
        • 通知权限：用于在日程/课程到期时发送准时振动提醒与专注流体云胶囊。
        • 闹钟与后台计时权限：用于保障专注番茄钟在熄屏后不被系统强杀，准时提醒。
        • 系统日历权限：仅在您主动开启“系统日历互通同步”时申请，用于将日程写入手机系统日历，随时可关闭。
        • 图片读取：在识别课表截图时通过系统照片选择器单次授权，不读取您的其他照片。
        
        3. 【第三方与 AI 大模型服务 (BYOK 机制)】
        本软件属于纯客户端效能工具，不提供任何境外未备案大模型在线中转。所有智能助理及截图识别功能均为用户自带密钥 (Bring Your Own Key) 模式，由您的设备直连您所授权的第三方合规服务。
        
        若您点击“同意并继续”，即表示您已充分理解并接受上述条款。
    """.trimIndent()

    /**
     * 完整隐私政策正文
     */
    val PRIVACY_POLICY_FULL = """
        【RMF 隐私保护指引】
        更新与生效日期：2026年3月
        
        一、引言
        RMF (Rhythm Matrix Flow，以下简称“本软件”) 极为重视您的隐私与个人信息安全。本隐私指引旨在向您清晰说明我们如何对待您的数据。
        
        二、我们如何收集与使用信息
        1. 业务数据本地化：
           本软件属于个人离线效能工具。您在软件内创建的日程清单、课程表、专注记录、记账明细、习惯打卡等数据，均存储于您本地设备的私有数据库中。
        2. 权限申请与使用原则：
           • POST_NOTIFICATIONS (通知权限)：在设定的提醒时间到来或专注计时进行中向您展示通知。
           • SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM (精确闹钟)：保障日程闹钟的准时触发。
           • FOREGROUND_SERVICE (前台服务)：用于专注计时期间在通知栏维持计时状态，避免系统强杀。
           • READ/WRITE_CALENDAR (系统日历)：属于可选扩展权限，仅在您手动触发系统日历同步功能时按需申请。
        
        三、第三方服务与数据共享
        1. 本软件无中心化注册登录服务器，不向任何第三方广告商、数据画像机构出售或共享您的个人信息。
        2. Google Drive / 坚果云等第三方云同步：为用户主动配置功能，备份数据直接传输至用户个人的私有网盘中，本软件开发者无法访问。
        3. 生成式 AI 接口：本软件内置 AI 助理遵循 BYOK (用户自备 Key) 原则，仅支持用户自主配置阿里云通义千问、DeepSeek 或其他合法合规的第三方 API。调用过程直接由您的客户端向第三方服务商发起，本软件不保存亦不对内容进行二次中转。
        
        四、数据安全与用户权利
        1. 您可以随时在「工作台」中导出全部数据为通用 JSON 或 SQLite 无损快照。
        2. 您可以通过「清空所有业务数据」彻底抹除本地存储的全部记录。
        3. 您可以随时在设置中撤回隐私协议授权或卸载应用。
        
        五、联系开发者
        如有任何疑问、意见或建议，请联系：renly20061108@gmail.com
    """.trimIndent()

    /**
     * 用户服务协议正文
     */
    val USER_AGREEMENT_FULL = """
        【RMF 用户服务协议】
        更新与生效日期：2026年3月
        
        一、协议范围
        欢迎使用 RMF 个人管理软件。本协议是您与 RMF 开发者之间就使用本软件所订立的协议。
        
        二、软件使用规范
        1. 用户权利：您享有按照本软件设计功能在个人设备上安装、使用、管理个人日程的权利。
        2. 合规守法承诺：
           • 您承诺遵守《中华人民共和国网络安全法》《中华人民共和国数据安全法》等法律法规。
           • 在使用第三方 AI 模型接口时，您承诺严格遵守《生成式人工智能服务管理暂行办法》，不得利用相关接口生成或传播违反法律法规、危害国家安全、侵害他人合法权益的内容。
        3. 知识产权声明：
           本软件的 UI 视觉设计、架构代码、原创逻辑受知识产权法律保护，版权归开发者 Renly 所有 (Copyright © 2024-2026 Renly. All Rights Reserved)。
        
        三、免责声明
        1. 本软件按“现状”提供，开发者致力于提供稳定流畅的体验，但因用户设备故障、系统强杀电池策略或第三方云服务异常造成的数据异常，用户需自行做好定期快照备份。
        2. 第三方 API 服务（包括但不限于 Google Drive、各大大模型接口）由第三方独立运营并受其各自服务条款约束，用户需自行承担调用所产生的费用及网络连通性责任。
    """.trimIndent()
}
