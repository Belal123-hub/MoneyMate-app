package com.example.data.network.wallet.model

import com.example.domain.wallet.model.Wallet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletResponse(
    val id: Int,
    val name: String,
    val currency: String,
    @SerialName("wallet_type") val wallet_type: String,
    @SerialName("card_number") val card_number: String? = null,
    val color: String,
    val balance: String,
    @SerialName("user_id") val user_id: Int,
    @SerialName("owner_user_id") val owner_user_id: Int? = null,
    @SerialName("is_shared") val is_shared: Boolean = false,
    @SerialName("my_role") val my_role: String? = null,
    @SerialName("member_count") val member_count: Int = 0,
    @SerialName("created_at") val created_at: String
) {
    fun toDomain(): Wallet = Wallet(
        id = id,
        name = name,
        currency = currency,
        walletType = wallet_type,
        initialBalance = balance,
        cardNumber = card_number,
        color = color,
        balance = balance,
        userId = user_id,
        ownerUserId = owner_user_id ?: user_id,
        isShared = is_shared,
        myRole = my_role,
        memberCount = member_count,
        createdAt = created_at
    )
}