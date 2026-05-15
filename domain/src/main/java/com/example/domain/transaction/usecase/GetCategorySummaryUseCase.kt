package com.example.domain.transaction.usecase

import com.example.domain.transaction.TransactionRepository
import com.example.domain.transaction.model.CategorySummaryData
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

class GetCategorySummaryUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend fun execute(
        startDate: String = getDefaultStartDate(),
        endDate: String = getDefaultEndDate()
    ): CategorySummaryData {
        return try {
            println("📊 DEBUG: GetCategorySummaryUseCase - Calling API with startDate=$startDate, endDate=$endDate")
            val result = transactionRepository.getCategorySummary(startDate, endDate)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                println("✅ DEBUG: GetCategorySummaryUseCase - Success! ${data.expenses.size} expenses, ${data.incomes.size} incomes")
                data
            } else {
                val error = result.exceptionOrNull()
                println("❌ DEBUG: GetCategorySummaryUseCase - API returned failure: ${error?.message}")
                error?.printStackTrace()
                getEmptyCategorySummary()
            }
        } catch (e: Exception) {
            println("❌ DEBUG: GetCategorySummaryUseCase - Exception caught: ${e.message}")
            e.printStackTrace()
            getEmptyCategorySummary()
        }
    }

    private fun getEmptyCategorySummary(): CategorySummaryData {
        return CategorySummaryData(
            expenses = emptyList(),
            incomes = emptyList(),
            totalExpenses = 0.0,
            totalIncomes = 0.0,
            netFlow = 0.0
        )
    }

    private fun getDefaultStartDate(): String {
        // Default to current month to match Transaction screen expectations
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(calendar.time)
    }

    private fun getDefaultEndDate(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(calendar.time)
    }
}