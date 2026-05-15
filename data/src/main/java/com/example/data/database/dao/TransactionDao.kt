package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC")
    suspend fun getTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE wallet_id = :walletId ORDER BY transaction_date DESC")
    suspend fun getTransactionsByWalletId(walletId: Int): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM transactions WHERE wallet_id = :walletId")
    suspend fun deleteTransactionsByWalletId(walletId: Int)

    @Query("UPDATE transactions SET wallet_id = :newId WHERE wallet_id = :oldId")
    suspend fun reassignWalletId(oldId: Int, newId: Int)

    @Query("SELECT * FROM transactions WHERE is_synced = 0")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE is_synced = 0")
    fun observeUnsyncedTransactions(): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET is_synced = 1, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun markTransactionsSynced(ids: List<Int>, updatedAt: Long)

    @Query("SELECT * FROM transactions WHERE id = :tempId")
    suspend fun getTransactionByTempId(tempId: Int): TransactionEntity?

    /**
     * Sum(income) − sum(expense) for [year]/[month] on wallets treated as savings.
     * Matches app type `"savings"` plus common backend variants (e.g. `savings_account`, "High Savings").
     * Uses YYYY-MM prefix of [transaction_date] (length ≥ 10, ISO-style dates).
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN LOWER(TRIM(t.type)) = 'income' THEN CAST(t.amount AS REAL) ELSE 0.0 END), 0.0) -
            COALESCE(SUM(CASE WHEN LOWER(TRIM(t.type)) = 'expense' THEN CAST(t.amount AS REAL) ELSE 0.0 END), 0.0)
        FROM transactions AS t
        INNER JOIN wallets AS w ON w.id = t.wallet_id
        WHERE (
            LOWER(TRIM(w.wallet_type)) = 'savings'
            OR LOWER(TRIM(w.wallet_type)) = 'saving'
            OR LOWER(TRIM(w.wallet_type)) LIKE '%savings%'
        )
        AND LENGTH(t.transaction_date) >= 10
        AND CAST(substr(t.transaction_date, 1, 4) AS INTEGER) = :year
        AND CAST(substr(t.transaction_date, 6, 2) AS INTEGER) = :month
        AND (
            LOWER(TRIM(t.type)) = 'income'
            OR LOWER(TRIM(t.type)) = 'expense'
        )
        """
    )
    suspend fun sumSavingsWalletIncomeMinusExpenseForMonth(year: Int, month: Int): Double

    /**
     * Net transaction flow for [walletId]: sum(income) − sum(expense).
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN LOWER(TRIM(type)) = 'income' THEN CAST(amount AS REAL) ELSE 0.0 END), 0.0) -
            COALESCE(SUM(CASE WHEN LOWER(TRIM(type)) = 'expense' THEN CAST(amount AS REAL) ELSE 0.0 END), 0.0)
        FROM transactions
        WHERE wallet_id = :walletId
        AND (
            LOWER(TRIM(type)) = 'income'
            OR LOWER(TRIM(type)) = 'expense'
        )
        """
    )
    suspend fun sumIncomeMinusExpenseForWallet(walletId: Int): Double
}
