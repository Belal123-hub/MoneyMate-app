// data/network/transaction/model/ChartResponses.kt
package com.example.data.network.transaction.model

import com.example.domain.transaction.model.AverageSpendingData
import com.example.domain.transaction.model.TopCategoryData
import com.example.data.network.common.serializer.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// KEEP EXISTING MODELS
@Serializable
data class SpendingTrendsResponse(
    val monthly_summary: List<MonthlySummary>,
    val summary: Summary,
    val analysis_period: AnalysisPeriod
)

@Serializable
data class MonthlySummary(
    val year: Int,
    val month: Int,
    val total_spent: Double,
    val total_income: Double,
    val month_name: String,
    val display_name: String
)

@Serializable
data class Summary(
    val total_spent: Double,
    val total_income: Double,
    val net_flow: Double,
    val average_monthly_spent: Double,
    val months_analyzed: Int
)

@Serializable
data class AnalysisPeriod(
    val start_date: String,
    val end_date: String,
    val months_analyzed: Int
)

// ADD NEW RESPONSE MODELS
@Serializable
data class CategorySummaryResponse(
    val expenses: List<CategoryResponse>,
    val incomes: List<CategoryResponse>,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val total_expenses: Double,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val total_incomes: Double,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val net_flow: Double
)

@Serializable
data class CategoryResponse(
    val category_id: Int,
    val category_name: String,
    val category_type: String,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val total_amount: Double,
    val transaction_count: Int
)

@Serializable
data class MonthlyComparisonResponse(
    val category_id: Int,
    val category_name: String,
    val current_month_amount: Double,
    val previous_month_amount: Double,
    val difference: Double,
    val percentage_change: Double
)

@Serializable
data class TopCategoryResponse(
    val category_id: Int,
    val category_name: String,
    val total_amount: Double
)

@Serializable
data class AverageSpendingResponse(
    val category_id: Int,
    val category_name: String,
    val total_period_spent: Double,
    val transactions: Int,
    val period_type: String
)

// SAVINGS TRENDS RESPONSE MODELS
@Serializable
data class SavingsTrendsResponse(
    val monthly_trends: List<SavingsMonthlyTrendDto>,
    val months_analyzed: Int = 6,  // Make optional with default value
    val analysis_period: AnalysisPeriodDto? = null,  // Make nullable
    val summary: SavingsTrendsSummaryDto? = null  // Add optional summary
)

@Serializable
data class SavingsTrendsSummaryDto(
    val total_saved: Double = 0.0,
    val total_target: Double = 0.0,
    val average_achievement_rate: Double = 0.0,
    val months_tracked: Int = 0
)

@Serializable
data class SavingsMonthlyTrendDto(
    val year: Int,
    val month: Int,
    val display_name: String,
    val saved_amount: Double,
    val target_amount: Double,
    val achievement_rate: Double
)

@Serializable
data class AnalysisPeriodDto(
    val start_date: String,
    val end_date: String
)

// FORECAST RESPONSE MODELS
@Serializable
data class SavingsForecastResponse(
    val current_savings: Double,
    val average_monthly_saving: Double,
    val months_ahead: Int,
    val projections: List<SavingsProjectionDto>,
    val forecast_date: String
)

@Serializable
data class SavingsProjectionDto(
    val month: String,
    val projected_amount: Double,
    val cumulative_total: Double
)

@Serializable
data class SpendingForecastResponse(
    val spent_so_far: Double,
    val daily_average_spending: Double,
    val forecast_end_of_month: Double,
    val days_elapsed: Int,
    val days_in_month: Int,
    val confidence: String
)

@Serializable
data class SavingsSuggestionsResponse(
    val suggestions: List<SavingsSuggestionDto>,
    val generated_at: String
)

@Serializable
data class SavingsSuggestionDto(
    val type: String,
    val title: String,
    val message: String,
    val amount: Double?
)