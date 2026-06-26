package com.example.moneymate.ui.offline

import com.example.domain.transaction.model.TransactionEntity

data class TransactionSyncItem(
    val transaction: TransactionEntity,
    val isSynced: Boolean
)
