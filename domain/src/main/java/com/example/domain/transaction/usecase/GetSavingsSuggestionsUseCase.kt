package com.example.domain.transaction.usecase

import com.example.domain.transaction.TransactionRepository
import com.example.domain.transaction.model.SavingsSuggestionData

class GetSavingsSuggestionsUseCase(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(): Result<SavingsSuggestionData> {
        return repository.getSavingsSuggestions()
    }
}
