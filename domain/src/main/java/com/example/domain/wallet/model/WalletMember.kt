package com.example.domain.wallet.model

data class WalletMember(
    val id: Int,
    val walletId: Int,
    val userId: Int,
    val userEmail: String,
    val userName: String,
    val role: String,
    val joinedAt: String
)
