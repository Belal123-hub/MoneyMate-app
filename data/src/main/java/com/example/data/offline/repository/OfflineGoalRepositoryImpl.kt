package com.example.data.offline.repository

import com.example.data.database.dao.GoalDao
import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.mapper.toDomain
import com.example.data.database.mapper.toLocalEntity
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.domain.goal.GoalRepository
import com.example.domain.goal.model.Goal
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

class OfflineGoalRepositoryImpl(
    private val remoteRepository: GoalRepository,
    private val goalDao: GoalDao,
    private val pendingOperationDao: PendingOperationDao,
    private val syncOrchestrator: OfflineSyncOrchestrator
) : GoalRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun mergeRemoteGoalsIntoRoom(serverGoals: List<Goal>, now: Long) {
        val toUpsert = serverGoals.mapNotNull { goal ->
            val local = goalDao.getGoalById(goal.id)
            if (local != null && !local.isSynced) {
                println("📱 OFFLINE_GOAL: skip remote overwrite unsynced id=${goal.id}")
                null
            } else {
                goal.toLocalEntity(now, true)
            }
        }
        if (toUpsert.isNotEmpty()) {
            goalDao.upsertGoals(toUpsert)
        }
    }

    private fun buildGoalPayload(
        title: String,
        goalAmount: Double,
        currency: String,
        description: String?,
        deadline: LocalDate?,
        imagePath: String?,
        currentAmount: Double? = null,
        walletId: Int = 0
    ): String {
        // Backend DeserializeResource<T> is case-insensitive; send common camelCase keys.
        // Include the essential fields required to create/update a Goal.
        val obj = buildJsonObject {
            put("title", title)
            put("goalAmount", goalAmount)
            put("currency", currency)
            put("description", description ?: "")
            put("deadline", deadline?.toString() ?: "")
            put("imageUrl", imagePath ?: "")
            put("walletId", walletId)
            put("currentAmount", currentAmount ?: 0.0)
        }
        return json.encodeToString(obj)
    }

    override suspend fun getGoal(goalId: Int): Result<Goal> {
        val remote = remoteRepository.getGoal(goalId)
        val now = System.currentTimeMillis()
        remote.onSuccess { goal ->
            val local = goalDao.getGoalById(goalId)
            if (local != null && !local.isSynced) {
                println("📱 OFFLINE_GOAL: skip getGoal remote overwrite unsynced id=$goalId")
                return@onSuccess
            }
            goalDao.upsertGoal(goal.toLocalEntity(now, true))
        }
        val merged = goalDao.getGoalById(goalId)?.toDomain()
        return when {
            merged != null -> Result.success(merged)
            remote.isSuccess -> remote
            else -> Result.failure(remote.exceptionOrNull() ?: Exception("getGoal failed"))
        }
    }

    override suspend fun getGoals(): Result<List<Goal>> {
        val remote = remoteRepository.getGoals()
        val now = System.currentTimeMillis()
        remote.onSuccess {
            mergeRemoteGoalsIntoRoom(it, now)
            syncOrchestrator.runSync("goals")
        }
        return when {
            remote.isSuccess -> Result.success(goalDao.getGoals().map { g -> g.toDomain() })
            else -> {
                val local = goalDao.getGoals().map { it.toDomain() }
                if (local.isNotEmpty()) Result.success(local)
                else Result.success(emptyList())
            }
        }
    }

    override suspend fun createGoal(
        title: String,
        goalAmount: Double,
        currency: String,
        description: String?,
        deadline: LocalDate?,
        imagePath: String?
    ): Result<Goal> {
        val remote = remoteRepository.createGoal(title, goalAmount, currency, description, deadline, imagePath)
        if (remote.isSuccess) {
            val created = remote.getOrThrow()
            goalDao.upsertGoal(created.toLocalEntity(System.currentTimeMillis(), true))
            return remote
        }

        val offlineGoal = Goal(
            id = -System.currentTimeMillis().toInt(),
            title = title,
            description = description,
            image = imagePath,
            deadline = deadline,
            goalAmount = goalAmount,
            amountSaved = 0.0,
            walletId = 0,
            currency = currency
        )
        goalDao.upsertGoal(offlineGoal.toLocalEntity(System.currentTimeMillis(), false))
        val payload = buildGoalPayload(
            title = title,
            goalAmount = goalAmount,
            currency = currency,
            description = description,
            deadline = deadline,
            imagePath = imagePath,
            currentAmount = 0.0,
            walletId = 0
        )
        syncOrchestrator.enqueueOperation("goal", offlineGoal.id, "create", payload)
        return Result.success(offlineGoal)
    }

    override suspend fun updateGoal(
        goalId: Int,
        title: String?,
        description: String?,
        deadline: LocalDate?,
        goalAmount: Double?,
        currentAmount: Double?
    ): Result<Goal> {
        val remote = remoteRepository.updateGoal(goalId, title, description, deadline, goalAmount, currentAmount)
        if (remote.isSuccess) {
            val updated = remote.getOrThrow()
            goalDao.upsertGoal(updated.toLocalEntity(System.currentTimeMillis(), true))
            return remote
        }
        val local = goalDao.getGoalById(goalId)
        if (local != null) {
            val updatedLocal = local.copy(
                title = title ?: local.title,
                description = description ?: local.description,
                deadline = deadline ?: local.deadline,
                goalAmount = goalAmount ?: local.goalAmount,
                amountSaved = currentAmount ?: local.amountSaved,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            goalDao.upsertGoal(updatedLocal)
            val payload = buildGoalPayload(
                title = updatedLocal.title,
                goalAmount = updatedLocal.goalAmount,
                currency = updatedLocal.currency,
                description = updatedLocal.description,
                deadline = updatedLocal.deadline,
                imagePath = updatedLocal.image,
                currentAmount = updatedLocal.amountSaved,
                walletId = updatedLocal.walletId
            )
            syncOrchestrator.enqueueOperation("goal", goalId, "update", payload)
            return Result.success(updatedLocal.toDomain())
        }
        return remote
    }

    override suspend fun deleteGoal(goalId: Int): Result<Boolean> {
        val remote = remoteRepository.deleteGoal(goalId)
        if (remote.isSuccess) {
            pendingOperationDao.removeAllPendingForResource("goal", goalId)
            goalDao.deleteGoalById(goalId)
            return remote
        }
        if (goalId < 0) {
            pendingOperationDao.removeAllPendingForResource("goal", goalId)
            goalDao.deleteGoalById(goalId)
            println("📤 OFFLINE_GOAL: removed pending ops + local temp goal id=$goalId")
            return Result.success(true)
        }
        pendingOperationDao.removeAllPendingForResource("goal", goalId)
        goalDao.deleteGoalById(goalId)
        syncOrchestrator.enqueueOperation("goal", goalId, "delete")
        println("📤 OFFLINE_GOAL: enqueued delete id=$goalId")
        return Result.success(true)
    }
}
