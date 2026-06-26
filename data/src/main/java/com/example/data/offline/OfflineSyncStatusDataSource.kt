package com.example.data.offline

import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineSyncStatusDataSource(
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao
) {
    suspend fun getUnsyncedTransactionIds(): List<Int> =
        transactionDao.getUnsyncedTransactions().map { it.id }

    fun observeUnsyncedTransactionIds(): Flow<Set<Int>> =
        transactionDao.observeUnsyncedTransactions().map { list -> list.map { it.id }.toSet() }

    fun observeUnsyncedWalletIds(): Flow<Set<Int>> =
        walletDao.observeUnsyncedWallets().map { list -> list.map { it.id }.toSet() }

    suspend fun getUnsyncedWalletIds(): Set<Int> =
        walletDao.getUnsyncedWallets().map { it.id }.toSet()
}
