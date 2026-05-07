package com.example.data.offline.repository

import com.example.data.database.dao.CategoryDao
import com.example.data.database.entity.CategoryEntity
import com.example.data.database.mapper.toDomain
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.domain.category.CategoryRepository
import com.example.domain.category.model.Category
import com.example.domain.category.model.CategoryCreateRequest

class OfflineCategoryRepositoryImpl(
    private val remoteRepository: CategoryRepository,
    private val categoryDao: CategoryDao,
    private val syncOrchestrator: OfflineSyncOrchestrator
) : CategoryRepository {

    private fun normalizeType(raw: String): String = raw.trim().lowercase()

    override suspend fun getCategories(): Result<List<Category>> {
        val local = categoryDao.getCategories().map { it.toDomain() }
        val remote = remoteRepository.getCategories()
        remote.onSuccess { categories ->
            val entities = categories.map { category ->
                CategoryEntity(
                    id = category.id,
                    name = category.name,
                    type = normalizeType(category.type),
                    color = category.color,
                    icon = category.icon,
                    userId = category.userId,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = true
                )
            }
            println("📦 Saving ${entities.size} categories to Room")
            try {
                categoryDao.upsertCategories(entities)
            } catch (e: Exception) {
                println("❌ Category Room save failed (getCategories): ${e.message}")
                e.printStackTrace()
            }

            val afterSave = categoryDao.getCategories()
            println("📦 After save, Room has ${afterSave.size} categories")
            afterSave.forEach { println("   Saved: ${it.name} (${it.type})") }
        }
        return if (local.isNotEmpty()) Result.success(local) else remote
    }

    override suspend fun createCategory(request: CategoryCreateRequest): Result<Category> =
        remoteRepository.createCategory(request)

    override suspend fun getIncomeCategories(): Result<List<Category>> {
        val local = categoryDao.getCategoriesByType("income").map { it.toDomain() }
        println("📦 getIncomeCategories - local size = ${local.size}")

        if (local.isNotEmpty()) {
            println("📦 Returning ${local.size} income categories from CACHE")
            return Result.success(local)
        }

        println("📦 No cached income categories, fetching from remote")
        val remote = remoteRepository.getIncomeCategories()
        remote.onSuccess { categories ->
            val entities = categories.map { category ->
                CategoryEntity(
                    id = category.id,
                    name = category.name,
                    type = normalizeType(category.type),
                    color = category.color,
                    icon = category.icon,
                    userId = category.userId,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = true
                )
            }
            println("📦 Saving ${entities.size} income categories to Room")
            try {
                categoryDao.upsertCategories(entities)
            } catch (e: Exception) {
                println("❌ Category Room save failed (getIncomeCategories): ${e.message}")
                e.printStackTrace()
            }

            val verify = categoryDao.getCategoriesByType("income")
            println("📦 Verification - income categories in Room after save: ${verify.size}")
        }
        remote.onFailure { error ->
            println("📦 ERROR: ${error.message}")
        }
        return remote
    }

    override suspend fun getExpenseCategories(): Result<List<Category>> {
        val local = categoryDao.getCategoriesByType("expense").map { it.toDomain() }
        println("📦 getExpenseCategories - local size = ${local.size}")

        if (local.isNotEmpty()) {
            println("📦 Returning ${local.size} expense categories from CACHE")
            return Result.success(local)
        }

        println("📦 No cached expense categories, fetching from remote")
        val remote = remoteRepository.getExpenseCategories()
        remote.onSuccess { categories ->
            val entities = categories.map { category ->
                CategoryEntity(
                    id = category.id,
                    name = category.name,
                    type = normalizeType(category.type),
                    color = category.color,
                    icon = category.icon,
                    userId = category.userId,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = true
                )
            }
            println("📦 Saving ${entities.size} expense categories to Room")
            try {
                categoryDao.upsertCategories(entities)
            } catch (e: Exception) {
                println("❌ Category Room save failed (getExpenseCategories): ${e.message}")
                e.printStackTrace()
            }

            val verify = categoryDao.getCategoriesByType("expense")
            println("📦 Verification - expense categories in Room after save: ${verify.size}")
            verify.forEach { println("   Verified: ${it.name}") }
        }
        remote.onFailure { error ->
            println("📦 ERROR: ${error.message}")
        }
        return remote
    }

    override suspend fun deleteCategory(categoryId: Int): Result<Boolean> =
        remoteRepository.deleteCategory(categoryId)
}