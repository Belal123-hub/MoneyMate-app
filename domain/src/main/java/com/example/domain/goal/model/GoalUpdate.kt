package com.example.domain.goal.model

data class GoalUpdate(
    val title: String? = null,
    val goalAmount: Double? = null,
    val currentAmount: Double? = null,
    val currency: String? = null,
    val description: String? = null,
    val deadline: String? = null
)