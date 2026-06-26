package com.example.domain.wallet.usecase

import com.example.domain.wallet.WalletRepository

class ShareWalletUseCase(private val repository: WalletRepository) {
    suspend operator fun invoke(walletId: Int, userEmail: String, role: String) =
        repository.shareWallet(walletId, userEmail, role)
}
