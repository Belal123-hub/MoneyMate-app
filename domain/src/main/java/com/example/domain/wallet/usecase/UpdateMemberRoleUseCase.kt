package com.example.domain.wallet.usecase

import com.example.domain.wallet.WalletRepository

class UpdateMemberRoleUseCase(private val repository: WalletRepository) {
    suspend operator fun invoke(walletId: Int, userId: Int, role: String) =
        repository.updateMemberRole(walletId, userId, role)
}
