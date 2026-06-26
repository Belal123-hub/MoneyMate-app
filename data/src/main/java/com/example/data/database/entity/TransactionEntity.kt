package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val amount: String,
    val note: String?,
    val type: String,
    @ColumnInfo(name = "transaction_date")
    val transactionDate: String,
    @ColumnInfo(name = "wallet_id")
    val walletId: Int,
    @ColumnInfo(name = "category_id")
    val categoryId: Int,
    @ColumnInfo(name = "user_id")
    val userId: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    val tags: List<String> = emptyList(),
    @ColumnInfo(name = "receipt_url")
    val receiptUrl: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)
