package com.example.data.network.savingsGoal

import com.example.data.network.savingsGoal.model.SavingsGoalResponse
import com.example.data.network.savingsGoal.model.SavingsGoalUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Current monthly savings goal.
 *
 * **Backend contract (2026):** `POST /api/transactions` may update savings incrementally (e.g. income-only).
 * **Sync push** recomputes savings in full from transactions. The app refreshes this resource after remote
 * transaction mutations and applies `current_savings_goal` from sync push when present.
 */
interface SavingsGoalApiService {
    
    @GET("api/savings_goals/current")
    suspend fun getCurrentSavingsGoal(): Response<SavingsGoalResponse>
    
    @PUT("api/savings_goals/current")
    suspend fun updateCurrentSavingsGoal(@Body request: SavingsGoalUpdateRequest): Response<SavingsGoalResponse>
}