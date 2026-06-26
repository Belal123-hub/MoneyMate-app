package com.example.domain.wallet.usecase

import com.example.domain.wallet.WalletRepository

class RemoveMemberUseCase(private val repository: WalletRepository) {
    suspend operator fun invoke(walletId: Int, userId: Int) = repository.removeMember(walletId, userId)
}
