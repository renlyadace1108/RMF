package com.renly.rmf.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renly.rmf.ui.theme.*

/**
 * 首次操作教学：如何进入与退出系统设置
 * 专为底部栏左右滑动手势量身定制的动态图文交互指引
 */
@Composable
fun SettingsGestureTutorialDialog(
    onDismiss: () -> Unit,
    onTryNow: () -> Unit
) {
    // 动态手势滑动动画
    val infiniteTransition = rememberInfiniteTransition(label = "GestureSwipeAnimation")
    
    val fingerOffset by infiniteTransition.animateFloat(
        initialValue = -65f,
        targetValue = 65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FingerOffset"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = DarkCard,
                border = BorderStroke(1.5.dp, DopamineCyan.copy(alpha = 0.65f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 顶部徽章
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DopamineCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✨", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "首次操作教学 · 快捷手势",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DopamineCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 标题
                    Text(
                        text = "如何进入「系统设置」？",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "工作台已独立分离，系统设置现拥有专属底栏手势",
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // ---------------------------------------------------------
                    // 动态手势演示卡片 (Dynamic Gesture Demo)
                    // ---------------------------------------------------------
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "👇 动态演示：在底部导航栏左右滑动",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = DopamineBlue
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 模拟底部栏
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkBg)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            DopamineCyan.copy(alpha = glowAlpha)
                                        ),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // 底栏模拟各个按键
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MockNavItem("📅", "日程")
                                    MockNavItem("🎓", "课表")
                                    MockNavItem("📖", "学习")
                                    MockNavItem("⏱️", "专注")
                                    MockNavItem("🚀", "工作台")
                                }

                                // 左右滑动的动态触摸指示器与手势图标
                                Box(
                                    modifier = Modifier
                                        .offset(x = fingerOffset.dp)
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    DopamineCyan.copy(alpha = 0.55f),
                                                    Color.Transparent
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = "滑动指示",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 滑动手势方向示意标签
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = null,
                                    tint = DopamineCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "向左或向右任意轻滑底栏",
                                    fontSize = 11.5.sp,
                                    color = DopamineCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = DopamineCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ---------------------------------------------------------
                    // 核心操作要点说明列表
                    // ---------------------------------------------------------
                    TutorialPointItem(
                        icon = "⚡",
                        title = "滑动直接进入",
                        desc = "在屏幕底部的导航栏上向左或向右滑动，即可瞬间进入「系统设置」。"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TutorialPointItem(
                        icon = "🔄",
                        title = "滑动原路退出",
                        desc = "在设置页面中再次左右滑动底栏（或点击顶栏返回键 ⬅️），即可返回当前页面。"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TutorialPointItem(
                        icon = "🚀",
                        title = "工作台备用入口",
                        desc = "「工作台」页面右上角也常驻有「⚙️ 系统设置」按钮，随时可点击进入。"
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ---------------------------------------------------------
                    // 交互按钮区
                    // ---------------------------------------------------------
                    Button(
                        onClick = onTryNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DopamineCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = DarkBg,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "👉 立即体验滑动进入设置",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkBg
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "我知道了，开始使用",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MockNavItem(icon: String, title: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text(title, fontSize = 9.sp, color = TextMuted)
    }
}

@Composable
private fun TutorialPointItem(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface.copy(alpha = 0.5f))
            .border(BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(icon, fontSize = 16.sp, modifier = Modifier.padding(top = 1.dp, end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
