package com.example.data.offline.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.mapper.toDomain
import com.example.data.database.mapper.toLocalEntity
import com.example.data.offline.OfflineSyncOrchestrator
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
    private val pendingOperationDao: PendingOperationDao
) : TransactionRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun createTransaction(createTransaction: CreateTransaction): Result<TransactionEntity> {
        val remote = remoteRepository.createTransaction(createTransaction)
        if (remote.isSuccess) {
            val created = remote.getOrThrow()
            transactionDao.upsertTransaction(created.toLocalEntity(System.currentTimeMillis(), true))
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
    ): Result<TransferEntity> = remoteRepository.createTransfer(sourceWalletId, destinationWalletId, amount, note)

    override suspend fun getTransferPreview(
        sourceWalletId: Int,
        destinationWalletId: Int,
        amount: String
    ): Result<TransferPreview> = remoteRepository.getTransferPreview(sourceWalletId, destinationWalletId, amount)

    override suspend fun getTransactions(): Result<List<TransactionEntity>> {
        val local = transactionDao.getTransactions().map { it.toDomain() }
        val remote = remoteRepository.getTransactions()
        remote.onSuccess {
            transactionDao.upsertTransactions(it.map { transaction -> transaction.toLocalEntity(System.currentTimeMillis(), true) })
            syncOrchestrator.runSync("transactions")
        }
        return when {
            local.isNotEmpty() -> Result.success(local)
            remote.isSuccess -> remote
            else -> Result.success(emptyList())
        }
    }

    override suspend fun deleteTransaction(id: Int): Result<Unit> {
        val remote = remoteRepository.deleteTransaction(id)
        if (remote.isSuccess) {
            transactionDao.deleteTransactionById(id)
            return remote
        }
        transactionDao.deleteTransactionById(id)
        syncOrchestrator.enqueueOperation("transaction", id, "delete")
        return Result.success(Unit)
    }

    override suspend fun getTransactionsByWalletId(walletId: Int): Result<List<TransactionEntity>> {
        val local = transactionDao.getTransactionsByWalletId(walletId).map { it.toDomain() }
        val remote = remoteRepository.getTransactionsByWalletId(walletId)
        return when {
            local.isNotEmpty() -> Result.success(local)
            remote.isSuccess -> remote
            else -> Result.success(emptyList())
        }
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
