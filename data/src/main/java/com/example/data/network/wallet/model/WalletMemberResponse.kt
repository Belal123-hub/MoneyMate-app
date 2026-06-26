package com.example.data.network.wallet.model

import com.example.data.database.entity.WalletMemberEntity
import com.example.domain.wallet.model.WalletMember
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletMemberResponse(
    @SerialName("user_id")
    val userId: Int,
    @SerialName("user_email")
    val userEmail: String,
    @SerialName("user_name")
    val userName: String,
    val role: String,
    @SerialName("joined_at")
    val joinedAt: String,
    @SerialName("wallet_id")  // Add this if it exists in the response
    val walletId: Int? = null  // Make it optional if not always present
) {
    fun toDomain(walletId: Int): WalletMember = WalletMember(
        id = this.userId,
        walletId = walletId,
        userId = this.userId,
        userEmail = this.userEmail,
        userName = this.userName,
        role = this.role,
        joinedAt = this.joinedAt
    )

    fun toDomain(): WalletMember = WalletMember(
        id = this.userId,
        walletId = this.walletId ?: 0,
        userId = this.userId,
        userEmail = this.userEmail,
        userName = this.userName,
        role = this.role,
        joinedAt = this.joinedAt
    )

    fun toLocalEntity(walletId: Int, isSynced: Boolean = true): WalletMemberEntity = WalletMemberEntity(
        id = this.userId,
        walletId = walletId,
        userId = this.userId,
        userEmail = this.userEmail,
        userName = this.userName,
        role = this.role,
        joinedAt = this.joinedAt,
        isSynced = isSynced
    )

    // Add overload that uses the walletId from the response if available
    fun toLocalEntity(isSynced: Boolean = true): WalletMemberEntity = WalletMemberEntity(
        id = this.userId,
        walletId = this.walletId ?: 0,
        userId = this.userId,
        userEmail = this.userEmail,
        userName = this.userName,
        role = this.role,
        joinedAt = this.joinedAt,
        isSynced = isSynced
    )
}