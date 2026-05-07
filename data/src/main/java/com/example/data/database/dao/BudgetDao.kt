package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.BudgetEntity

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month LIMIT 1")
    suspend fun getBudgetByMonth(year: Int, month: Int): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE is_synced = 0 ORDER BY updated_at ASC")
    suspend fun getUnsyncedBudgets(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("UPDATE budgets SET is_synced = 1, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun markBudgetsSynced(ids: List<Int>, updatedAt: Long)
}

