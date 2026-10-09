package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "type")
    val type: String = "EXPENSE", // EXPENSE, INCOME

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "category")
    val category: String = "餐饮",

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "raw_input_text")
    val rawInputText: String = "",

    @ColumnInfo(name = "transaction_time")
    val transactionTime: String,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String
)
