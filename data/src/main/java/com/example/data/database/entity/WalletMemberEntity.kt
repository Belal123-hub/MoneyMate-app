package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.wallet.model.WalletMember

@Entity(tableName = "wallet_members")
data class WalletMemberEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "wallet_id")
    val walletId: Int,
    @ColumnInfo(name = "user_id")
    val userId: Int,
    @ColumnInfo(name = "user_email")
    val userEmail: String,
    @ColumnInfo(name = "user_name")
    val userName: String,
    val role: String,
    @ColumnInfo(name = "joined_at")
    val joinedAt: String,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)

fun WalletMemberEntity.toDomain(): WalletMember = WalletMember(
    id = this.id,
    walletId = this.walletId,
    userId = this.userId,
    userEmail = this.userEmail,
    userName = this.userName,
    role = this.role,
    joinedAt = this.joinedAt
)

// ADD THIS MISSING FUNCTION
fun WalletMember.toLocalEntity(
    walletId: Int,
    isSynced: Boolean = true
): WalletMemberEntity = WalletMemberEntity(
    id = this.id,
    walletId = walletId,
    userId = this.userId,
    userEmail = this.userEmail,
    userName = this.userName,
    role = this.role,
    joinedAt = this.joinedAt,
    isSynced = isSynced
)