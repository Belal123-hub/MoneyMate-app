package com.example.data.offline.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.dao.TransactionDao
import com.example.data.offline.WalletPermissionHelper
import com.example.data.database.entity.MonthlySavingsGoalEntity
import com.example.data.database.mapper.toDomain
import com.example.data.database.mapper.toLocalEntity
import com.example.data.offline.MonthlySavingsLocalRecalculator
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.data.offline.WalletBalanceRecalculator
import com.example.domain.savingsGoal.SavingsGoalRepository
import com.example.domain.transaction.TransactionRepository
import com.example.domain.transaction.model.AverageSpendingData
import com.example.domain.transaction.model.CategorySummaryData
import com.example.domain.transaction.model.ComparisonCategoryData
import com.example.domain.transaction.model.CreateTransaction
import com.example.domain.transaction.model.DailyData
import com.example.domain.transaction.model.PeriodFilter
import com.example.domain.transaction.model.SavingsForecastData
import com.example.domain.transaction.model.SavingsSuggestionData
import com.example.domain.transaction.model.SavingsTrendsData
import com.example.domain.transaction.model.SpendingForecastData
import com.example.domain.transaction.model.SpendingTrendData
import com.example.domain.transaction.model.TopCategoryData
import com.example.domain.transaction.model.TransactionEntity
import com.example.domain.transaction.model.TransferEntity
import com.example.domain.transaction.model.TransferPreview
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

