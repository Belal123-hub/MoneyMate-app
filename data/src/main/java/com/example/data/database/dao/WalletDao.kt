package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY created_at DESC")
    suspend fun getWallets(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE id = :walletId LIMIT 1")
    suspend fun getWalletById(walletId: Int): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWallet(wallet: WalletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWallets(wallets: List<WalletEntity>)

    @Query("DELETE FROM wallets WHERE id = :walletId")
    suspend fun deleteWalletById(walletId: Int)

    @Query("SELECT * FROM wallets WHERE is_synced = 0")
    suspend fun getUnsyncedWallets(): List<WalletEntity>

    @Query("UPDATE wallets SET is_synced = 1, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun markWalletsSynced(ids: List<Int>, updatedAt: Long)

    @Query("UPDATE wallets SET balance = :balance, updated_at = :updatedAt WHERE id = :walletId")
    suspend fun updateWalletBalance(walletId: Int, balance: String, updatedAt: Long)

    @Query("SELECT * FROM wallets WHERE is_synced = 0")
    fun observeUnsyncedWallets(): Flow<List<WalletEntity>>
}
