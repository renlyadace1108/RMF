package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE is_deleted = 0 ORDER BY transaction_time DESC")
    fun getAllActiveExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(expense: ExpenseEntity)

    @Query("UPDATE expenses SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT * FROM expenses")
    suspend fun getAllRawExpenses(): List<ExpenseEntity>
}
