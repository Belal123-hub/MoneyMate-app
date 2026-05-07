package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.MonthlySavingsGoalEntity

@Dao
interface MonthlySavingsGoalDao {
    @Query("SELECT * FROM monthly_savings_goals ORDER BY year DESC, month DESC")
    suspend fun getMonthlySavingsGoals(): List<MonthlySavingsGoalEntity>

    @Query("SELECT * FROM monthly_savings_goals WHERE year = :year AND month = :month LIMIT 1")
    suspend fun getMonthlySavingsGoalByMonth(year: Int, month: Int): MonthlySavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthlySavingsGoal(goal: MonthlySavingsGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthlySavingsGoals(goals: List<MonthlySavingsGoalEntity>)

    @Query("SELECT * FROM monthly_savings_goals WHERE is_synced = 0")
    suspend fun getUnsyncedGoals(): List<MonthlySavingsGoalEntity>

    @Query("UPDATE monthly_savings_goals SET is_synced = 1, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun markGoalsSynced(ids: List<Int>, updatedAt: Long)
}