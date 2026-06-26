package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.GoalEntity

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id DESC")
    suspend fun getGoals(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :goalId LIMIT 1")
    suspend fun getGoalById(goalId: Int): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoals(goals: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteGoalById(goalId: Int)

    @Query("SELECT * FROM goals WHERE is_synced = 0")
    suspend fun getUnsyncedGoals(): List<GoalEntity>

    @Query("UPDATE goals SET is_synced = 1, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun markGoalsSynced(ids: List<Int>, updatedAt: Long)

    @Query("UPDATE goals SET wallet_id = :newId WHERE wallet_id = :oldId")
    suspend fun reassignWalletId(oldId: Int, newId: Int)
}
