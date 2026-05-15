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
    /**
     * Last net (income − expense on savings wallets) for this goal month when [currentSaved] was reconciled.
     * Null means not bootstrapped yet — first recalc locks anchor to current tx net without changing [currentSaved].
     */
    @ColumnInfo(name = "savings_tx_net_anchor")
    val savingsTxNetAnchor: Double? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)