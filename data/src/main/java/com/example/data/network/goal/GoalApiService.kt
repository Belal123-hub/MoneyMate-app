package com.example.data.network.goal

import com.example.data.network.goal.model.GoalCreateRequest
import com.example.data.network.goal.model.GoalResponse
import com.example.data.network.goal.model.GoalUpdateRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface GoalApiService {

    @GET("api/goals/")
    suspend fun getGoals(): Response<List<GoalResponse>>

    @GET("api/goals/{goal_id}")
    suspend fun getGoal(@Path("goal_id") goalId: Int): Response<GoalResponse>

    @POST("api/goals/")
    suspend fun createGoal(@Body request: GoalCreateRequest): Response<GoalResponse>

    @PUT("api/goals/{goal_id}")
    suspend fun updateGoal(
        @Path("goal_id") goalId: Int,
        @Body request: GoalUpdateRequest
    ): Response<GoalResponse>

    @DELETE("api/goals/{goal_id}")
    suspend fun deleteGoal(@Path("goal_id") goalId: Int): Response<Unit>
}