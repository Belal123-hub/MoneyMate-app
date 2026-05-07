package com.example.data.network.sync.model

import com.example.data.network.category.model.CategoryResponse
import com.example.data.network.goal.model.GoalResponse
import com.example.data.network.transaction.model.TransactionDto
import com.example.data.network.wallet.model.WalletResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PendingOperationPayload(
    val resourceType: String,
    val operationType: String,
    val resourceId: Int,
    val payload: String? = null
)

@Serializable
data class SyncPushRequest(
    val operations: List<PendingOperationPayload>
)

@Serializable
data class SyncPullResponse(
    val transactions: List<TransactionDto> = emptyList(),
    val wallets: List<WalletResponse> = emptyList(),
    val categories: List<CategoryResponse> = emptyList(),
    val goals: List<GoalResponse> = emptyList()
)

@Serializable
data class SyncPushResponse(
    @SerialName("server_timestamp")
    val serverTimestamp: Long,
    val results: List<SyncOperationResult>,
    val transactions: List<TransactionDto>,
    val wallets: List<WalletResponse>,
    val goals: List<GoalResponse>,
    val categories: List<CategoryResponse>
)
@Serializable
data class SyncOperationResult(
    @SerialName("resource_type")
    val resourceType: String,
    @SerialName("operation_type")
    val operationType: String,
    @SerialName("resource_id")
    val resourceId: Int,
    val status: String,
    val message: String? = null
)
