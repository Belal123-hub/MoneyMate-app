package com.example.data.offline

import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import java.util.Locale

/**
 * Sets each wallet [com.example.data.database.entity.WalletEntity.balance] to
 * `initial_balance + sum(income) − sum(expense)` for that wallet’s transactions.
 */
class WalletBalanceRecalculator(
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao
) {

    suspend fun recalculateAllWalletBalances() {
        val wallets = walletDao.getWallets()
        val now = System.currentTimeMillis()
        wallets.forEach { applyBalanceForWallet(it.id, now) }
        println("WALLET_BALANCE: recalculated ${wallets.size} wallet(s)")
    }

    suspend fun recalculateWalletBalance(walletId: Int) {
        applyBalanceForWallet(walletId, System.currentTimeMillis())
    }

    private suspend fun applyBalanceForWallet(walletId: Int, now: Long) {
        val w = walletDao.getWalletById(walletId) ?: return
        val net = transactionDao.sumIncomeMinusExpenseForWallet(walletId)
        val initial = w.initialBalance.toDoubleOrNull() ?: 0.0
        val newBal = initial + net
        val formatted = String.format(Locale.US, "%.2f", newBal)
        walletDao.updateWalletBalance(walletId, formatted, now)
    }
}
