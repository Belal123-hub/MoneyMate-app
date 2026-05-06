package com.example.domain.goal

import com.example.domain.goal.model.Goal

interface GoalRepository {
    suspend fun getGoal(goalId: Int): Result<Goal>
    suspend fun getGoals(): Result<List<Goal>>
    suspend fun createGoal(
        title: String,
        goalAmount: Double,
        currency: String,
        description: String?,
        deadline: java.time.LocalDate?,
        imagePath: String?
    ): Result<Goal>
    suspend fun updateGoal(
        goalId: Int,
        title: String? = null,
        description: String? = null,
        deadline: java.time.LocalDate? = null,
        goalAmount: Double? = null,
        currentAmount: Double? = null
    ): Result<Goal>
    suspend fun deleteGoal(goalId: Int): Result<Boolean>
}