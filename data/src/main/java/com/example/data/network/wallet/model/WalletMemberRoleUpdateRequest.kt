package com.example.data.network.wallet.model

import kotlinx.serialization.Serializable

@Serializable
data class WalletMemberRoleUpdateRequest(
    val role: String
)