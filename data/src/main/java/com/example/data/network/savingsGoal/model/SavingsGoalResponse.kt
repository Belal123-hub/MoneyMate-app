package com.example.data.network.savingsGoal.model

import com.example.domain.savingsGoal.model.SavingsGoal
import kotlinx.serialization.Serializable

@Serializable
data class SavingsGoalResponse(
    val id: Int,
    val month: Int,
    val year: Int,
    val target_amount: Double,
    val current_saved: Double
)

@Serializable
data class SavingsGoalUpdateRequest(
    val target_amount: Double
)

fun SavingsGoalResponse.toDomain(): SavingsGoal {
    return SavingsGoal(
        id = id,
        month = month,
        year = year,
        // Converting String from API to Double for Domain
        targetAmount = target_amount,
        currentSaved = current_saved
    )
}