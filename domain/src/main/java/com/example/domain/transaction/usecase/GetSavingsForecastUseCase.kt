package com.example.domain.transaction.usecase

import com.example.domain.transaction.TransactionRepository
import com.example.domain.transaction.model.SavingsForecastData

class GetSavingsForecastUseCase(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(monthsAhead: Int = 3): Result<SavingsForecastData> {
        return repository.getSavingsForecast(monthsAhead)
    }
}