class OfflineTransactionRepositoryImpl(
    private val remoteRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val syncOrchestrator: OfflineSyncOrchestrator,
    private val pendingOperationDao: PendingOperationDao,
    private val monthlySavingsLocalRecalculator: MonthlySavingsLocalRecalculator,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val monthlySavingsGoalDao: MonthlySavingsGoalDao,
    private val walletBalanceRecalculator: WalletBalanceRecalculator,
    private val walletPermissionHelper: WalletPermissionHelper
) : TransactionRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun ensureCanMutateWallet(walletId: Int): Result<Unit> {
        val wallet = walletPermissionHelper.resolveWallet(walletId)
            ?: return Result.failure(IllegalStateException("Wallet not found"))
        return if (wallet.canAddTransactions()) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException("You have view-only access to this wallet")
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun createTransaction(createTransaction: CreateTransaction): Result<TransactionEntity> {
        ensureCanMutateWallet(createTransaction.walletId).onFailure {
            return Result.failure(it)
        }
        val remote = remoteRepository.createTransaction(createTransaction)
        if (remote.isSuccess) {
            val created = remote.getOrThrow()
            transactionDao.upsertTransaction(created.toLocalEntity(System.currentTimeMillis(), true))
            walletBalanceRecalculator.recalculateWalletBalance(created.walletId)
            refreshMonthlySavingsFromServerAfterRemoteTransaction()
            monthlySavingsLocalRecalculator.recalculateAllCachedMonths()
            return remote
        }

        val localTransaction = TransactionEntity(
            id = -System.currentTimeMillis().toInt(),
            name = createTransaction.name,
            amount = createTransaction.amount.toString(),
            note = createTransaction.note,
            type = createTransaction.type,
            transactionDate = createTransaction.transactionDate,
            walletId = createTransaction.walletId,
            categoryId = createTransaction.categoryId,
            userId = 0,
            createdAt = LocalDateTime.now().toString(),
            tags = createTransaction.tags.map { it.toString() },
            receiptUrl = null
        )
        transactionDao.upsertTransaction(localTransaction.toLocalEntity(System.currentTimeMillis(), false))
        monthlySavingsLocalRecalculator.recalculateAllCachedMonths()
        walletBalanceRecalculator.recalculateWalletBalance(localTransaction.walletId)
        val payload = buildCreateTransactionPayload(createTransaction)

        // Debug: Check pending operations count before enqueue
        val beforeCount = pendingOperationDao.getAll().size
        println("📤 OFFLINE_SYNC: Before enqueue - pending operations count: $beforeCount")

        syncOrchestrator.enqueueOperation("transaction", localTransaction.id, "create", payload)

        // Debug: Check pending operations count after enqueue
        val afterCount = pendingOperationDao.getAll().size
        println("📤 OFFLINE_SYNC: After enqueue - pending operations count: $afterCount")
        println("📤 OFFLINE_SYNC: Enqueued transaction for sync (id=${localTransaction.id}, name=${localTransaction.name})")

        return Result.success(localTransaction)
    }

    override suspend fun createTransfer(
        sourceWalletId: Int,
        destinationWalletId: Int,
        amount: Any,
        note: String?
    ): Result<TransferEntity> {
        ensureCanMutateWallet(sourceWalletId).onFailure { return Result.failure(it) }
        ensureCanMutateWallet(destinationWalletId).onFailure { return Result.failure(it) }
        return remoteRepository.createTransfer(sourceWalletId, destinationWalletId, amount, note)
    }

    override suspend fun getTransferPreview(
        sourceWalletId: Int,
        destinationWalletId: Int,
        amount: String
    ): Result<TransferPreview> = remoteRepository.getTransferPreview(sourceWalletId, destinationWalletId, amount)

    override suspend fun getTransactions(): Result<List<TransactionEntity>> {
        val local = transactionDao.getTransactions().map { it.toDomain() }
        val remote = remoteRepository.getTransactions()
        remote.onSuccess { remoteList ->
            transactionDao.upsertTransactions(
                remoteList.map { transaction ->
                    transaction.toLocalEntity(System.currentTimeMillis(), true)
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                syncOrchestrator.dedupeStaleOfflineTransactions()
                syncOrchestrator.runSync("transactions")
            }
        }
        return when {
            remote.isSuccess -> Result.success(transactionDao.getTransactions().map { it.toDomain() })
            local.isNotEmpty() -> Result.success(local)
            else -> Result.success(emptyList())
        }
    }

    override suspend fun deleteTransaction(id: Int): Result<Unit> {
        val existing = transactionDao.getTransactionByTempId(id)
        existing?.walletId?.let { walletId ->
            ensureCanMutateWallet(walletId).onFailure { return Result.failure(it) }
        }

        // Never synced to server — local-only row.
        if (id <= 0) {
            removeTransactionLocally(id, existing?.walletId)
            return Result.success(Unit)
        }

        val remote = remoteRepository.deleteTransaction(id)
        if (remote.isSuccess) {
            removeTransactionLocally(id, existing?.walletId)
            refreshMonthlySavingsFromServerAfterRemoteTransaction()
            return Result.success(Unit)
        }

        val error = remote.exceptionOrNull()
        if (isNetworkError(error)) {
            removeTransactionLocally(id, existing?.walletId)
            syncOrchestrator.enqueueOperation("transaction", id, "delete")
            return Result.success(Unit)
        }

        return Result.failure(error ?: Exception("Could not delete transaction"))
    }

    override suspend fun getTransactionsByWalletId(walletId: Int): Result<List<TransactionEntity>> {
        val local = transactionDao.getTransactionsByWalletId(walletId).map { it.toDomain() }
        val remote = remoteRepository.getTransactionsByWalletId(walletId)
        remote.onSuccess { remoteList ->
            transactionDao.upsertTransactions(
                remoteList.map { transaction ->
                    transaction.toLocalEntity(System.currentTimeMillis(), true)
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                syncOrchestrator.dedupeStaleOfflineTransactions()
            }
        }
        return when {
            remote.isSuccess -> Result.success(
                transactionDao.getTransactionsByWalletId(walletId).map { it.toDomain() }
            )
            local.isNotEmpty() -> Result.success(local)
            else -> Result.success(emptyList())
        }
    }

    private suspend fun removeTransactionLocally(transactionId: Int, walletId: Int?) {
        transactionDao.deleteTransactionById(transactionId)
        walletId?.let { walletBalanceRecalculator.recalculateWalletBalance(it) }
        monthlySavingsLocalRecalculator.recalculateAllCachedMonths()
    }

    private fun isNetworkError(error: Throwable?): Boolean {
        if (error == null) return false
        var current: Throwable? = error
        while (current != null) {
            if (current is java.io.IOException) return true
            current = current.cause
        }
        val message = error.message.orEmpty()
        return message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("Failed to connect", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)
    }

    override suspend fun getSpendingTrends(months: Int): Result<List<SpendingTrendData>> = remoteRepository.getSpendingTrends(months)
    override suspend fun getCategorySummary(startDate: String, endDate: String): Result<CategorySummaryData> = remoteRepository.getCategorySummary(startDate, endDate)
    override suspend fun getMonthlyComparison(month: String): Result<List<ComparisonCategoryData>> = remoteRepository.getMonthlyComparison(month)
    override suspend fun getDailySpendingData(startDate: String, endDate: String): Result<List<DailyData>> = remoteRepository.getDailySpendingData(startDate, endDate)
    override suspend fun getTransactionsByDateRange(startDate: String, endDate: String): Result<List<TransactionEntity>> = remoteRepository.getTransactionsByDateRange(startDate, endDate)
    override suspend fun getTopCategoriesCurrentMonth(): Result<List<TopCategoryData>> = remoteRepository.getTopCategoriesCurrentMonth()
    override suspend fun getAverageSpending(period: PeriodFilter): Result<List<AverageSpendingData>> = remoteRepository.getAverageSpending(period)
    override suspend fun getSavingsTrends(months: Int): Result<SavingsTrendsData> = remoteRepository.getSavingsTrends(months)
    override suspend fun getSavingsForecast(monthsAhead: Int): Result<SavingsForecastData> = remoteRepository.getSavingsForecast(monthsAhead)
    override suspend fun getSpendingForecast(): Result<SpendingForecastData> = remoteRepository.getSpendingForecast()
    override suspend fun getSavingsSuggestions(): Result<SavingsSuggestionData> = remoteRepository.getSavingsSuggestions()

    /**
     * After a successful **REST** transaction mutation, align Room with `GET /api/savings_goals/current`.
     * REST may use incremental savings updates while sync uses full recompute; the GET is authoritative here.
     *
     * @return true if Room monthly goal was updated from the API
     */
    private suspend fun refreshMonthlySavingsFromServerAfterRemoteTransaction(): Boolean {
        return try {
            val result = savingsGoalRepository.getCurrentSavingsGoal()
            if (result.isFailure) return false
            val sg = result.getOrNull() ?: return false
            val txNet = runCatching {
                transactionDao.sumSavingsWalletIncomeMinusExpenseForMonth(sg.year, sg.month)
            }.getOrElse { 0.0 }
            val currentSavedForRoom =
                if (sg.currentSaved <= 1e-9 && txNet > 1e-9) {
                    println(
                        "SAVINGS_REST: API current_saved=${sg.currentSaved} but local savings-wallet txNet=$txNet — using txNet"
                    )
                    txNet
                } else {
                    sg.currentSaved
                }
            val now = System.currentTimeMillis()
            monthlySavingsGoalDao.upsertMonthlySavingsGoal(
                MonthlySavingsGoalEntity(
                    id = sg.id,
                    month = sg.month,
                    year = sg.year,
                    targetAmount = sg.targetAmount,
                    currentSaved = currentSavedForRoom,
                    savingsTxNetAnchor = txNet,
                    updatedAt = now,
                    isSynced = true
                )
            )
            println(
                "SAVINGS_REST: Cached GET savings_goals/current after remote tx " +
                    "(id=${sg.id}, month=${sg.month}, year=${sg.year}, current_saved=$currentSavedForRoom, anchorTxNet=$txNet)"
            )
            true
        } catch (e: Exception) {
            println("SAVINGS_REST: Failed to refresh savings after remote tx: ${e.message}")
            false
        }
    }

    private fun buildCreateTransactionPayload(createTransaction: CreateTransaction): String {
        val amountString = when (val amount = createTransaction.amount) {
            is Number -> amount.toString()
            is String -> amount
            else -> amount.toString()
        }
        return json.encodeToString(
            mapOf(
                "name" to createTransaction.name,
                "amount" to amountString,
                "type" to createTransaction.type,
                "transactionDate" to createTransaction.transactionDate,
                "walletId" to createTransaction.walletId.toString(),
                "categoryId" to createTransaction.categoryId.toString(),
                "note" to (createTransaction.note ?: ""),
                "tags" to createTransaction.tags.map { it.toString() }.joinToString(",")
            )
        )
    }
}
