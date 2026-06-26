package com.example.domain.transaction.usecase

import com.example.domain.transaction.TransactionRepository
import com.example.domain.transaction.model.SpendingForecastData

class GetSpendingForecastUseCase(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(): Result<SpendingForecastData> {
        return repository.getSpendingForecast()
    }
}
