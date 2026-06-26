package com.example.data.offline.repository

import com.example.data.database.dao.TagDao
import com.example.data.database.entity.TagEntity
import com.example.data.database.mapper.toDomain
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.domain.tag.TagRepository
import com.example.domain.tag.model.CreateTag
import com.example.domain.tag.model.Tag
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineTagRepositoryImpl(
    private val remoteRepository: TagRepository,
    private val tagDao: TagDao,
    private val syncOrchestrator: OfflineSyncOrchestrator
) : TagRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getTags(): Result<List<Tag>> {
        val local = tagDao.getTags().map { it.toDomain() }
        val remote = remoteRepository.getTags()
        remote.onSuccess { tags ->
            val now = System.currentTimeMillis()
            val entities = tags.map { tag ->
                TagEntity(
                    id = tag.id,
                    name = tag.name,
                    userId = tag.userId,
                    updatedAt = now,
                    isSynced = true
                )
            }
            try {
                tagDao.upsertTags(entities)
                // Trigger a sync cycle (pull may deliver deletes/updates)
                syncOrchestrator.runSync("tags")
            } catch (e: Exception) {
                println("❌ Tag Room cache failed (getTags): ${e.message}")
                e.printStackTrace()
            }
        }
        return when {
            local.isNotEmpty() -> Result.success(local)
            remote.isSuccess -> remote
            else -> Result.success(emptyList())
        }
    }

    override suspend fun createTag(createTag: CreateTag): Result<Tag> {
        val remote = remoteRepository.createTag(createTag)
        if (remote.isSuccess) {
            val created = remote.getOrThrow()
            tagDao.upsertTag(
                TagEntity(
                    id = created.id,
                    name = created.name,
                    userId = created.userId,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = true
                )
            )
            return remote
        }

        // Offline fallback
        val localTag = Tag(
            id = -System.currentTimeMillis().toInt(),
            name = createTag.name.trim(),
            userId = 0
        )
        val payload = json.encodeToString(mapOf("name" to localTag.name))
        tagDao.upsertTag(
            TagEntity(
                id = localTag.id,
                name = localTag.name,
                userId = null,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
        )
        syncOrchestrator.enqueueOperation("tag", localTag.id, "create", payload)
        return Result.success(localTag)
    }

    override suspend fun deleteTag(id: Int): Result<Unit> {
        val remote = remoteRepository.deleteTag(id)
        if (remote.isSuccess) {
            tagDao.deleteTagById(id)
            return remote
        }

        // Offline fallback
        tagDao.deleteTagById(id)
        syncOrchestrator.enqueueOperation("tag", id, "delete")
        return Result.success(Unit)
    }
}

