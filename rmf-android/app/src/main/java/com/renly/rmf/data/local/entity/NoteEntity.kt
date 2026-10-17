package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["is_pinned", "is_deleted"], name = "idx_notes_pinned"),
        Index(value = ["updated_at", "is_deleted"], name = "idx_notes_updated")
    ]
)
data class NoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title", defaultValue = "''")
    val title: String = "",

    @ColumnInfo(name = "content", defaultValue = "''")
    val content: String = "",

    @ColumnInfo(name = "category", defaultValue = "'灵感'")
    val category: String = "灵感", // 灵感, 思考, 待办, 闪念, 摘录, 生活

    @ColumnInfo(name = "color_hex", defaultValue = "'#F59E0B'")
    val colorHex: String = "#F59E0B",

    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Int = 0, // 1 = 置顶

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
