package com.example.data.network.transaction
import com.example.data.network.transaction.model.AverageSpendingResponse
import com.example.data.network.transaction.model.CategorySummaryResponse
import com.example.data.network.transaction.model.MonthlyComparisonResponse
import com.example.data.network.transaction.model.SavingsForecastResponse
import com.example.data.network.transaction.model.SavingsSuggestionsResponse
import com.example.data.network.transaction.model.SavingsTrendsResponse
import com.example.data.network.transaction.model.SpendingForecastResponse
import com.example.data.network.transaction.model.SpendingTrendsResponse
import com.example.data.network.transaction.model.TopCategoryResponse
import com.example.data.network.transaction.model.TransactionCreateRequest
import com.example.data.network.transaction.model.TransactionDto
import com.example.data.network.transaction.model.TransferCreateRequest
import com.example.data.network.transaction.model.TransferDto
import com.example.data.network.transaction.model.TransferPreviewResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface TransactionApi {

    /**
     * Direct REST create. Backend note: this path may use incremental savings recording (e.g. income-only),
     * while **sync push** uses full savings recomputation. Clients should refresh
     * `GET /api/savings_goals/current` after success if they cache monthly savings locally.
     */
    @POST("api/transactions/")
    suspend fun createTransaction(@Body request: TransactionCreateRequest): Response<TransactionDto>

    @POST("api/transactions/transfer")
    suspend fun createTransfer(@Body request: TransferCreateRequest): Response<TransferDto>

    @GET("api/transactions/transfer/preview")
    suspend fun getTransferPreview(
        @Query("source_wallet_id") sourceWalletId: Int,
        @Query("destination_wallet_id") destinationWalletId: Int,
        @Query("amount") amount: String
    ): Response<TransferPreviewResponse>

    @GET("api/transactions/")
    suspend fun getTransactions(): Response<List<TransactionDto>>

    @GET("api/transactions/wallet/{wallet_id}")
    suspend fun getTransactionsByWalletId(@Path("wallet_id") walletId: Int): Response<List<TransactionDto>>

    @DELETE("api/transactions/{transaction_id}")
    suspend fun deleteTransaction(@Path("transaction_id") id: Int): Response<Unit>


    @GET("api/analytics/spending-trends")
    suspend fun getSpendingTrends(
        @Query("months") months: Int
    ): Response<SpendingTrendsResponse>

    // ADD NEW ENDPOINTS
    @GET("api/analytics/category-summary")
    suspend fun getCategorySummary(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): Response<CategorySummaryResponse>

    @GET("api/analytics/monthly-comparison")
    suspend fun getMonthlyComparison(
        @Query("month") month: String
    ): Response<List<MonthlyComparisonResponse>>

    @GET("api/analytics/top-categories/current-month")
    suspend fun getTopCategoriesCurrentMonth(): Response<List<TopCategoryResponse>>

    @GET("api/analytics/average-spending")
    suspend fun getAverageSpending(
        @Query("period") period: String // "day", "month", or "year"
    ): Response<List<AverageSpendingResponse>>

    @GET("api/analytics/savings-trends")
    suspend fun getSavingsTrends(
        @Query("months") months: Int = 6
    ): Response<SavingsTrendsResponse?>

    // NEW FORECAST ENDPOINTS
    @GET("api/analytics/forecast/savings")
    suspend fun getSavingsForecast(
        @Query("months_ahead") monthsAhead: Int = 3
    ): Response<SavingsForecastResponse?>

    @GET("api/analytics/forecast/spending")
    suspend fun getSpendingForecast(): Response<SpendingForecastResponse?>

    @GET("api/analytics/forecast/suggestions")
    suspend fun getSavingsSuggestions(): Response<SavingsSuggestionsResponse?>
}