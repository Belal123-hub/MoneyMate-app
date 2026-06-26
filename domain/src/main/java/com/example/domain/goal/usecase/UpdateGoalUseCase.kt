package com.example.domain.goal.usecase

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.domain.goal.GoalRepository
import com.example.domain.goal.model.Goal
import com.example.domain.goal.model.GoalUpdate

class UpdateGoalUseCase(
    private val repository: GoalRepository
) {
    // Existing method for full updates
    suspend operator fun invoke(
        goalId: Int,
        title: String? = null,
        description: String? = null,
        deadline: java.time.LocalDate? = null,
        goalAmount: Double? = null
    ): Result<Goal> {
        return repository.updateGoal(goalId, title, description, deadline, goalAmount)
    }

    // New overloaded method for partial updates using GoalUpdate
    @RequiresApi(Build.VERSION_CODES.O)
    suspend operator fun invoke(
        goalId: Int,
        request: GoalUpdate
    ): Result<Goal> {
        return repository.updateGoal(
            goalId = goalId,
            title = request.title,
            description = request.description,
            deadline = request.deadline?.let { java.time.LocalDate.parse(it) },
            goalAmount = request.goalAmount,
            currentAmount = request.currentAmount
        )
    }
}