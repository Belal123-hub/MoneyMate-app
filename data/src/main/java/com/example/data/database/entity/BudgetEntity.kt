package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: Int,
    val month: Int,
    val year: Int,
    @ColumnInfo(name = "monthly_limit")
    val monthlyLimit: Double,
    @ColumnInfo(name = "daily_limit")
    val dailyLimit: Double,
    @ColumnInfo(name = "monthly_spent")
    val monthlySpent: Double,
    @ColumnInfo(name = "daily_spent")
    val dailySpent: Double,
    @ColumnInfo(name = "last_updated_date")
    val lastUpdatedDate: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)

