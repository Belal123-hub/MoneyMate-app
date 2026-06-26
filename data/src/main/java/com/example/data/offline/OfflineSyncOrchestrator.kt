package com.example.data.offline

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.database.dao.CategoryDao
import com.example.data.database.dao.GoalDao
import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.dao.SyncMetadataDao
import com.example.data.database.dao.TagDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import com.example.data.database.dao.WalletMemberDao
import com.example.data.database.entity.MonthlySavingsGoalEntity
import com.example.data.database.entity.PendingOperation
import com.example.data.database.entity.SyncMetadata
import com.example.data.database.entity.TransactionEntity
import com.example.data.database.entity.WalletEntity
import com.example.data.database.entity.GoalEntity
import com.example.data.database.mapper.toLocalEntity
import com.example.data.network.goal.GoalApiService
import com.example.data.network.goal.model.GoalCreateRequest
import com.example.data.network.goal.model.GoalResponse
import com.example.data.network.goal.model.GoalUpdateRequest
import com.example.data.network.goal.model.toDomain
import com.example.data.network.sync.OfflineSyncApi
import com.example.data.network.transaction.TransactionApi
import com.example.data.network.transaction.model.TransactionCreateRequest
import com.example.data.network.transaction.model.TransactionDto
import com.example.data.network.wallet.WalletApi
import com.example.data.network.wallet.model.WalletMemberRoleUpdateRequest
import com.example.data.network.wallet.model.WalletShareRequest
import com.example.data.network.sync.model.PendingOperationPayload
import com.example.data.network.sync.model.SyncOperationResult
import com.example.data.network.sync.model.SyncPushRequest
import com.example.data.network.sync.model.SyncPushResponse
import com.example.data.network.wallet.model.WalletCreateRequest as ApiWalletCreateRequest
import com.example.data.network.wallet.model.WalletResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OfflineSyncOrchestrator(
    private val syncApi: OfflineSyncApi,
    private val transactionApi: TransactionApi,
    private val walletApi: WalletApi,
    private val goalApi: GoalApiService,
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao,
    private val walletMemberDao: WalletMemberDao,
    private val categoryDao: CategoryDao,
    private val goalDao: GoalDao,
    private val tagDao: TagDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val pendingOperationDao: PendingOperationDao,
    private val monthlySavingsGoalDao: MonthlySavingsGoalDao,
    private val monthlySavingsLocalRecalculator: MonthlySavingsLocalRecalculator,
    private val walletBalanceRecalculator: WalletBalanceRecalculator
) {

    private val syncMutex = Mutex()
    private var isSyncing = false
    private val json = Json { ignoreUnknownKeys = true }

    private fun buildGoalPayloadFromLocal(goal: com.example.data.database.entity.GoalEntity): String {
        // Build JsonObject to avoid Map<String, Any> (Any isn't serializable)
        val obj = buildJsonObject {
            put("title", goal.title)
            put("goalAmount", goal.goalAmount)
            put("currentAmount", goal.amountSaved)
            put("currency", goal.currency)
            put("description", goal.description ?: "")
            put("deadline", goal.deadline?.toString() ?: "")
            put("imageUrl", goal.image ?: "")
            put("walletId", goal.walletId)
        }
        return json.encodeToString(obj)
    }

    private fun buildWalletPayloadFromLocal(wallet: WalletEntity): String {
        val obj = buildJsonObject {
            put("name", wallet.name)
            put("currency", wallet.currency)
            put("wallet_type", wallet.walletType)
            put("card_number", wallet.cardNumber ?: "")
            put("color", wallet.color)
            put("balance", wallet.initialBalance.toDoubleOrNull() ?: 0.0)
        }
        return obj.toString()
    }

    suspend fun enqueueOperation(
        resourceType: String,
        resourceId: Int,
        operationType: String,
        payload: String? = null
    ) {
        println(
            "📥 OFFLINE_SYNC: enqueueOperation(resourceType=$resourceType, operationType=$operationType, " +
                "resourceId=$resourceId, payloadLen=${payload?.length ?: 0})"
        )
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

            var syncException: Exception? = null
            try {
                pushPendingOperations()
                pullLatestChanges(resourceName)
                walletBalanceRecalculator.recalculateAllWalletBalances()
                syncMetadataDao.upsert(
                    SyncMetadata(
                        resourceName = resourceName,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                println("❌ OFFLINE_SYNC: Sync failed for $resourceName: ${e.message}")
                syncException = e
            } finally {
                try {
                    recalculateMonthlySavingsFromLocalTransactions()
                } catch (e: Exception) {
                    println("❌ OFFLINE_SYNC: Monthly savings recalc failed: ${e.message}")
                }
                isSyncing = false
                println("🔵 OFFLINE_SYNC: Sync lock released for $resourceName")
            }

            if (syncException == null) {
                println("✅ OFFLINE_SYNC: Sync completed successfully for $resourceName")
                Result.success(Unit)
            } else {
                Result.failure(syncException)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pushPendingOperations() {
        val operations = pendingOperationDao.getAll()
        if (operations.isEmpty()) {
            println("📭 OFFLINE_SYNC: No pending operations to push")
            return
        }
        logPendingOperationsSummary(operations)
        println(
            "🚀 OFFLINE_SYNC: Pushing ${operations.size} queued ops (wallet delete/create use REST first, " +
                "then api/sync/push for the rest)"
        )

        val afterTransactionDeletes = processTransactionDeletesViaRest(operations)
        val afterTransactionCreates = processTransactionCreatesViaRest(afterTransactionDeletes)
        val afterWalletDeletes = processWalletDeletesViaRest(afterTransactionCreates)
        val afterWalletShareOps = processWalletShareOpsViaRest(afterWalletDeletes)
        val afterGoalDeletes = processGoalDeletesViaRest(afterWalletShareOps)
        val afterWalletCreates = processWalletCreatesViaRest(afterGoalDeletes)
        val afterGoalCreates = processGoalCreatesViaRest(afterWalletCreates)
        val forBatch = processGoalUpdatesViaRest(afterGoalCreates)
        if (forBatch.isEmpty()) {
            println("📭 OFFLINE_SYNC: No batch operations left after wallet REST delete/create flush")
            return
        }

        // Repair missing payloads for resources that require it (backend enforces this).
        val repairedOperations = forBatch.map { op ->
            when {
                op.payload.isNullOrBlank() &&
                    op.resourceType.equals("goal", ignoreCase = true) &&
                    (
                        op.operationType.equals("create", ignoreCase = true) ||
                            op.operationType.equals("update", ignoreCase = true)
                        ) -> {
                    val localGoal = goalDao.getGoalById(op.resourceId)
                    if (localGoal != null) {
                        val payload = buildGoalPayloadFromLocal(localGoal)
                        pendingOperationDao.updatePayload(op.id, payload)
                        op.copy(payload = payload)
                    } else {
                        op
                    }
                }
                op.payload.isNullOrBlank() &&
                    op.resourceType.equals("wallet", ignoreCase = true) &&
                    (
                        op.operationType.equals("create", ignoreCase = true) ||
                            op.operationType.equals("update", ignoreCase = true)
                        ) -> {
                    val w = walletDao.getWalletById(op.resourceId)
                    if (w != null) {
                        val payload = buildWalletPayloadFromLocal(w)
                        pendingOperationDao.updatePayload(op.id, payload)
                        op.copy(payload = payload)
                    } else {
                        op
                    }
                }
                else -> op
            }
        }

        val payload = SyncPushRequest(
            operations = repairedOperations.map {
                PendingOperationPayload(
                    resourceType = it.resourceType,
                    operationType = it.operationType,
                    resourceId = it.resourceId,
                    payload = it.payload
                )
            }
        )

        println(
            "📤 OFFLINE_SYNC: api/sync/push batch size=${repairedOperations.size} → " +
                repairedOperations.joinToString(limit = 20) {
                    "${it.resourceType}/${it.operationType}#${it.resourceId}"
                }
        )

        try {
            val response = syncApi.pushPendingOperations(payload)
            val now = System.currentTimeMillis()

            println("📦 OFFLINE_SYNC: Push response received with ${response.results.size} results")
            response.results.forEach { result ->
                println(
                    "   Result: ${result.resourceType} - ${result.operationType} - " +
                        "${result.resourceId} - ${result.status}" +
                        (result.serverResourceId?.let { " serverResourceId=$it" } ?: "")
                )
            }

            val aligned = alignOpsWithResults(repairedOperations, response.results)
            val tempWalletCreateRemap =
                remapSuccessfulWalletCreatesAfterPush(repairedOperations, response, aligned, now)
            val tempGoalCreateRemap =
                remapSuccessfulGoalCreatesAfterPush(repairedOperations, response, aligned, now)
            val tempTransactionCreateRemap =
                remapSuccessfulTransactionCreatesAfterPush(repairedOperations, response, aligned, now)

            val effectiveSuccessfulOps = aligned.mapNotNull { (op, res) ->
                if (res?.status != "success") return@mapNotNull null
                if (op.resourceType.equals("wallet", ignoreCase = true) &&
                    op.operationType.equals("create", ignoreCase = true) &&
                    op.resourceId < 0 &&
                    !tempWalletCreateRemap.containsKey(op.resourceId)
                ) {
                    return@mapNotNull null
                }
                if (op.resourceType.equals("goal", ignoreCase = true) &&
                    op.operationType.equals("create", ignoreCase = true) &&
                    op.resourceId < 0 &&
                    !tempGoalCreateRemap.containsKey(op.resourceId)
                ) {
                    return@mapNotNull null
                }
                if (op.resourceType.equals("transaction", ignoreCase = true) &&
                    op.operationType.equals("create", ignoreCase = true) &&
                    op.resourceId < 0 &&
                    !tempTransactionCreateRemap.containsKey(op.resourceId)
                ) {
                    return@mapNotNull null
                }
                op
            }

            val transactionIds = effectiveSuccessfulOps
                .filter { it.resourceType.equals("transaction", ignoreCase = true) }
                .mapNotNull { op ->
                    when {
                        op.operationType.equals("create", ignoreCase = true) && op.resourceId < 0 ->
                            tempTransactionCreateRemap[op.resourceId] ?: op.resourceId.takeIf { it > 0 }
                        else -> op.resourceId
                    }
                }
                .filter { it > 0 }
            val goalIds = effectiveSuccessfulOps.mapNotNull { op ->
                if (!op.resourceType.equals("goal", ignoreCase = true)) return@mapNotNull null
                if (op.operationType.equals("delete", ignoreCase = true)) return@mapNotNull null
                when {
                    op.operationType.equals("create", ignoreCase = true) && op.resourceId < 0 ->
                        tempGoalCreateRemap[op.resourceId]
                    else -> op.resourceId
                }
            }.filter { it > 0 }

            val walletIds = effectiveSuccessfulOps.mapNotNull { op ->
                if (!op.resourceType.equals("wallet", ignoreCase = true)) return@mapNotNull null
                if (op.operationType.equals("delete", ignoreCase = true)) return@mapNotNull null
                when {
                    op.operationType.equals("create", ignoreCase = true) && op.resourceId < 0 ->
                        tempWalletCreateRemap[op.resourceId]
                    else -> op.resourceId
                }
            }

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

            applyCurrentSavingsGoalFromPushResponse(response, now)

            if (response.transactions.isNotEmpty()) {
                reconcilePendingTransactionCreatesAfterPull(response.transactions, now)
                transactionDao.upsertTransactions(
                    response.transactions.map { it.toEntity().toLocalEntity(now, true) }
                )
                println("📦 OFFLINE_SYNC: Upserted ${response.transactions.size} transaction(s) from push response")
            }

            if (response.wallets.isNotEmpty()) {
                reconcilePendingWalletCreatesAfterPull(response.wallets, now)
                walletDao.upsertWallets(
                    response.wallets.map { it.toDomain().toLocalEntity(now, true) }
                )
                println("📦 OFFLINE_SYNC: Upserted ${response.wallets.size} wallet(s) from push response")
            }

            if (response.goals.isNotEmpty()) {
                reconcilePendingGoalCreatesAfterPull(response.goals, now)
                val goalUpserts = response.goals.mapNotNull { g ->
                    val existing = goalDao.getGoalById(g.id)
                    if (existing != null && !existing.isSynced) {
                        println("📦 OFFLINE_SYNC: skip push-response goal overwrite unsynced id=${g.id}")
                        null
                    } else {
                        g.toDomain().toLocalEntity(now, true)
                    }
                }
                if (goalUpserts.isNotEmpty()) {
                    goalDao.upsertGoals(goalUpserts)
                }
                println("📦 OFFLINE_SYNC: Upserted ${goalUpserts.size}/${response.goals.size} goal(s) from push response")
            }

            var removed = 0
            var retried = 0
            for ((op, result) in aligned) {
                when {
                    result?.status != "success" -> {
                        pendingOperationDao.incrementRetryCount(op.id)
                        retried++
                    }
                    op.resourceType.equals("wallet", ignoreCase = true) &&
                        op.operationType.equals("create", ignoreCase = true) &&
                        op.resourceId < 0 &&
                        !tempWalletCreateRemap.containsKey(op.resourceId) -> {
                        pendingOperationDao.incrementRetryCount(op.id)
                        retried++
                    }
                    op.resourceType.equals("goal", ignoreCase = true) &&
                        op.operationType.equals("create", ignoreCase = true) &&
                        op.resourceId < 0 &&
                        !tempGoalCreateRemap.containsKey(op.resourceId) -> {
                        pendingOperationDao.incrementRetryCount(op.id)
                        retried++
                    }
                    op.resourceType.equals("transaction", ignoreCase = true) &&
                        op.operationType.equals("create", ignoreCase = true) &&
                        op.resourceId < 0 &&
                        !tempTransactionCreateRemap.containsKey(op.resourceId) -> {
                        pendingOperationDao.incrementRetryCount(op.id)
                        retried++
                    }
                    else -> {
                        pendingOperationDao.removeById(op.id)
                        removed++
                    }
                }
            }

            println("✅ OFFLINE_SYNC: Push completed - $removed cleared, $retried kept for retry")

        } catch (e: Exception) {
            repairedOperations.forEach { pendingOperationDao.incrementRetryCount(it.id) }
            println(
                "❌ OFFLINE_SYNC: Push failed: ${e.message}, incremented retry for ${repairedOperations.size} batch ops"
            )
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
            val tagsCount = body.tags.size
            val walletMembersCount = body.walletMembers.size

            println(
                "📥 OFFLINE_SYNC: Pull response - ${transactionsCount} transactions, ${walletsCount} wallets, " +
                    "${goalsCount} goals, ${categoriesCount} categories, ${tagsCount} tags, " +
                    "${walletMembersCount} wallet_members"
            )

            if (transactionsCount > 0) {
                reconcilePendingTransactionCreatesAfterPull(body.transactions, now)
                transactionDao.upsertTransactions(body.transactions.map { it.toEntity().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${transactionsCount} transactions")
            }
            if (walletsCount > 0) {
                reconcilePendingWalletCreatesAfterPull(body.wallets, now)
                walletDao.upsertWallets(body.wallets.map { it.toDomain().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${walletsCount} wallets")
            }
            if (goalsCount > 0) {
                reconcilePendingGoalCreatesAfterPull(body.goals, now)
                val toUpsert = body.goals.mapNotNull { resp ->
                    val existing = goalDao.getGoalById(resp.id)
                    if (existing != null && !existing.isSynced) {
                        println("📥 OFFLINE_SYNC: skip pull overwrite unsynced goal id=${resp.id}")
                        null
                    } else {
                        resp.toDomain().toLocalEntity(now, true)
                    }
                }
                if (toUpsert.isNotEmpty()) {
                    goalDao.upsertGoals(toUpsert)
                }
                println("📥 OFFLINE_SYNC: Updated ${toUpsert.size}/$goalsCount goals (respecting unsynced local rows)")
            }
            if (categoriesCount > 0) {
                categoryDao.upsertCategories(body.categories.map { it.toDomain().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${categoriesCount} categories")
            }
            if (tagsCount > 0) {
                tagDao.upsertTags(body.tags.map { it.toEntity().toLocalEntity(now, true) })
                println("📥 OFFLINE_SYNC: Updated ${tagsCount} tags")
            }
            if (walletMembersCount > 0) {
                walletMemberDao.upsertMembers(
                    body.walletMembers.map { it.toLocalEntity(isSynced = true) }
                )
                println("📥 OFFLINE_SYNC: Updated $walletMembersCount wallet member(s)")
            }

            println("📥 OFFLINE_SYNC: Pull completed successfully")
        } catch (e: Exception) {
            println("❌ OFFLINE_SYNC: Pull failed: ${e.message}")
            throw IllegalStateException("Pull failed: ${e.message}")
        }
    }

    /**
     * Goal mutations should hit REST first; batch [OfflineSyncApi.pushPendingOperations] may not apply them.
     */
    private suspend fun processGoalDeletesViaRest(operations: List<PendingOperation>): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            val isGoalDelete =
                op.resourceType.equals("goal", ignoreCase = true) &&
                    op.operationType.equals("delete", ignoreCase = true)
            if (!isGoalDelete) {
                forBatch.add(op)
                continue
            }
            if (op.resourceId <= 0) {
                pendingOperationDao.removeById(op.id)
                println("⚠️ OFFLINE_SYNC: dropped goal delete for temp id=${op.resourceId}")
                continue
            }
            try {
                val resp = goalApi.deleteGoal(op.resourceId)
                val ok = resp.isSuccessful || resp.code() == 404
                if (ok) {
                    pendingOperationDao.removeById(op.id)
                    println("✅ OFFLINE_SYNC: Goal delete via REST id=${op.resourceId} code=${resp.code()}")
                } else {
                    pendingOperationDao.incrementRetryCount(op.id)
                    forBatch.add(op)
                    println("⚠️ OFFLINE_SYNC: Goal REST delete failed id=${op.resourceId} code=${resp.code()}")
                }
            } catch (e: Exception) {
                pendingOperationDao.incrementRetryCount(op.id)
                forBatch.add(op)
                println("⚠️ OFFLINE_SYNC: Goal REST delete error id=${op.resourceId}: ${e.message}")
            }
        }
        return forBatch
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processGoalCreatesViaRest(operations: List<PendingOperation>): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            val isGoalCreate =
                op.resourceType.equals("goal", ignoreCase = true) &&
                    op.operationType.equals("create", ignoreCase = true) &&
                    op.resourceId < 0
            if (!isGoalCreate) {
                forBatch.add(op)
                continue
            }
            val local = goalDao.getGoalById(op.resourceId)
            if (local == null) {
                pendingOperationDao.removeById(op.id)
                continue
            }
            val req = GoalCreateRequest(
                title = local.title,
                goalAmount = local.goalAmount,
                currency = local.currency,
                description = local.description,
                deadline = local.deadline?.toString()
            )
            try {
                println(
                    "🌐 OFFLINE_SYNC: POST api/goals for offline create " +
                        "tempGoalId=${op.resourceId} pendingOpDbId=${op.id}"
                )
                val now = System.currentTimeMillis()
                val resp = goalApi.createGoal(req)
                if (!resp.isSuccessful || resp.body() == null) {
                    pendingOperationDao.incrementRetryCount(op.id)
                    forBatch.add(op)
                    println("⚠️ OFFLINE_SYNC: REST goal create failed temp=${op.resourceId} code=${resp.code()}")
                    continue
                }
                var created = resp.body()!!
                val newId = created.id
                if (local.amountSaved > created.current_amount + 1e-9) {
                    val putReq = GoalUpdateRequest(
                        title = local.title,
                        description = local.description,
                        deadline = local.deadline?.toString(),
                        goal_amount = local.goalAmount.toString(),
                        current_amount = local.amountSaved
                    )
                    val putResp = goalApi.updateGoal(newId, putReq)
                    if (putResp.isSuccessful && putResp.body() != null) {
                        created = putResp.body()!!
                    } else {
                        println(
                            "⚠️ OFFLINE_SYNC: POST goal ok but PUT current_amount failed id=$newId code=${putResp.code()}"
                        )
                    }
                }
                remapLocalGoalToServerId(op.resourceId, newId, now, created)
                pendingOperationDao.removeById(op.id)
                println("✅ OFFLINE_SYNC: REST goal create temp=${op.resourceId} -> serverId=$newId")
            } catch (e: Exception) {
                pendingOperationDao.incrementRetryCount(op.id)
                forBatch.add(op)
                println("⚠️ OFFLINE_SYNC: REST goal create error temp=${op.resourceId}: ${e.message}")
            }
        }
        return forBatch
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processGoalUpdatesViaRest(operations: List<PendingOperation>): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            val isGoalUpdate =
                op.resourceType.equals("goal", ignoreCase = true) &&
                    op.operationType.equals("update", ignoreCase = true)
            if (!isGoalUpdate) {
                forBatch.add(op)
                continue
            }
            if (op.resourceId <= 0) {
                forBatch.add(op)
                continue
            }
            val local = goalDao.getGoalById(op.resourceId)
            if (local == null) {
                println("⚠️ OFFLINE_SYNC: goal update but Room missing id=${op.resourceId}, dropping pending op")
                pendingOperationDao.removeById(op.id)
                continue
            }
            try {
                val request = GoalUpdateRequest(
                    title = local.title,
                    description = local.description,
                    deadline = local.deadline?.toString(),
                    goal_amount = local.goalAmount.toString(),
                    current_amount = local.amountSaved
                )
                val resp = goalApi.updateGoal(op.resourceId, request)
                if (resp.isSuccessful) {
                    val now = System.currentTimeMillis()
                    val body = resp.body()
                    if (body != null) {
                        val fromServer = body.toDomain().toLocalEntity(now, true)
                        goalDao.upsertGoal(
                            fromServer.copy(amountSaved = maxOf(fromServer.amountSaved, local.amountSaved))
                        )
                    } else {
                        goalDao.upsertGoal(local.copy(isSynced = true, updatedAt = now))
                    }
                    pendingOperationDao.removeById(op.id)
                    println("✅ OFFLINE_SYNC: REST goal update id=${op.resourceId}")
                } else {
                    pendingOperationDao.incrementRetryCount(op.id)
                    forBatch.add(op)
                    println("⚠️ OFFLINE_SYNC: REST goal update failed id=${op.resourceId} code=${resp.code()}")
                }
            } catch (e: Exception) {
                pendingOperationDao.incrementRetryCount(op.id)
                forBatch.add(op)
                println("⚠️ OFFLINE_SYNC: REST goal update error id=${op.resourceId}: ${e.message}")
            }
        }
        return forBatch
    }

    private fun pickMatchingGoalFromResponse(local: GoalEntity, pulled: List<GoalResponse>): GoalResponse? =
        pulled.find { g ->
            g.title == local.title &&
                g.goal_amount == local.goalAmount &&
                g.currency == local.currency
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun remapLocalGoalToServerId(
        oldId: Int,
        newId: Int,
        now: Long,
        serverGoal: GoalResponse
    ) {
        if (oldId == newId) {
            val cur = goalDao.getGoalById(oldId) ?: return
            val s = serverGoal.toDomain().toLocalEntity(now, true)
            goalDao.upsertGoal(s.copy(amountSaved = maxOf(s.amountSaved, cur.amountSaved)))
            return
        }
        val local = goalDao.getGoalById(oldId) ?: return
        val fromServer = serverGoal.toDomain().toLocalEntity(now, true)
        val merged = fromServer.copy(
            amountSaved = maxOf(fromServer.amountSaved, local.amountSaved),
            image = fromServer.image ?: local.image,
            walletId = local.walletId.takeIf { it != 0 } ?: fromServer.walletId
        )
        goalDao.upsertGoal(merged.copy(id = newId, isSynced = true, updatedAt = now))
        pendingOperationDao.remapGoalResourceIds(oldId, newId)
        goalDao.deleteGoalById(oldId)
        println("✅ OFFLINE_SYNC: Remapped local goal $oldId -> server id $newId")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun reconcilePendingGoalCreatesAfterPull(
        pulled: List<GoalResponse>,
        now: Long
    ) {
        val pendingCreates = pendingOperationDao.getAll().filter {
            it.resourceType.equals("goal", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.resourceId < 0
        }
        if (pendingCreates.isEmpty()) return
        for (op in pendingCreates) {
            val local = goalDao.getGoalById(op.resourceId)
            if (local == null) {
                pendingOperationDao.removeById(op.id)
                continue
            }
            val match = pickMatchingGoalFromResponse(local, pulled) ?: continue
            println(
                "🔁 OFFLINE_SYNC: Pull reconcile temp goal ${op.resourceId} -> server id ${match.id}"
            )
            remapLocalGoalToServerId(op.resourceId, match.id, now, match)
            pendingOperationDao.removeById(op.id)
        }
    }

    private fun resolveServerGoalIdForCreatedGoal(
        op: PendingOperation,
        result: SyncOperationResult,
        response: SyncPushResponse,
        local: GoalEntity?
    ): Int? {
        result.serverResourceId?.takeIf { it > 0 }?.let { return it }
        if (op.resourceId < 0 && result.resourceId > 0) return result.resourceId
        val l = local ?: return null
        return pickMatchingGoalFromResponse(l, response.goals)?.id?.takeIf { it > 0 }
    }

    /**
     * Wallet share / member mutations use dedicated REST endpoints before batch sync.
     */
    private suspend fun processWalletShareOpsViaRest(operations: List<PendingOperation>): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            if (!op.resourceType.equals("wallet", ignoreCase = true)) {
                forBatch.add(op)
                continue
            }
            when (op.operationType.lowercase()) {
                "share_wallet" -> {
                    if (op.resourceId <= 0) {
                        pendingOperationDao.removeById(op.id)
                        continue
                    }
                    val payloadObj = runCatching {
                        parseToJsonElement(op.payload ?: "{}").jsonObject
                    }.getOrNull()
                    if (payloadObj == null) {
                        pendingOperationDao.incrementRetryCount(op.id)
                        forBatch.add(op)
                        continue
                    }
                    val email = payloadObj["email"]?.jsonPrimitive?.content
                    val role = payloadObj["role"]?.jsonPrimitive?.content ?: "viewer"
                    if (email.isNullOrBlank()) {
                        pendingOperationDao.removeById(op.id)
                        continue
                    }
                    try {
                        val resp = walletApi.shareWallet(
                            op.resourceId,
                            WalletShareRequest(email = email, role = role)
                        )
                        if (resp.isSuccessful) {
                            resp.body()?.let { m ->
                                walletMemberDao.upsertMember(m.toLocalEntity(walletId = op.resourceId, isSynced = true))
                            }
                            pendingOperationDao.removeById(op.id)
                            println("✅ OFFLINE_SYNC: REST share_wallet walletId=${op.resourceId}")
                        } else {
                            pendingOperationDao.incrementRetryCount(op.id)
                            forBatch.add(op)
                        }
                    } catch (e: Exception) {
                        pendingOperationDao.incrementRetryCount(op.id)
                        forBatch.add(op)
                        println("⚠️ OFFLINE_SYNC: REST share_wallet failed: ${e.message}")
                    }
                }
                "update_member_role" -> {
                    val payloadObj = runCatching {
                        parseToJsonElement(op.payload ?: "{}").jsonObject
                    }.getOrNull()
                    if (payloadObj == null) {
                        pendingOperationDao.incrementRetryCount(op.id)
                        forBatch.add(op)
                        continue
                    }
                    val userId = payloadObj["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                    val role = payloadObj["role"]?.jsonPrimitive?.content
                    if (userId == null || role.isNullOrBlank()) {
                        pendingOperationDao.removeById(op.id)
                        continue
                    }
                    try {
                        val resp = walletApi.updateMemberRole(
                            op.resourceId,
                            userId,
                            WalletMemberRoleUpdateRequest(role = role)
                        )
                        if (resp.isSuccessful) {
                            walletMemberDao.updateMemberRole(op.resourceId, userId, role)
                            pendingOperationDao.removeById(op.id)
                            println("✅ OFFLINE_SYNC: REST update_member_role walletId=${op.resourceId}")
                        } else {
                            pendingOperationDao.incrementRetryCount(op.id)
                            forBatch.add(op)
                        }
                    } catch (e: Exception) {
                        pendingOperationDao.incrementRetryCount(op.id)
                        forBatch.add(op)
                    }
                }
                "remove_member" -> {
                    val payloadObj = runCatching {
                        parseToJsonElement(op.payload ?: "{}").jsonObject
                    }.getOrNull()
                    if (payloadObj == null) {
                        pendingOperationDao.incrementRetryCount(op.id)
                        forBatch.add(op)
                        continue
                    }
                    val userId = payloadObj["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (userId == null) {
                        pendingOperationDao.removeById(op.id)
                        continue
                    }
                    try {
                        val resp = walletApi.removeMember(op.resourceId, userId)
                        if (resp.isSuccessful || resp.code() == 404) {
                            walletMemberDao.deleteMember(op.resourceId, userId)
                            pendingOperationDao.removeById(op.id)
                            println("✅ OFFLINE_SYNC: REST remove_member walletId=${op.resourceId}")
                        } else {
                            pendingOperationDao.incrementRetryCount(op.id)
                            forBatch.add(op)
                        }
                    } catch (e: Exception) {
                        pendingOperationDao.incrementRetryCount(op.id)
                        forBatch.add(op)
                    }
                }
                else -> forBatch.add(op)
            }
        }
        return forBatch
    }

    /**
     * Offline transaction creates should hit POST /api/transactions/ so the client can replace
     * the temp negative id with the server id (avoids duplicate rows after sync).
     */
    private suspend fun processTransactionCreatesViaRest(
        operations: List<PendingOperation>
    ): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            val isTransactionCreate =
                op.resourceType.equals("transaction", ignoreCase = true) &&
                    op.operationType.equals("create", ignoreCase = true) &&
                    op.resourceId < 0
            if (!isTransactionCreate) {
                forBatch.add(op)
                continue
            }
            val local = transactionDao.getTransactionByTempId(op.resourceId)
            if (local == null) {
                println(
                    "⚠️ OFFLINE_SYNC: transaction create pending but Room row missing " +
                        "resourceId=${op.resourceId}, removing pending op id=${op.id}"
                )
                pendingOperationDao.removeById(op.id)
                continue
            }
            val request = TransactionCreateRequest(
                name = local.name,
                amount = local.amount,
                type = local.type,
                transactionDate = local.transactionDate,
                walletId = local.walletId,
                categoryId = local.categoryId,
                note = local.note,
                tags = local.tags
            )
            try {
                println(
                    "🌐 OFFLINE_SYNC: POST api/transactions for offline create " +
                        "tempTxId=${op.resourceId} pendingOpDbId=${op.id} name=${local.name}"
                )
                val now = System.currentTimeMillis()
                val response = transactionApi.createTransaction(request)
                if (!response.isSuccessful) {
                    pendingOperationDao.incrementRetryCount(op.id)
                    forBatch.add(op)
                    println(
                        "⚠️ OFFLINE_SYNC: REST transaction create failed temp=${op.resourceId} " +
                            "code=${response.code()}; keeping for api/sync/push"
                    )
                    continue
                }
                val created = response.body()
                if (created == null) {
                    pendingOperationDao.incrementRetryCount(op.id)
                    forBatch.add(op)
                    continue
                }
                remapLocalTransactionToServerId(op.resourceId, created.id, now, created)
                pendingOperationDao.removeById(op.id)
                println(
                    "✅ OFFLINE_SYNC: REST transaction create temp=${op.resourceId} -> serverId=${created.id}"
                )
            } catch (e: Exception) {
                pendingOperationDao.incrementRetryCount(op.id)
                forBatch.add(op)
                println(
                    "⚠️ OFFLINE_SYNC: REST transaction create error temp=${op.resourceId}: ${e.message}"
                )
            }
        }
        return forBatch
    }

    /**
     * Transaction deletes must hit DELETE /api/transactions/{transaction_id}.
     */
    private suspend fun processTransactionDeletesViaRest(
        operations: List<PendingOperation>
    ): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            val isTransactionDelete =
                op.resourceType.equals("transaction", ignoreCase = true) &&
                    op.operationType.equals("delete", ignoreCase = true)
            if (!isTransactionDelete) {
                forBatch.add(op)
                continue
            }
            if (op.resourceId <= 0) {
                pendingOperationDao.removeById(op.id)
                println("⚠️ OFFLINE_SYNC: dropped transaction delete for temp id=${op.resourceId}")
                continue
            }
            try {
                val resp = transactionApi.deleteTransaction(op.resourceId)
                val ok = resp.isSuccessful || resp.code() == 404
                if (ok) {
                    transactionDao.deleteTransactionById(op.resourceId)
                    pendingOperationDao.removeById(op.id)
                    println(
                        "✅ OFFLINE_SYNC: Transaction delete via REST id=${op.resourceId} code=${resp.code()}"
                    )
                } else {
                    pendingOperationDao.incrementRetryCount(op.id)
                    forBatch.add(op)
                    println(
                        "⚠️ OFFLINE_SYNC: Transaction REST delete failed id=${op.resourceId} code=${resp.code()}"
                    )
                }
            } catch (e: Exception) {
                pendingOperationDao.incrementRetryCount(op.id)
                forBatch.add(op)
                println("⚠️ OFFLINE_SYNC: Transaction REST delete error id=${op.resourceId}: ${e.message}")
            }
        }
        return forBatch
    }

    /**
     * Wallet deletes must hit DELETE /api/wallets/{id}. The batch sync endpoint may not apply them.
     */
    private suspend fun processWalletDeletesViaRest(operations: List<PendingOperation>): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            val isWalletDelete =
                op.resourceType.equals("wallet", ignoreCase = true) &&
                    op.operationType.equals("delete", ignoreCase = true)
            if (!isWalletDelete) {
                forBatch.add(op)
                continue
            }
            if (op.resourceId <= 0) {
                pendingOperationDao.removeById(op.id)
                println("⚠️ OFFLINE_SYNC: dropped invalid wallet delete (temp id=${op.resourceId})")
                continue
            }
            try {
                val resp = walletApi.deleteWallet(op.resourceId)
                val ok = resp.isSuccessful || resp.code() == 404
                if (ok) {
                    pendingOperationDao.removeById(op.id)
                    println(
                        "✅ OFFLINE_SYNC: Wallet delete via REST id=${op.resourceId} code=${resp.code()}"
                    )
                } else {
                    pendingOperationDao.incrementRetryCount(op.id)
                    println(
                        "⚠️ OFFLINE_SYNC: Wallet REST delete failed id=${op.resourceId} code=${resp.code()}"
                    )
                }
            } catch (e: Exception) {
                pendingOperationDao.incrementRetryCount(op.id)
                println("⚠️ OFFLINE_SYNC: Wallet REST delete error id=${op.resourceId}: ${e.message}")
            }
        }
        return forBatch
    }

    /**
     * Wallet creates must hit POST /api/wallets. Many backends do not apply wallet create from
     * [OfflineSyncApi.pushPendingOperations]; mirror transaction visibility by making real HTTP POST here.
     * On failure, the op stays in the queue for `/api/sync/push` fallback.
     */
    private suspend fun processWalletCreatesViaRest(operations: List<PendingOperation>): List<PendingOperation> {
        val forBatch = mutableListOf<PendingOperation>()
        for (op in operations) {
            val isWalletCreate =
                op.resourceType.equals("wallet", ignoreCase = true) &&
                    op.operationType.equals("create", ignoreCase = true) &&
                    op.resourceId < 0
            if (!isWalletCreate) {
                forBatch.add(op)
                continue
            }
            val local = walletDao.getWalletById(op.resourceId)
            if (local == null) {
                println(
                    "⚠️ OFFLINE_SYNC: wallet create pending but Room row missing resourceId=${op.resourceId}, " +
                        "removing pending op id=${op.id}"
                )
                pendingOperationDao.removeById(op.id)
                continue
            }
            val request = ApiWalletCreateRequest(
                name = local.name,
                currency = local.currency,
                wallet_type = local.walletType,
                card_number = local.cardNumber,
                color = local.color,
                balance = local.initialBalance.toDoubleOrNull() ?: 0.0
            )
            try {
                println(
                    "🌐 OFFLINE_SYNC: POST api/wallets for offline create " +
                        "tempWalletId=${op.resourceId} pendingOpDbId=${op.id} name=${local.name}"
                )
                val now = System.currentTimeMillis()
                val created = walletApi.createWallet(request)
                remapLocalWalletToServerId(op.resourceId, created.id, now, created)
                pendingOperationDao.removeById(op.id)
                println(
                    "✅ OFFLINE_SYNC: REST wallet create temp=${op.resourceId} -> serverId=${created.id}"
                )
            } catch (e: Exception) {
                pendingOperationDao.incrementRetryCount(op.id)
                forBatch.add(op)
                println(
                    "⚠️ OFFLINE_SYNC: REST wallet create failed temp=${op.resourceId}; " +
                        "keeping for api/sync/push: ${e.message}"
                )
            }
        }
        return forBatch
    }

    private fun logPendingOperationsSummary(operations: List<PendingOperation>) {
        val counts = operations.groupingBy { "${it.resourceType}/${it.operationType}" }.eachCount()
        println(
            "📋 OFFLINE_SYNC: Pending queue: " +
                counts.entries.joinToString(separator = ", ") { "${it.key}=${it.value}" }
        )
        operations.filter { it.resourceType.equals("wallet", ignoreCase = true) }.forEach { op ->
            println(
                "   └─ wallet: pendingRowId=${op.id} resourceId=${op.resourceId} op=${op.operationType} " +
                    "payloadEmpty=${op.payload.isNullOrBlank()}"
            )
        }
    }

    /**
     * Match a local wallet row to server DTOs (strict name+currency+type, then unique name+currency).
     */
    private fun pickMatchingWalletFromResponse(
        local: WalletEntity,
        pulled: List<WalletResponse>
    ): WalletResponse? {
        val strict = pulled.filter { w ->
            w.name == local.name &&
                w.currency == local.currency &&
                w.wallet_type.equals(local.walletType, ignoreCase = true)
        }
        if (strict.size == 1) return strict.first()
        val loose = pulled.filter { w -> w.name == local.name && w.currency == local.currency }
        if (loose.size == 1) return loose.first()
        return null
    }

    /**
     * If the server already has a wallet that matches a pending offline create (e.g. push succeeded
     * but client id remap failed), merge the temp row into the server id and drop the duplicate.
     */
    private suspend fun reconcilePendingWalletCreatesAfterPull(
        pulled: List<WalletResponse>,
        now: Long
    ) {
        val pendingCreates = pendingOperationDao.getAll().filter {
            it.resourceType.equals("wallet", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.resourceId < 0
        }
        if (pendingCreates.isEmpty()) return
        for (op in pendingCreates) {
            val local = walletDao.getWalletById(op.resourceId)
            if (local == null) {
                pendingOperationDao.removeById(op.id)
                continue
            }
            val match = pickMatchingWalletFromResponse(local, pulled) ?: continue
            println(
                "🔁 OFFLINE_SYNC: Pull reconcile temp wallet ${op.resourceId} -> server id ${match.id}"
            )
            remapLocalWalletToServerId(op.resourceId, match.id, now, match)
            pendingOperationDao.removeById(op.id)
        }
    }

    private fun alignOpsWithResults(
        operations: List<PendingOperation>,
        results: List<SyncOperationResult>
    ): List<Pair<PendingOperation, SyncOperationResult?>> {
        if (operations.size == results.size) {
            return operations.zip(results) { o, r -> o to r }
        }
        println(
            "⚠️ OFFLINE_SYNC: push results count (${results.size}) != operations (${operations.size}); " +
                "matching by resourceType, operationType, resourceId"
        )
        return operations.map { op ->
            op to results.find { r ->
                r.resourceType.equals(op.resourceType, ignoreCase = true) &&
                    r.operationType.equals(op.operationType, ignoreCase = true) &&
                    (
                        r.resourceId == op.resourceId ||
                            (
                                op.resourceType.equals("wallet", ignoreCase = true) &&
                                    op.operationType.equals("create", ignoreCase = true) &&
                                    op.resourceId < 0 &&
                                    r.status == "success"
                                ) ||
                            (
                                op.resourceType.equals("goal", ignoreCase = true) &&
                                    op.operationType.equals("create", ignoreCase = true) &&
                                    op.resourceId < 0 &&
                                    r.status == "success"
                                ) ||
                            (
                                op.resourceType.equals("transaction", ignoreCase = true) &&
                                    op.operationType.equals("create", ignoreCase = true) &&
                                    op.resourceId < 0 &&
                                    r.status == "success"
                                )
                        )
            }
        }
    }

    private fun normalizeTransactionAmount(amount: String): String {
        return amount.toDoubleOrNull()?.let { "%.2f".format(it) } ?: amount.trim()
    }

    private fun pickMatchingTransactionFromResponse(
        local: TransactionEntity,
        pulled: List<TransactionDto>
    ): TransactionDto? {
        val localAmount = normalizeTransactionAmount(local.amount)
        val strict = pulled.filter { tx ->
            tx.name.equals(local.name, ignoreCase = true) &&
                normalizeTransactionAmount(tx.amount.toString()) == localAmount &&
                tx.walletId == local.walletId &&
                tx.categoryId == local.categoryId &&
                tx.type.equals(local.type, ignoreCase = true)
        }
        if (strict.size == 1) return strict.first()
        val loose = pulled.filter { tx ->
            tx.name.equals(local.name, ignoreCase = true) &&
                normalizeTransactionAmount(tx.amount.toString()) == localAmount &&
                tx.walletId == local.walletId
        }
        if (loose.size == 1) return loose.first()
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun remapLocalTransactionToServerId(
        oldId: Int,
        newId: Int,
        now: Long,
        serverTx: TransactionDto?
    ) {
        if (oldId == newId) {
            serverTx?.let {
                transactionDao.upsertTransaction(it.toEntity().toLocalEntity(now, true))
            }
            return
        }
        val local = transactionDao.getTransactionByTempId(oldId)
        val merged = if (serverTx != null) {
            serverTx.toEntity().toLocalEntity(now, true)
        } else {
            local?.copy(id = newId, isSynced = true, updatedAt = now) ?: return
        }
        transactionDao.upsertTransaction(merged.copy(id = newId, isSynced = true, updatedAt = now))
        pendingOperationDao.remapTransactionResourceIds(oldId, newId)
        transactionDao.deleteTransactionById(oldId)
        walletBalanceRecalculator.recalculateWalletBalance(merged.walletId)
        println("✅ OFFLINE_SYNC: Remapped local transaction $oldId -> server id $newId")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun reconcilePendingTransactionCreatesAfterPull(
        pulled: List<TransactionDto>,
        now: Long
    ) {
        val pendingCreates = pendingOperationDao.getAll().filter {
            it.resourceType.equals("transaction", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.resourceId < 0
        }
        if (pendingCreates.isEmpty()) return
        for (op in pendingCreates) {
            val local = transactionDao.getTransactionByTempId(op.resourceId)
            if (local == null) {
                pendingOperationDao.removeById(op.id)
                continue
            }
            val match = pickMatchingTransactionFromResponse(local, pulled) ?: continue
            println(
                "🔁 OFFLINE_SYNC: Pull reconcile temp transaction ${op.resourceId} -> server id ${match.id}"
            )
            remapLocalTransactionToServerId(op.resourceId, match.id, now, match)
            pendingOperationDao.removeById(op.id)
        }
    }

    private fun resolveServerTransactionIdForCreatedTransaction(
        op: PendingOperation,
        result: SyncOperationResult,
        response: SyncPushResponse,
        local: TransactionEntity?
    ): Int? {
        result.serverResourceId?.takeIf { it > 0 }?.let { return it }
        if (op.resourceId < 0 && result.resourceId > 0) return result.resourceId
        val l = local ?: return null
        return pickMatchingTransactionFromResponse(l, response.transactions)?.id?.takeIf { it > 0 }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun remapSuccessfulTransactionCreatesAfterPush(
        repairedOperations: List<PendingOperation>,
        response: SyncPushResponse,
        aligned: List<Pair<PendingOperation, SyncOperationResult?>>,
        now: Long
    ): MutableMap<Int, Int> {
        val tempTransactionCreateRemap = mutableMapOf<Int, Int>()

        suspend fun tryRemapOne(op: PendingOperation, result: SyncOperationResult) {
            if (result.status != "success") return
            if (!op.resourceType.equals("transaction", ignoreCase = true)) return
            if (!op.operationType.equals("create", ignoreCase = true)) return
            if (op.resourceId >= 0) return
            if (tempTransactionCreateRemap.containsKey(op.resourceId)) return
            val local = transactionDao.getTransactionByTempId(op.resourceId) ?: return
            val serverId = resolveServerTransactionIdForCreatedTransaction(op, result, response, local)
                ?: run {
                    println(
                        "⚠️ OFFLINE_SYNC: transaction create success but server id unresolved " +
                            "clientId=${op.resourceId}"
                    )
                    return
                }
            if (serverId == op.resourceId) return
            val serverTx =
                response.transactions.firstOrNull { it.id == serverId }
                    ?: pickMatchingTransactionFromResponse(local, response.transactions)
            remapLocalTransactionToServerId(op.resourceId, serverId, now, serverTx)
            tempTransactionCreateRemap[op.resourceId] = serverId
        }

        for ((op, result) in aligned) {
            val r = result ?: continue
            tryRemapOne(op, r)
        }

        val txCreateOps = repairedOperations.filter {
            it.resourceType.equals("transaction", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.resourceId < 0
        }
        val txCreateResults = response.results.filter {
            it.resourceType.equals("transaction", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.status == "success"
        }
        if (txCreateOps.size == txCreateResults.size) {
            txCreateOps.zip(txCreateResults).forEach { (op, result) ->
                tryRemapOne(op, result)
            }
        } else if (txCreateOps.isNotEmpty() && txCreateResults.isNotEmpty()) {
            println(
                "⚠️ OFFLINE_SYNC: transaction create op count (${txCreateOps.size}) != " +
                    "success result count (${txCreateResults.size}); using primary alignment only"
            )
        }

        return tempTransactionCreateRemap
    }

    /**
     * Map offline wallet creates (negative ids) to server ids after push — same outcome as
     * replacing the local transaction id when sync returns the real id.
     */
    private suspend fun remapSuccessfulWalletCreatesAfterPush(
        repairedOperations: List<PendingOperation>,
        response: SyncPushResponse,
        aligned: List<Pair<PendingOperation, SyncOperationResult?>>,
        now: Long
    ): MutableMap<Int, Int> {
        val tempWalletCreateRemap = mutableMapOf<Int, Int>()

        suspend fun tryRemapOne(op: PendingOperation, result: SyncOperationResult) {
            if (result.status != "success") return
            if (!op.resourceType.equals("wallet", ignoreCase = true)) return
            if (!op.operationType.equals("create", ignoreCase = true)) return
            if (op.resourceId >= 0) return
            if (tempWalletCreateRemap.containsKey(op.resourceId)) return
            val local = walletDao.getWalletById(op.resourceId) ?: return
            val serverId = resolveServerWalletIdForCreatedWallet(op, result, response, local)
                ?: run {
                    println(
                        "⚠️ OFFLINE_SYNC: wallet create success but server id unresolved clientId=${op.resourceId}"
                    )
                    return
                }
            if (serverId == op.resourceId) return
            val serverWallet =
                response.wallets.firstOrNull { it.id == serverId }
                    ?: pickMatchingWalletFromResponse(local, response.wallets)
            remapLocalWalletToServerId(op.resourceId, serverId, now, serverWallet)
            tempWalletCreateRemap[op.resourceId] = serverId
        }

        for ((op, result) in aligned) {
            val r = result ?: continue
            tryRemapOne(op, r)
        }

        val walletCreateOps = repairedOperations.filter {
            it.resourceType.equals("wallet", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.resourceId < 0
        }
        val walletCreateResults = response.results.filter {
            it.resourceType.equals("wallet", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.status == "success"
        }
        if (walletCreateOps.size == walletCreateResults.size) {
            walletCreateOps.zip(walletCreateResults).forEach { (op, result) ->
                tryRemapOne(op, result)
            }
        } else if (walletCreateOps.isNotEmpty() && walletCreateResults.isNotEmpty()) {
            println(
                "⚠️ OFFLINE_SYNC: wallet create op count (${walletCreateOps.size}) != " +
                    "success result count (${walletCreateResults.size}); using primary alignment only"
            )
        }

        return tempWalletCreateRemap
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun remapSuccessfulGoalCreatesAfterPush(
        repairedOperations: List<PendingOperation>,
        response: SyncPushResponse,
        aligned: List<Pair<PendingOperation, SyncOperationResult?>>,
        now: Long
    ): MutableMap<Int, Int> {
        val tempGoalCreateRemap = mutableMapOf<Int, Int>()

        suspend fun tryRemapOne(op: PendingOperation, result: SyncOperationResult) {
            if (result.status != "success") return
            if (!op.resourceType.equals("goal", ignoreCase = true)) return
            if (!op.operationType.equals("create", ignoreCase = true)) return
            if (op.resourceId >= 0) return
            if (tempGoalCreateRemap.containsKey(op.resourceId)) return
            val local = goalDao.getGoalById(op.resourceId) ?: return
            val serverId = resolveServerGoalIdForCreatedGoal(op, result, response, local)
                ?: run {
                    println(
                        "⚠️ OFFLINE_SYNC: goal create success but server id unresolved clientId=${op.resourceId}"
                    )
                    return
                }
            if (serverId == op.resourceId) return
            val serverGoal =
                response.goals.firstOrNull { it.id == serverId }
                    ?: pickMatchingGoalFromResponse(local, response.goals)
                    ?: return
            remapLocalGoalToServerId(op.resourceId, serverId, now, serverGoal)
            tempGoalCreateRemap[op.resourceId] = serverId
        }

        for ((op, result) in aligned) {
            val r = result ?: continue
            tryRemapOne(op, r)
        }

        val goalCreateOps = repairedOperations.filter {
            it.resourceType.equals("goal", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.resourceId < 0
        }
        val goalCreateResults = response.results.filter {
            it.resourceType.equals("goal", ignoreCase = true) &&
                it.operationType.equals("create", ignoreCase = true) &&
                it.status == "success"
        }
        if (goalCreateOps.size == goalCreateResults.size) {
            goalCreateOps.zip(goalCreateResults).forEach { (op, result) ->
                tryRemapOne(op, result)
            }
        } else if (goalCreateOps.isNotEmpty() && goalCreateResults.isNotEmpty()) {
            println(
                "⚠️ OFFLINE_SYNC: goal create op count (${goalCreateOps.size}) != " +
                    "success result count (${goalCreateResults.size}); using primary alignment only"
            )
        }

        return tempGoalCreateRemap
    }

    private fun resolveServerWalletIdForCreatedWallet(
        op: PendingOperation,
        result: SyncOperationResult,
        response: SyncPushResponse,
        local: WalletEntity?
    ): Int? {
        result.serverResourceId?.takeIf { it > 0 }?.let { return it }
        if (op.resourceId < 0 && result.resourceId > 0) return result.resourceId
        val l = local ?: return null
        return pickMatchingWalletFromResponse(l, response.wallets)?.id?.takeIf { it > 0 }
    }

    private suspend fun remapLocalWalletToServerId(
        oldId: Int,
        newId: Int,
        now: Long,
        serverWallet: WalletResponse?
    ) {
        if (oldId == newId) return
        val local = walletDao.getWalletById(oldId) ?: return
        val merged = if (serverWallet != null) {
            val fromServer = serverWallet.toDomain().toLocalEntity(now, true)
            fromServer.copy(
                balance = fromServer.balance?.takeIf { it.isNotBlank() } ?: local.balance,
                initialBalance = fromServer.initialBalance.takeIf { it.isNotBlank() } ?: local.initialBalance
            )
        } else {
            local.copy(id = newId, isSynced = true, updatedAt = now)
        }
        walletDao.upsertWallet(merged.copy(id = newId, isSynced = true, updatedAt = now))
        transactionDao.reassignWalletId(oldId, newId)
        goalDao.reassignWalletId(oldId, newId)
        pendingOperationDao.remapWalletResourceIds(oldId, newId)
        walletDao.deleteWalletById(oldId)
        walletBalanceRecalculator.recalculateWalletBalance(newId)
        println("✅ OFFLINE_SYNC: Remapped local wallet $oldId -> server id $newId")
    }

    /**
     * Persists authoritative monthly savings from [SyncPushResponse.currentSavingsGoal] when the backend
     * sends it after recalculating (e.g. post-transaction push). Aligns [MonthlySavingsGoalEntity.savingsTxNetAnchor]
     * with the current local savings-wallet tx net so a later local recalc does not overwrite server [current_saved].
     */
    private suspend fun applyCurrentSavingsGoalFromPushResponse(response: SyncPushResponse, now: Long) {
        val sg = response.currentSavingsGoal ?: return
        val txNet = runCatching {
            transactionDao.sumSavingsWalletIncomeMinusExpenseForMonth(sg.year, sg.month)
        }.getOrElse { 0.0 }
        monthlySavingsGoalDao.upsertMonthlySavingsGoal(
            MonthlySavingsGoalEntity(
                id = sg.id,
                month = sg.month,
                year = sg.year,
                targetAmount = sg.target_amount,
                currentSaved = sg.current_saved,
                savingsTxNetAnchor = txNet,
                updatedAt = now,
                isSynced = true
            )
        )
        println(
            "📦 OFFLINE_SYNC: Applied current_savings_goal from push " +
                "(id=${sg.id}, year=${sg.year}, month=${sg.month}, current_saved=${sg.current_saved}, anchorTxNet=$txNet)"
        )
    }

    /**
     * Recomputes monthly savings [current_saved] from local Room transactions (savings wallets only).
     * Call after sync so UI matches pulled transaction data without changing the transactions table.
     */
    /**
     * Removes offline temp rows when an equivalent synced server row already exists
     * (e.g. after a failed id remap in an older app version).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun dedupeStaleOfflineTransactions() {
        val all = transactionDao.getTransactions()
        val pending = all.filter { it.id < 0 || !it.isSynced }
        if (pending.isEmpty()) return
        val synced = all.filter { it.id > 0 && it.isSynced }
        for (temp in pending) {
            val hasServerCopy = synced.any { server ->
                server.name.equals(temp.name, ignoreCase = true) &&
                    normalizeTransactionAmount(server.amount) == normalizeTransactionAmount(temp.amount) &&
                    server.walletId == temp.walletId &&
                    server.categoryId == temp.categoryId &&
                    server.type.equals(temp.type, ignoreCase = true)
            }
            if (hasServerCopy) {
                transactionDao.deleteTransactionById(temp.id)
                pendingOperationDao.removeAllPendingForResource("transaction", temp.id)
                walletBalanceRecalculator.recalculateWalletBalance(temp.walletId)
                println("🧹 OFFLINE_SYNC: Removed duplicate temp transaction id=${temp.id} name=${temp.name}")
            }
        }
    }

    suspend fun recalculateMonthlySavingsFromLocalTransactions() {
        println("SAVINGS_RECALC: OfflineSyncOrchestrator.recalculateMonthlySavingsFromLocalTransactions()")
        monthlySavingsLocalRecalculator.recalculateAllCachedMonths()
    }
}