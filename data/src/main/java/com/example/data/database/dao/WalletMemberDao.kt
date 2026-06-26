package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.WalletMemberEntity

@Dao
interface WalletMemberDao {
    @Query("SELECT * FROM wallet_members WHERE wallet_id = :walletId ORDER BY joined_at ASC")
    suspend fun getMembersByWalletId(walletId: Int): List<WalletMemberEntity>

    @Query("SELECT COUNT(*) FROM wallet_members WHERE wallet_id = :walletId")
    suspend fun getMemberCount(walletId: Int): Int

    @Query(
        "SELECT role FROM wallet_members WHERE wallet_id = :walletId AND user_id = :userId LIMIT 1"
    )
    suspend fun getRoleForMember(walletId: Int, userId: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<WalletMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: WalletMemberEntity)

    @Query("DELETE FROM wallet_members WHERE wallet_id = :walletId AND user_id = :userId")
    suspend fun deleteMember(walletId: Int, userId: Int)

    @Query("DELETE FROM wallet_members WHERE wallet_id = :walletId")
    suspend fun deleteMembersForWallet(walletId: Int)

    @Query(
        "UPDATE wallet_members SET role = :role WHERE wallet_id = :walletId AND user_id = :userId"
    )
    suspend fun updateMemberRole(walletId: Int, userId: Int, role: String)
}
