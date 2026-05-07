package com.example.data.offline

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.database.dao.CategoryDao
import com.example.data.database.dao.GoalDao
import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.dao.SyncMetadataDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import com.example.data.database.entity.PendingOperation
import com.example.data.database.entity.SyncMetadata
import com.example.data.database.mapper.toLocalEntity
import com.example.data.network.goal.model.toDomain
import com.example.data.network.sync.OfflineSyncApi
import com.example.data.network.sync.model.PendingOperationPayload
import com.example.data.network.sync.model.SyncPushRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineSyncOrchestrator(
    private val syncApi: OfflineSyncApi,
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val goalDao: GoalDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val pendingOperationDao: PendingOperationDao
) {

    private val syncMutex = Mutex()
    private var isSyncing = false

    suspend fun enqueueOperation(
        resourceType: String,
        resourceId: Int,
        operationType: String,
        payload: String? = null
    ) {
        println("📥 OFFLINE_SYNC: enqueueOperation(resourceType=$resourceType, operationType=$operationType, resourceId=$resourceId)")
        pendingOperationDao.enqueue(
            PendingOperation(
                resourceType = resourceType,
                resourceId = resourceId,
                operationType = operationType,
                payload = payload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun runSync(resourceName: String = "default"): Result<Unit> {
        return syncMutex.withLock {
            if (isSyncing) {
                println("⚠️ OFFLINE_SYNC: Sync already in progress, skipping")
                return@withLock Result.success(Unit)
            }

            isSyncing = true
            println("🔵 OFFLINE_SYNC: Starting sync for $resourceName")

            try {
                pushPendingOperations()
                pullLatestChanges(resourceName)
                syncMetadataDao.upsert(
                    SyncMetadata(
                        resourceName = resourceName,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                )
                println("✅ OFFLINE_SYNC: Sync completed successfully for $resourceName")
                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ OFFLINE_SYNC: Sync failed for $resourceName: ${e.message}")
                Result.failure(e)
            } finally {
                isSyncing = false
                println("🔵 OFFLINE_SYNC: Sync lock released for $resourceName")
            }
        }
    }

    private suspend fun pushPendingOperations() {
        val operations = pendingOperationDao.getAll()
        if (operations.isEmpty()) {
            println("📭 OFFLINE_SYNC: No pending operations to push")
            return
        }
        println("🚀 OFFLINE_SYNC: Pushing ${operations.size} operations")

        val payload = SyncPushRequest(
            operations = operations.map {
                PendingOperationPayload(
                    resourceType = it.resourceType,
                    operationType = it.operationType,
                    resourceId = it.resourceId,
                    payload = it.payload
                )
            }
        )

        try {
            val response = syncApi.pushPendingOperations(payload)
            val now = System.currentTimeMillis()

            // Log response results
            println("📦 OFFLINE_SYNC: Push response received with ${response.results.size} results")
            response.results.forEach { result ->
                println("   Result: ${result.resourceType} - ${result.operationType} - ${result.resourceId} - ${result.status}")
            }

            // Mark only successful operations as synced
            val successfulIds = response.results.filter { it.status == "success" }.map { it.resourceId }

            val transactionIds = operations.filter {
                it.resourceType == "transaction" && successfulIds.contains(it.resourceId)
            }.map { it.resourceId }
            val walletIds = operations.filter {
                it.resourceType == "wallet" && successfulIds.contains(it.resourceId)
            }.map { it.resourceId }
            val goalIds = operations.filter {
                it.resourceType == "goal" && successfulIds.contains(it.resourceId)
            }.map { it.resourceId }

            if (transactionIds.isNotEmpty()) {
                transactionDao.markTransactionsSynced(transactionIds, now)
                println("✅ OFFLINE_SYNC: Marked ${transactionIds.size} transactions as synced")
            }
            if (walletIds.isNotEmpty()) {
                walletDao.markWalletsSynced(walletIds, now)
                println("✅ OFFLINE_SYNC: Marked ${walletIds.size} wallets as synced")
            }
            if (goalIds.isNotEmpty()) {
                goalDao.markGoalsSynced(goalIds, now)
                println("✅ OFFLINE_SYNC: Marked ${goalIds.size} goals as synced")
            }

            // Remove only successfully synced operations
            val operationsToRemove = operations.filter { successfulIds.contains(it.resourceId) }
            operationsToRemove.forEach { pendingOperationDao.removeById(it.id) }

            // Increment retry count for failed operations
            val failedOperations = operations.filter { !successfulIds.contains(it.resourceId) }
            if (failedOperations.isNotEmpty()) {
                failedOperations.forEach { pendingOperationDao.incrementRetryCount(it.id) }
                println("⚠️ OFFLINE_SYNC: ${failedOperations.size} operations failed, will retry later")
            }

            println("✅ OFFLINE_SYNC: Push completed - ${operationsToRemove.size} cleared, ${failedOperations.size} kept for retry")

        } catch (e: Exception) {
            operations.forEach { pendingOperationDao.incrementRetryCount(it.id) }
            println("❌ OFFLINE_SYNC: Push failed: ${e.message}, incremented retry for ${operations.size} operations")
            throw IllegalStateException("Push failed: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pullLatestChanges(resourceName: String) {
        val lastSyncMillis = syncMetadataDao.getByResourceName(resourceName)?.lastSyncTimestamp ?: 0L

        // Convert milliseconds to seconds for backend
        val lastSyncSeconds = lastSyncMillis / 1000L
        println("📥 OFFLINE_SYNC: Pulling changes since timestamp: $lastSyncSeconds")

        try {
            val body = syncApi.pullChanges(lastSyncSeconds)
            val now = System.currentTimeMillis()

            val transactionsCount = body.transactions.size
            val walletsCount = body.wallets.size
            val goalsCount = body.goals.size
            val categoriesCount = body.categories.size

            println("📥 OFFLINE_SYNC: Pull response - ${transactionsCount} transactions, ${walletsCount} wallets, ${goalsCount} goals, ${categoriesCount} categories")

            if (transactionsCount > 0) {
                transactionDao.upsertTransactions(body.transactions.map { it.toEntity().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${transactionsCount} transactions")
            }
            if (walletsCount > 0) {
                walletDao.upsertWallets(body.wallets.map { it.toDomain().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${walletsCount} wallets")
            }
            if (goalsCount > 0) {
                goalDao.upsertGoals(body.goals.map { it.toDomain().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${goalsCount} goals")
            }
            if (categoriesCount > 0) {
                categoryDao.upsertCategories(body.categories.map { it.toDomain().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${categoriesCount} categories")
            }

            println("📥 OFFLINE_SYNC: Pull completed successfully")
        } catch (e: Exception) {
            println("❌ OFFLINE_SYNC: Pull failed: ${e.message}")
            throw IllegalStateException("Pull failed: ${e.message}")
        }
    }
}