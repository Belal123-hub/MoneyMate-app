package com.example.data.offline.repository

import com.example.data.database.dao.WalletDao
import com.example.data.database.mapper.toDomain
import com.example.data.database.mapper.toLocalEntity
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.domain.wallet.WalletRepository
import com.example.domain.wallet.model.TotalBalance
import com.example.domain.wallet.model.BalanceBreakdown
import com.example.domain.wallet.model.Wallet
import com.example.domain.wallet.model.WalletBalance
import com.example.domain.wallet.model.WalletCreateRequest
import com.example.domain.wallet.model.WalletUpdateRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OfflineWalletRepositoryImpl(
    private val remoteRepository: WalletRepository,
    private val walletDao: WalletDao,
    private val syncOrchestrator: OfflineSyncOrchestrator
) : WalletRepository {

    override suspend fun getWallets(): Result<List<Wallet>> {
        val localWallets = walletDao.getWallets().map { it.toDomain() }
        val remoteResult = remoteRepository.getWallets()
        remoteResult.onSuccess { wallets ->
            walletDao.upsertWallets(wallets.map { it.toLocalEntity(System.currentTimeMillis(), true) })
            syncOrchestrator.runSync("wallets")
        }
        return when {
            localWallets.isNotEmpty() -> Result.success(localWallets)
            remoteResult.isSuccess -> remoteResult
            else -> Result.success(emptyList())
        }
    }

    override suspend fun createWallet(wallet: WalletCreateRequest): Result<Wallet> {
        val remoteResult = remoteRepository.createWallet(wallet)
        if (remoteResult.isSuccess) {
            val created = remoteResult.getOrThrow()
            walletDao.upsertWallet(created.toLocalEntity(System.currentTimeMillis(), true))
            return remoteResult
        }

        val localWallet = Wallet(
            id = -System.currentTimeMillis().toInt(),
            name = wallet.name,
            currency = wallet.currency,
            walletType = wallet.walletType,
            initialBalance = wallet.initialBalance.toString(),
            cardNumber = wallet.cardNumber,
            color = wallet.color,
            balance = wallet.initialBalance.toString(),
            userId = null,
            createdAt = null
        )
        walletDao.upsertWallet(localWallet.toLocalEntity(System.currentTimeMillis(), false))
        syncOrchestrator.enqueueOperation("wallet", localWallet.id, "create")
        return Result.success(localWallet)
    }

    override suspend fun getTotalBalance(): Result<TotalBalance> {
        val remote = remoteRepository.getTotalBalance()
        if (remote.isSuccess) return remote

        val wallets = walletDao.getWallets().map { it.toDomain() }
        val total = wallets.sumOf { it.balance?.toDoubleOrNull() ?: it.initialBalance.toDoubleOrNull() ?: 0.0 }
        val breakdown = wallets.map {
            val amount = it.balance?.toDoubleOrNull() ?: it.initialBalance.toDoubleOrNull() ?: 0.0
            BalanceBreakdown(
                walletId = it.id,
                walletName = it.name,
                walletType = it.walletType,
                originalBalance = amount,
                originalCurrency = it.currency,
                convertedBalance = amount,
                convertedCurrency = it.currency,
                exchangeRateUsed = 1.0
            )
        }
        return Result.success(
            TotalBalance(
                totalBalance = total,
                currency = wallets.firstOrNull()?.currency ?: "USD",
                breakdown = breakdown as Map<String, Double>
            )
        )
    }

    override suspend fun getWalletDetail(walletId: Int): Flow<Wallet> = flow {
        walletDao.getWalletById(walletId)?.let { emit(it.toDomain()) }
        remoteRepository.getWalletDetail(walletId).collect { wallet ->
            walletDao.upsertWallet(wallet.toLocalEntity(System.currentTimeMillis(), true))
            emit(wallet)
        }
    }

    override suspend fun deleteWallet(walletId: Int): Result<Boolean> {
        val remote = remoteRepository.deleteWallet(walletId)
        if (remote.isSuccess) {
            walletDao.deleteWalletById(walletId)
            return remote
        }
        walletDao.deleteWalletById(walletId)
        syncOrchestrator.enqueueOperation("wallet", walletId, "delete")
        return Result.success(true)
    }

    override suspend fun updateWallet(walletId: Int, walletRequest: WalletUpdateRequest): Result<Wallet> {
        val remote = remoteRepository.updateWallet(walletId, walletRequest)
        if (remote.isSuccess) {
            val updated = remote.getOrThrow()
            walletDao.upsertWallet(updated.toLocalEntity(System.currentTimeMillis(), true))
            return remote
        }

        val localWallet = walletDao.getWalletById(walletId)?.copy(
            name = walletRequest.name,
            currency = walletRequest.currency,
            walletType = walletRequest.walletType,
            initialBalance = walletRequest.initialBalance.toString(),
            cardNumber = walletRequest.cardNumber,
            color = walletRequest.color,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        if (localWallet != null) {
            walletDao.upsertWallet(localWallet)
            syncOrchestrator.enqueueOperation("wallet", walletId, "update")
            return Result.success(localWallet.toDomain())
        }
        return remote
    }

    override suspend fun getWalletBalance(walletId: Int): Result<WalletBalance> = remoteRepository.getWalletBalance(walletId)
}
