package com.example.data.network.goal

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.network.goal.model.GoalCreateRequest
import com.example.data.network.goal.model.GoalUpdateRequest
import com.example.data.network.goal.model.toDomain
import com.example.domain.goal.GoalRepository
import com.example.domain.goal.model.Goal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class GoalRepositoryImpl(
    private val apiService: GoalApiService
) : GoalRepository {

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getGoals(): Result<List<Goal>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getGoals()
                if (response.isSuccessful) {
                    response.body()?.let { goalResponses ->
                        Result.success(goalResponses.map { it.toDomain() })
                    } ?: Result.failure(Exception("Empty response body"))
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getGoal(goalId: Int): Result<Goal> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getGoal(goalId)
                if (response.isSuccessful) {
                    response.body()?.let {
                        Result.success(it.toDomain())
                    } ?: Result.failure(Exception("Empty response body"))
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun createGoal(
        title: String,
        goalAmount: Double,
        currency: String,
        description: String?,
        deadline: LocalDate?,
        imagePath: String?
    ): Result<Goal> = withContext(Dispatchers.IO) {
        try {
            val request = GoalCreateRequest(
                title = title,
                goalAmount = goalAmount,
                currency = currency,
                description = description,
                deadline = deadline?.toString()
            )

            val response = apiService.createGoal(request)

            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it.toDomain())
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // FIXED: Single updateGoal that matches the interface exactly
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun updateGoal(
        goalId: Int,
        title: String?,
        description: String?,
        deadline: LocalDate?,
        goalAmount: Double?,
        currentAmount: Double?
    ): Result<Goal> = withContext(Dispatchers.IO) {
        try {
            val request = GoalUpdateRequest(
                title = title,
                description = description,
                deadline = deadline?.toString(),
                goal_amount = goalAmount?.toString(),
                current_amount = currentAmount
            )

            val response = apiService.updateGoal(goalId, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it.toDomain())
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteGoal(goalId: Int): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteGoal(goalId)
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}