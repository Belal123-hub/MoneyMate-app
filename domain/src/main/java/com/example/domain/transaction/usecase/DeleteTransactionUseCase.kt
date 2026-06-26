package com.example.domain.transaction.usecase

import com.example.domain.transaction.TransactionRepository

class DeleteTransactionUseCase(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(transactionId: Int): Result<Unit> =
        repository.deleteTransaction(transactionId)
}
