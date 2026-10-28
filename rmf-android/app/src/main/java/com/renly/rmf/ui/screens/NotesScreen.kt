package com.renly.rmf.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.NoteEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val NOTE_PALETTE = listOf(
    "#F59E0B" to "琥珀黄",
    "#10B981" to "薄荷绿",
    "#38BDF8" to "天空蓝",
    "#818CF8" to "薰衣紫",
    "#EC4899" to "蔷薇粉",
    "#64748B" to "沉稳灰"
)

private val NOTE_CATEGORIES = listOf(
    "灵感", "思考", "闪念", "待办", "摘录", "生活"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notesFlow: Flow<List<NoteEntity>>,
    onAddNote: (title: String, content: String, category: String, colorHex: String, isPinned: Boolean) -> Unit,
    onUpdateNote: (NoteEntity) -> Unit,
    onDeleteNote: (String) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onConvertToSchedule: (title: String, content: String) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null
) {
    val notes by notesFlow.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("全部") }
    var showCreateEditDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToConvert by remember { mutableStateOf<NoteEntity?>(null) }

    // 过滤便签
    val filteredNotes = remember(notes, searchQuery, selectedCategoryFilter) {
        notes.filter { note ->
            val matchCategory = selectedCategoryFilter == "全部" || note.category == selectedCategoryFilter
            val matchSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true) ||
                note.category.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    Scaffold(
        containerColor = DarkBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showCreateEditDialog = true
                },
                containerColor = DopamineAmber,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建随记便签", modifier = Modifier.size(26.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 头部标题栏与返回
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkSurface, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💡", fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "随记便签",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "随时记录奇思妙想、灵感火花与随手备忘",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "${filteredNotes.size} 条",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DopamineAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索灵感、标题或内容...", fontSize = 12.5.sp, color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = DopamineAmber,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 分类筛选横滑列表
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filterCategories = listOf("全部") + NOTE_CATEGORIES
                filterCategories.forEach { category ->
                    val isSelected = selectedCategoryFilter == category
                    Surface(
                        onClick = { selectedCategoryFilter = category },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) DopamineAmber else DarkSurface,
                        border = BorderStroke(1.dp, if (isSelected) DopamineAmber else DarkBorder),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = category,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 便签瀑布流展示
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📝", fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategoryFilter != "全部") "没有匹配的便签" else "灵感库还是空的",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击右下角「+」快速记录一闪而过的想法！",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val noteColumns = if (configuration.screenWidthDp >= 840) 4 else if (configuration.screenWidthDp >= 600) 3 else 2

                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(noteColumns),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        StickyNoteCard(
                            note = note,
                            onClick = {
                                editingNote = note
                                showCreateEditDialog = true
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                noteToDelete = note
                            },
                            onTogglePin = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePin(note)
                            },
                            onCopyContent = {
                                val fullText = if (note.title.isNotBlank()) "${note.title}\n\n${note.content}" else note.content
                                clipboardManager.setText(AnnotatedString(fullText))
                                Toast.makeText(context, "✓ 便签内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            onConvertToSchedule = {
                                noteToConvert = note
                            }
                        )
                    }
                }
            }
        }
    }

    // 1. 创建 / 编辑便签弹窗
    if (showCreateEditDialog) {
        NoteEditDialog(
            initialNote = editingNote,
            onDismiss = { showCreateEditDialog = false },
            onSave = { title, content, category, colorHex, isPinned ->
                if (editingNote != null) {
                    val updated = editingNote!!.copy(
                        title = title,
                        content = content,
                        category = category,
                        colorHex = colorHex,
                        isPinned = if (isPinned) 1 else 0,
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    )
                    onUpdateNote(updated)
                    Toast.makeText(context, "✓ 便签已更新", Toast.LENGTH_SHORT).show()
                } else {
                    onAddNote(title, content, category, colorHex, isPinned)
                    Toast.makeText(context, "✓ 灵感已保存", Toast.LENGTH_SHORT).show()
                }
                showCreateEditDialog = false
            },
            onDelete = if (editingNote != null) {
                {
                    noteToDelete = editingNote
                    showCreateEditDialog = false
                }
            } else null
        )
    }

    // 2. 删除确认弹窗
    if (noteToDelete != null) {
        val target = noteToDelete!!
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text("删除便签", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    text = "确定要删除这条便签「${if (target.title.isNotBlank()) target.title else target.content.take(15)}」吗？此操作无法撤销。",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNote(target.id)
                        noteToDelete = null
                        Toast.makeText(context, "已删除便签", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineRed)
                ) {
                    Text("确认删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    // 3. 转为日程 / 待办弹窗
    if (noteToConvert != null) {
        val target = noteToConvert!!
        val defaultTitle = if (target.title.isNotBlank()) target.title else target.content.lines().firstOrNull()?.take(20) ?: "新灵感任务"
        var convertTitle by remember { mutableStateOf(defaultTitle) }
        var isBacklog by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { noteToConvert = null },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚀", fontSize = 18.sp, modifier = Modifier.padding(end = 6.dp))
                    Text("将便签转为日程待办", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("将当前的灵感便签一键沉淀到行动流，随时跟进与落地：", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = convertTitle,
                        onValueChange = { convertTitle = it },
                        label = { Text("任务标题") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg,
                            focusedBorderColor = DopamineAmber,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { isBacklog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBacklog) DopamineAmber.copy(alpha = 0.2f) else DarkBg,
                            border = BorderStroke(1.dp, if (isBacklog) DopamineAmber else DarkBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📥 存入待办箱", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (isBacklog) DopamineAmber else TextPrimary)
                                Text("无时间排期", fontSize = 10.5.sp, color = TextSecondary)
                            }
                        }

                        Surface(
                            onClick = { isBacklog = false },
                            shape = RoundedCornerShape(10.dp),
                            color = if (!isBacklog) DopamineBlue.copy(alpha = 0.2f) else DarkBg,
                            border = BorderStroke(1.dp, if (!isBacklog) DopamineBlue else DarkBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📅 排入今日日程", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (!isBacklog) DopamineBlue else TextPrimary)
                                Text("自动分配时间", fontSize = 10.5.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = convertTitle.ifBlank { "灵感速记任务" }
                        val fullNotes = "来源便签 [${target.category}]:\n${target.content}"
                        onConvertToSchedule(finalTitle, fullNotes)
                        noteToConvert = null
                        Toast.makeText(context, "✓ 已成功转为日程待办！", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber)
                ) {
                    Text("立即转化", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToConvert = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * 便签卡片 (Sticky Note Card)
 * 采用拟物贴纸轻质感设计：顶部色彩装饰条、分类徽标、置顶图钉、正文预览与快捷操作
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StickyNoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onCopyContent: () -> Unit,
    onConvertToSchedule: () -> Unit
) {
    val defaultAmber = DopamineAmber
    val noteColor = remember(note.colorHex, defaultAmber) {
        try {
            Color(android.graphics.Color.parseColor(note.colorHex))
        } catch (_: Exception) {
            defaultAmber
        }
    }
    val isPinned = note.isPinned == 1

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(
            width = if (isPinned) 1.5.dp else 1.dp,
            color = if (isPinned) noteColor else noteColor.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // 顶部条：颜色指示 + 分类标签 + 置顶标识
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类胶囊
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = noteColor.copy(alpha = 0.18f),
                    border = BorderStroke(0.5.dp, noteColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = note.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = noteColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isPinned) {
                    Text("📌", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 标题 (若有)
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 正文内容
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    fontSize = 12.sp,
                    color = if (note.title.isNotBlank()) TextSecondary else TextPrimary,
                    lineHeight = 17.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "（无内容）",
                    fontSize = 11.sp,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 底部操作与时间栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 更新时间简写
                Text(
                    text = formatNoteTime(note.updatedAt),
                    fontSize = 9.5.sp,
                    color = TextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )

                // 快捷操作小图标
                // 1. 置顶
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "置顶",
                        tint = if (isPinned) noteColor else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // 2. 复制
                IconButton(
                    onClick = onCopyContent,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "复制",
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // 3. 转化为日程
                IconButton(
                    onClick = onConvertToSchedule,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowOutward,
                        contentDescription = "转为日程",
                        tint = noteColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * 便签编辑 / 新建弹窗
 */
@Composable
private fun NoteEditDialog(
    initialNote: NoteEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, category: String, colorHex: String, isPinned: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var category by remember { mutableStateOf(initialNote?.category ?: "灵感") }
    var selectedColorHex by remember { mutableStateOf(initialNote?.colorHex ?: "#F59E0B") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned == 1) }

    val defaultAmber = DopamineAmber
    val accentColor = remember(selectedColorHex, defaultAmber) {
        try {
            Color(android.graphics.Color.parseColor(selectedColorHex))
        } catch (_: Exception) {
            defaultAmber
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialNote != null) "✏️ 编辑便签" else "💡 捕捉灵感",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "删除",
                            tint = DopamineRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. 标题输入
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("标题（可选）", color = TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 正文多行输入
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("在此写下你的灵感、思路或临时备忘...", color = TextSecondary, fontSize = 13.sp) },
                    minLines = 5,
                    maxLines = 10,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 分类选择
                Text("分类标签", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NOTE_CATEGORIES.forEach { cat ->
                        val isSelected = category == cat
                        Surface(
                            onClick = { category = cat },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) accentColor else DarkBg,
                            border = BorderStroke(1.dp, if (isSelected) accentColor else DarkBorder),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else TextSecondary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 便签调色盘
                Text("便签贴纸色彩", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NOTE_PALETTE.forEach { (hex, _) ->
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (hex == "#64748B") Color.White else Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. 置顶选项开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📌", fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                    Text(
                        text = "置顶此便签",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurface
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isBlank() && title.isBlank()) {
                        return@Button
                    }
                    onSave(title.trim(), content.trim(), category, selectedColorHex, isPinned)
                },
                enabled = content.isNotBlank() || title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("保存", color = if (selectedColorHex == "#64748B") Color.White else Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

private fun formatNoteTime(isoStr: String): String {
    return try {
        if (isoStr.isBlank()) return ""
        val dt = if (isoStr.contains("T")) LocalDateTime.parse(isoStr) else LocalDateTime.parse("${isoStr}T00:00:00")
        dt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    } catch (_: Exception) {
        if (isoStr.length >= 16) isoStr.substring(5, 16).replace("T", " ") else isoStr
    }
}
