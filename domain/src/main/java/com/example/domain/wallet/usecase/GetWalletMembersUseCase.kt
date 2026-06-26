package com.example.domain.wallet.usecase

import com.example.domain.wallet.WalletRepository

class GetWalletMembersUseCase(private val repository: WalletRepository) {
    suspend operator fun invoke(walletId: Int) = repository.getWalletMembers(walletId)
}
