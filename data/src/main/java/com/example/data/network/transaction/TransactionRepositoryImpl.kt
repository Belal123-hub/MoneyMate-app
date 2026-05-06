// data/network/transaction/TransactionRepositoryImpl.kt
package com.example.data.network.transaction

import com.example.data.network.transaction.model.*
import com.example.domain.transaction.TransactionRepository
import com.example.domain.transaction.model.*
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class TransactionRepositoryImpl(
    private val apiService: TransactionApi
) : TransactionRepository {

    override suspend fun createTransaction(
        createTransaction: CreateTransaction
    ): Result<TransactionEntity> {
        return try {
            // Convert amount to string
            val amountString = when (val amount = createTransaction.amount) {
                is Double -> amount.toString()
                is Float -> amount.toString()
                is Int -> amount.toString()
                is Long -> amount.toString()
                is String -> amount
                else -> throw IllegalArgumentException("Unsupported amount type: ${amount::class.simpleName}")
            }

            // Create JSON request object
            val request = TransactionCreateRequest(
                name = createTransaction.name,
                amount = amountString,
                type = createTransaction.type,
                transactionDate = createTransaction.transactionDate,
                walletId = createTransaction.walletId,
                categoryId = createTransaction.categoryId,
                note = createTransaction.note,
                tags = createTransaction.tags.map { it.toString() }
            )

            val response = apiService.createTransaction(request)
            handleTransactionResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createTransfer(
        sourceWalletId: Int,
        destinationWalletId: Int,
        amount: Any,
        note: String?
    ): Result<TransferEntity> {
        return try {
            val request = TransferCreateRequest.create(
                sourceWalletId = sourceWalletId,
                destinationWalletId = destinationWalletId,
                amount = amount,
                note = note
            )

            val response = apiService.createTransfer(request)
            handleTransferResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransferPreview(
        sourceWalletId: Int,
        destinationWalletId: Int,
        amount: String
    ): Result<TransferPreview> {
        return try {
            val response = apiService.getTransferPreview(
                sourceWalletId = sourceWalletId,
                destinationWalletId = destinationWalletId,
                amount = amount
            )
            if (response.isSuccessful) {
                val previewDto = response.body()
                if (previewDto != null) {
                    Result.success(previewDto.toDomain())
                } else {
                    Result.failure(Exception("Failed to get transfer preview"))
                }
            } else {
                Result.failure(Exception("API error: ${response.code()} - ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransactions(): Result<List<TransactionEntity>> {
        return try {
            val response = apiService.getTransactions()
            if (response.isSuccessful) {
                val transactionDtos = response.body()
                if (transactionDtos != null) {
                    Result.success(transactionDtos.map { it.toEntity() })
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransactionsByWalletId(walletId: Int): Result<List<TransactionEntity>> {
        return try {
            val response = apiService.getTransactionsByWalletId(walletId)
            if (response.isSuccessful) {
                val transactionDtos = response.body()
                if (transactionDtos != null) {
                    Result.success(transactionDtos.map { it.toEntity() })
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteTransaction(id: Int): Result<Unit> {
        return try {
            val response = apiService.deleteTransaction(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSpendingTrends(months: Int): Result<List<SpendingTrendData>> {
        return try {
            val response = apiService.getSpendingTrends(months)
            if (response.isSuccessful) {
                val spendingTrendsResponse = response.body()
                if (spendingTrendsResponse != null) {
                    Result.success(spendingTrendsResponse.monthly_summary.map { it.toDomain() })
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCategorySummary(
        startDate: String,
        endDate: String
    ): Result<CategorySummaryData> {
        return try {
            println("📊 DEBUG: Getting category summary from $startDate to $endDate")
            val response = apiService.getCategorySummary(startDate, endDate)
            if (response.isSuccessful) {
                val body = response.body()
                println("📊 DEBUG: Category summary response body: $body")
                if (body != null) {
                    println("✅ DEBUG: Category summary - ${body.expenses.size} expenses, ${body.incomes.size} incomes, total expenses: ${body.total_expenses}")
                    if (body.expenses.isNotEmpty()) {
                        println("  First expense: ${body.expenses.first()}")
                    }
                    Result.success(body.toDomain())
                } else {
                    println("⚠️ DEBUG: Category summary returned null body")
                    Result.success(getEmptyCategorySummary())
                }
            } else {
                println("❌ DEBUG: Category summary API error ${response.code()}")
                println("  Error body: ${response.errorBody()?.string()}")
                Result.failure(Exception("Failed to fetch category summary"))
            }
        } catch (e: Exception) {
            println("❌ DEBUG: Category summary exception - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getMonthlyComparison(month: String): Result<List<ComparisonCategoryData>> {
        return try {
            val response = apiService.getMonthlyComparison(month)
            if (response.isSuccessful) {
                val body = response.body()
                Result.success(body?.map { it.toDomain() } ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch monthly comparison"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDailySpendingData(startDate: String, endDate: String): Result<List<DailyData>> {
        return try {
            // For now, return sample data
            // In real implementation, you would call an API endpoint
            val sampleData = generateSampleDailyData(startDate, endDate)
            Result.success(sampleData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransactionsByDateRange(startDate: String, endDate: String): Result<List<TransactionEntity>> {
        return try {
            val allTransactions = getTransactions().getOrElse { emptyList() }

            // Filter transactions by date range
            val filteredTransactions = allTransactions.filter { transaction ->
                isDateInRange(transaction.transactionDate, startDate, endDate)
            }

            Result.success(filteredTransactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTopCategoriesCurrentMonth(): Result<List<TopCategoryData>> {
        return try {
            val response = apiService.getTopCategoriesCurrentMonth()
            if (response.isSuccessful) {
                val body = response.body()
                Result.success(body?.map { it.toDomain() } ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch top categories"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAverageSpending(period: PeriodFilter): Result<List<AverageSpendingData>> {
        return try {
            val periodString = when (period) {
                PeriodFilter.DAY -> "day"
                PeriodFilter.MONTH -> "month"
                PeriodFilter.YEAR -> "year"
                PeriodFilter.DAYS_7 -> "day"
                PeriodFilter.DAYS_15 -> "day"
                PeriodFilter.DAYS_30 -> "month"
                PeriodFilter.DAYS_90 -> "month"
            }
            val response = apiService.getAverageSpending(periodString)
            if (response.isSuccessful) {
                val body = response.body()
                Result.success(body?.map { it.toDomain() } ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch average spending"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ADD SAVINGS TRENDS METHOD
    override suspend fun getSavingsTrends(months: Int): Result<SavingsTrendsData> {
        return try {
            val response = apiService.getSavingsTrends(months)
            if (response.isSuccessful) {
                val savingsTrendsResponse = response.body()
                if (savingsTrendsResponse != null && savingsTrendsResponse.monthly_trends.isNotEmpty()) {
                    println("✅ DEBUG: Savings trends API success - ${savingsTrendsResponse.monthly_trends.size} months")
                    Result.success(savingsTrendsResponse.toDomain())
                } else if (savingsTrendsResponse != null && savingsTrendsResponse.monthly_trends.isEmpty()) {
                    println("⚠️ DEBUG: Savings trends API returned empty data")
                    Result.failure(Exception("No savings data available"))
                } else {
                    println("❌ DEBUG: Savings trends API returned null body")
                    Result.failure(Exception("No data received from server"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                println("❌ DEBUG: Savings trends API error ${response.code()} - $errorBody")
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            println("❌ DEBUG: Savings trends exception - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // NEW FORECAST METHODS
    override suspend fun getSavingsForecast(monthsAhead: Int): Result<com.example.domain.transaction.model.SavingsForecastData> {
        return try {
            val response = apiService.getSavingsForecast(monthsAhead)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body.toDomain())
                } else {
                    Result.failure(Exception("No data received from server"))
                }
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSpendingForecast(): Result<com.example.domain.transaction.model.SpendingForecastData> {
        return try {
            val response = apiService.getSpendingForecast()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body.toDomain())
                } else {
                    Result.failure(Exception("No data received from server"))
                }
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSavingsSuggestions(): Result<com.example.domain.transaction.model.SavingsSuggestionData> {
        return try {
            val response = apiService.getSavingsSuggestions()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body.toDomain())
                } else {
                    Result.failure(Exception("No data received from server"))
                }
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isDateInRange(date: String, startDate: String, endDate: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val transactionDate = dateFormat.parse(date)
        val start = dateFormat.parse(startDate)
        val end = dateFormat.parse(endDate)

        return transactionDate in start..end
    }

    private fun generateSampleDailyData(startDate: String, endDate: String): List<DailyData> {
        // Implementation to generate daily data between startDate and endDate
        // This would be replaced with actual API call
        val dailyData = mutableListOf<DailyData>()

        // Parse dates and generate data for each day
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayLabelFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        val start = dateFormat.parse(startDate)
        val end = dateFormat.parse(endDate)

        val calendar = Calendar.getInstance().apply {
            time = start
        }

        while (calendar.time <= end) {
            val date = calendar.time
            val dateString = dateFormat.format(date)
            val dayLabel = dayLabelFormat.format(date)

            // Generate sample data
            val income = 50000.0 + (Math.random() * 30000)
            val expense = 30000.0 + (Math.random() * 20000)

            dailyData.add(
                DailyData(
                    date = dateString,
                    dayLabel = dayLabel,
                    income = income,
                    expenses = expense
                )
            )

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return dailyData
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

    private fun handleTransactionResponse(response: Response<TransactionDto>): Result<TransactionEntity> {
        return if (response.isSuccessful) {
            val transactionDto = response.body()
            if (transactionDto != null) {
                Result.success(transactionDto.toEntity())
            } else {
                Result.failure(Exception("Failed to create transaction"))
            }
        } else {
            Result.failure(Exception("API error: ${response.code()} - ${response.errorBody()?.string()}"))
        }
    }

    private fun handleTransferResponse(response: Response<TransferDto>): Result<TransferEntity> {
        return if (response.isSuccessful) {
            val transferDto = response.body()
            if (transferDto != null) {
                Result.success(transferDto.toEntity())
            } else {
                Result.failure(Exception("Failed to create transfer"))
            }
        } else {
            Result.failure(Exception("API error: ${response.code()} - ${response.errorBody()?.string()}"))
        }
    }
}