package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val description: String?,
    val image: String?,
    val deadline: LocalDate?,
    @ColumnInfo(name = "goal_amount")
    val goalAmount: Double,
    @ColumnInfo(name = "amount_saved")
    val amountSaved: Double,
    @ColumnInfo(name = "wallet_id")
    val walletId: Int,
    val currency: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)
