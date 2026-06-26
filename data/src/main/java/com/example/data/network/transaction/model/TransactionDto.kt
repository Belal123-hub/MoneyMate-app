package com.example.data.network.transaction.model

import com.example.domain.transaction.model.TransactionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("amount") val amount: Double,
    @SerialName("note") val note: String? = null,
    @SerialName("type") val type: String,
    @SerialName("transaction_date") val transactionDate: String,
    @SerialName("wallet_id") val walletId: Int,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("user_id") val userId: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("receipt_url") val receiptUrl: String? = null
) {
    fun toEntity(): TransactionEntity {
        return TransactionEntity(
            id = id,
            name = name,
            amount = amount.toString(),
            note = note,
            type = type,
            transactionDate = transactionDate,
            walletId = walletId,
            categoryId = categoryId,
            userId = userId,
            createdAt = createdAt,
            tags = tags,
            receiptUrl = receiptUrl
        )
    }
}
