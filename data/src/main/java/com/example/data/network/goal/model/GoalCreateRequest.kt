package com.example.data.network.goal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoalCreateRequest(
    @SerialName("title") val title: String,
    @SerialName("goal_amount") val goalAmount: Double,
    @SerialName("currency") val currency: String,
    @SerialName("description") val description: String? = null,
    @SerialName("deadline") val deadline: String? = null
)
