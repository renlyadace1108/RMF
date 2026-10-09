package com.renly.rmf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.ui.theme.*

@Composable
fun TutorialScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "📖 RMF 使用教程与心流工作流",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "多维时间管理、课表动态投影与学习攻坚指南",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. ⚡ 自然语言闪念输入", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DopamineBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "在日程页顶部输入框，你可以直接输入自然语言，例如：\n" +
                        "• 「明早 9:00 电路实验 p:高 d:45m #专业课」\n" +
                        "系统会自动识别日期、起止时间、优先级(高/中/低)以及标签，并自动排期。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("2. 🎓 课表动态投影到日程", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DopamineGreen)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "进入「课表」板块，维护你的学期课程表与上课时间节点。\n" +
                        "点击「⚡ 投影到今日日程」，系统将根据当前学期周次（自动判断单双周与临时调课），将今日所有课程一键生成当日时间块日程。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("3. 🎯 专注时钟与打断记录", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DopaminePurple)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "进入「专注」板块，支持全屏极简倒计时与画中画(PiP)小窗浮窗。\n" +
                        "专注过程中如有外界干扰，可实时打卡记录打断原因与损耗耗时，辅助后续精力分析与时间复盘。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("4. 📚 艾宾浩斯记忆与学时追踪", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DopamineOrange)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "在「学习」板块，为你的专业课、考证或书籍建立知识攻坚主题。\n" +
                        "系统遵循 1, 2, 4, 7, 15 天艾宾浩斯复习曲线算法，并在今日有到期复习主题时展示智能复习横幅，助你牢固掌握知识。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("5. ☁️ 跨端数据同步与备份", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DopamineBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "在「工作台」板块，支持通过 Google Drive 无感同步 rmf_cloud_backup.db。\n" +
                        "电脑版与安卓版采用完全统一的 SQLite 底层结构，随时上传/下载覆盖，无缝切换设备无缝协作。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
