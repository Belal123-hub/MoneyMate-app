package com.example.data.network.sync.model

import com.example.data.network.category.model.CategoryResponse
import com.example.data.network.goal.model.GoalResponse
import com.example.data.network.savingsGoal.model.SavingsGoalResponse
import com.example.data.network.tag.model.TagDto
import com.example.data.network.transaction.model.TransactionDto
import com.example.data.network.wallet.model.WalletMemberResponse
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
    val goals: List<GoalResponse> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    @SerialName("wallet_members")
    val walletMembers: List<WalletMemberResponse> = emptyList()
)

@Serializable
data class SyncPushResponse(
    @SerialName("server_timestamp")
    val serverTimestamp: Long,
    val results: List<SyncOperationResult>,
    val transactions: List<TransactionDto>,
    val wallets: List<WalletResponse>,
    val goals: List<GoalResponse>,
    val categories: List<CategoryResponse>,
    val tags: List<TagDto> = emptyList(),
    /**
     * When present, authoritative monthly savings goal after push (e.g. server recalculated from transactions).
     * JSON key must match backend contract (default `current_savings_goal`).
     */
    @SerialName("current_savings_goal")
    val currentSavingsGoal: SavingsGoalResponse? = null
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
    val message: String? = null,
    /**
     * Optional server-assigned id for creates when [resourceId] still holds the client temp id,
     * or when the API uses a separate field for the persisted id.
     */
    @SerialName("server_resource_id")
    val serverResourceId: Int? = null
)
