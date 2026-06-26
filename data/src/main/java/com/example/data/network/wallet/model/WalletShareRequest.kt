package com.example.data.network.wallet.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletShareRequest(
    @SerialName("user_email")
    val email: String,
    val role: String
)