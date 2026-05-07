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

    @Query("SELECT * FROM transactions WHERE is_synced = 0")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE is_synced = 0")
    fun observeUnsyncedTransactions(): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET is_synced = 1, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun markTransactionsSynced(ids: List<Int>, updatedAt: Long)

    @Query("SELECT * FROM transactions WHERE id = :tempId")
    suspend fun getTransactionByTempId(tempId: Int): TransactionEntity?
}
