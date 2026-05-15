package com.example.data.offline.repository

import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import com.example.data.database.mapper.toDomain
import com.example.data.database.mapper.toLocalEntity
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.data.offline.WalletBalanceRecalculator
import com.example.domain.wallet.WalletRepository
import com.example.domain.wallet.model.BalanceBreakdown
import com.example.domain.wallet.model.TotalBalance
import com.example.domain.wallet.model.Wallet
import com.example.domain.wallet.model.WalletBalance
import com.example.domain.wallet.model.WalletCreateRequest
import com.example.domain.wallet.model.WalletUpdateRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OfflineWalletRepositoryImpl(
    private val remoteRepository: WalletRepository,
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val syncOrchestrator: OfflineSyncOrchestrator,
    private val walletBalanceRecalculator: WalletBalanceRecalculator,
    private val pendingOperationDao: PendingOperationDao
) : WalletRepository {

    private fun buildWalletCreateEnqueuePayload(wallet: WalletCreateRequest): String =
        buildJsonObject {
            put("name", wallet.name)
            put("currency", wallet.currency)
            put("wallet_type", wallet.walletType)
            put("card_number", wallet.cardNumber ?: "")
            put("color", wallet.color)
            put("balance", wallet.initialBalance)
        }.toString()

    private fun buildWalletUpdateEnqueuePayload(req: WalletUpdateRequest): String =
        buildJsonObject {
            put("name", req.name)
            put("currency", req.currency)
            put("wallet_type", req.walletType)
            put("card_number", req.cardNumber ?: "")
            put("color", req.color)
            put("balance", req.initialBalance)
        }.toString()

    override suspend fun getWallets(): Result<List<Wallet>> {
        val remoteResult = remoteRepository.getWallets()
        remoteResult.onSuccess { wallets ->
            val pendingCreates = pendingOperationDao.getAll()
                .filter {
                    it.resourceType.equals("wallet", ignoreCase = true) &&
                        it.operationType.equals("create", ignoreCase = true)
                }
                .map { it.resourceId }
                .toSet()
            val serverIds = wallets.map { it.id }.toSet()
            val keepIds = serverIds + pendingCreates
            val staleIds = walletDao.getWallets().map { it.id }.filter { it !in keepIds }
            staleIds.forEach { id ->
                transactionDao.deleteTransactionsByWalletId(id)
                walletDao.deleteWalletById(id)
                println("🧹 OFFLINE_WALLET: pruned stale wallet id=$id (not on server / not pending create)")
            }
            walletDao.upsertWallets(wallets.map { it.toLocalEntity(System.currentTimeMillis(), true) })
            walletBalanceRecalculator.recalculateAllWalletBalances()
            syncOrchestrator.runSync("wallets")
        }
        return when {
            remoteResult.isSuccess -> {
                val merged = walletDao.getWallets().map { it.toDomain() }
                println("📱 OFFLINE_WALLET: getWallets after server merge → ${merged.size} row(s) from Room")
                Result.success(merged)
            }
            else -> {
                val localOnly = walletDao.getWallets().map { it.toDomain() }
                if (localOnly.isNotEmpty()) {
                    Result.success(localOnly)
                } else {
                    Result.success(emptyList())
                }
            }
        }
    }

    override suspend fun createWallet(wallet: WalletCreateRequest): Result<Wallet> {
        val remoteResult = remoteRepository.createWallet(wallet)
        if (remoteResult.isSuccess) {
            val created = remoteResult.getOrThrow()
            walletDao.upsertWallet(created.toLocalEntity(System.currentTimeMillis(), true))
            walletBalanceRecalculator.recalculateWalletBalance(created.id)
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
            balance = String.format(java.util.Locale.US, "%.2f", wallet.initialBalance),
            userId = null,
            createdAt = null
        )
        walletDao.upsertWallet(localWallet.toLocalEntity(System.currentTimeMillis(), false))
        syncOrchestrator.enqueueOperation(
            "wallet",
            localWallet.id,
            "create",
            buildWalletCreateEnqueuePayload(wallet)
        )
        println(
            "📤 OFFLINE_WALLET: enqueued create localId=${localWallet.id} isSynced=false " +
                "pendingTotal=${pendingOperationDao.getAll().size}"
        )
        return Result.success(localWallet)
    }

    override suspend fun getTotalBalance(): Result<TotalBalance> {
        val remote = remoteRepository.getTotalBalance()
        if (remote.isSuccess) return remote

        walletBalanceRecalculator.recalculateAllWalletBalances()
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
        val local = walletDao.getWalletById(walletId)?.toDomain()
        if (local != null) {
            emit(local)
        }
        try {
            remoteRepository.getWalletDetail(walletId).collect { wallet ->
                walletDao.upsertWallet(wallet.toLocalEntity(System.currentTimeMillis(), true))
                walletBalanceRecalculator.recalculateWalletBalance(wallet.id)
                emit(wallet)
            }
        } catch (e: Exception) {
            println("📱 OFFLINE_WALLET: getWalletDetail remote failed: ${e.message}")
            if (local == null) throw e
        }
    }

    override suspend fun deleteWallet(walletId: Int): Result<Boolean> {
        val remote = remoteRepository.deleteWallet(walletId)
        if (remote.isSuccess) {
            walletDao.deleteWalletById(walletId)
            return remote
        }
        if (walletId < 0) {
            pendingOperationDao.removeByResourceAndOperation("wallet", walletId, "create")
            walletDao.deleteWalletById(walletId)
            println("📤 OFFLINE_WALLET: removed pending create + local temp wallet id=$walletId")
            return Result.success(true)
        }
        walletDao.deleteWalletById(walletId)
        pendingOperationDao.removeByResourceAndOperation("wallet", walletId, "update")
        syncOrchestrator.enqueueOperation("wallet", walletId, "delete")
        println("📤 OFFLINE_WALLET: enqueued delete id=$walletId")
        return Result.success(true)
    }

    override suspend fun updateWallet(walletId: Int, walletRequest: WalletUpdateRequest): Result<Wallet> {
        val remote = remoteRepository.updateWallet(walletId, walletRequest)
        if (remote.isSuccess) {
            val updated = remote.getOrThrow()
            walletDao.upsertWallet(updated.toLocalEntity(System.currentTimeMillis(), true))
            walletBalanceRecalculator.recalculateWalletBalance(walletId)
            return remote
        }

        val existing = walletDao.getWalletById(walletId) ?: return remote
        val updatedEntity = existing.copy(
            name = walletRequest.name,
            currency = walletRequest.currency,
            walletType = walletRequest.walletType,
            initialBalance = walletRequest.initialBalance.toString(),
            cardNumber = walletRequest.cardNumber,
            color = walletRequest.color,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        walletDao.upsertWallet(updatedEntity)
        syncOrchestrator.enqueueOperation(
            "wallet",
            walletId,
            "update",
            buildWalletUpdateEnqueuePayload(walletRequest)
        )
        walletBalanceRecalculator.recalculateWalletBalance(walletId)
        println("📤 OFFLINE_WALLET: enqueued update id=$walletId")
        return Result.success(updatedEntity.toDomain())
    }

    override suspend fun getWalletBalance(walletId: Int): Result<WalletBalance> =
        remoteRepository.getWalletBalance(walletId)
}
