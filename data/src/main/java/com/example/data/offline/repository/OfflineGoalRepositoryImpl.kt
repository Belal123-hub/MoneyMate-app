package com.example.data.offline.repository

import com.example.data.database.dao.GoalDao
import com.example.data.database.mapper.toDomain
import com.example.data.database.mapper.toLocalEntity
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.domain.goal.GoalRepository
import com.example.domain.goal.model.Goal
import java.time.LocalDate

class OfflineGoalRepositoryImpl(
    private val remoteRepository: GoalRepository,
    private val goalDao: GoalDao,
    private val syncOrchestrator: OfflineSyncOrchestrator
) : GoalRepository {
    override suspend fun getGoal(goalId: Int): Result<Goal> {
        val local = goalDao.getGoalById(goalId)?.toDomain()
        val remote = remoteRepository.getGoal(goalId)
        remote.onSuccess {
            goalDao.upsertGoal(it.toLocalEntity(System.currentTimeMillis(), true))
        }
        return local?.let { Result.success(it) } ?: remote
    }

    override suspend fun getGoals(): Result<List<Goal>> {
        val local = goalDao.getGoals().map { it.toDomain() }
        val remote = remoteRepository.getGoals()
        remote.onSuccess {
            goalDao.upsertGoals(it.map { goal -> goal.toLocalEntity(System.currentTimeMillis(), true) })
            syncOrchestrator.runSync("goals")
        }
        return when {
            local.isNotEmpty() -> Result.success(local)
            remote.isSuccess -> remote
            else -> Result.success(emptyList())
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
        syncOrchestrator.enqueueOperation("goal", offlineGoal.id, "create")
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
            syncOrchestrator.enqueueOperation("goal", goalId, "update")
            return Result.success(updatedLocal.toDomain())
        }
        return remote
    }

    override suspend fun deleteGoal(goalId: Int): Result<Boolean> {
        val remote = remoteRepository.deleteGoal(goalId)
        if (remote.isSuccess) {
            goalDao.deleteGoalById(goalId)
            return remote
        }
        goalDao.deleteGoalById(goalId)
        syncOrchestrator.enqueueOperation("goal", goalId, "delete")
        return Result.success(true)
    }
}
