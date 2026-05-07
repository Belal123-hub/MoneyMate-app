package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val currency: String = "USD",
    @ColumnInfo(name = "wallet_type")
    val walletType: String,
    @ColumnInfo(name = "initial_balance")
    val initialBalance: String = "0.00",
    @ColumnInfo(name = "card_number")
    val cardNumber: String? = null,
    val color: String = "#4D6BFA",
    val balance: String? = null,
    @ColumnInfo(name = "user_id")
    val userId: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)
