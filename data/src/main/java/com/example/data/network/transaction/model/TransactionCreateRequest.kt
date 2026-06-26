package com.example.data.network.transaction.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionCreateRequest(
    @SerialName("name") val name: String,
    @SerialName("amount") val amount: String,
    @SerialName("type") val type: String,
    @SerialName("transaction_date") val transactionDate: String,
    @SerialName("wallet_id") val walletId: Int,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("note") val note: String? = null,
    @SerialName("tags") val tags: List<String> = emptyList()
)
