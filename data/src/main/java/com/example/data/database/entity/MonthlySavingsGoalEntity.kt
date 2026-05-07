package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_savings_goals")
data class MonthlySavingsGoalEntity(
    @PrimaryKey
    val id: Int,
    val month: Int,
    val year: Int,
    @ColumnInfo(name = "target_amount")
    val targetAmount: Double,
    @ColumnInfo(name = "current_saved")
    val currentSaved: Double,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)