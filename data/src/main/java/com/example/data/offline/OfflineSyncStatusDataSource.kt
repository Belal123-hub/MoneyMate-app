package com.example.data.offline

import com.example.data.database.dao.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineSyncStatusDataSource(
    private val transactionDao: TransactionDao
) {
    suspend fun getUnsyncedTransactionIds(): List<Int> =
        transactionDao.getUnsyncedTransactions().map { it.id }

    fun observeUnsyncedTransactionIds(): Flow<Set<Int>> =
        transactionDao.observeUnsyncedTransactions().map { list -> list.map { it.id }.toSet() }
}
