package com.example.data.offline.repository

import com.example.data.database.dao.CategoryDao
import com.example.data.database.mapper.toDomain
import com.example.data.database.mapper.toLocalEntity
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.domain.category.CategoryRepository
import com.example.domain.category.model.Category
import com.example.domain.category.model.CategoryCreateRequest

class OfflineCategoryRepositoryImpl(
    private val remoteRepository: CategoryRepository,
    private val categoryDao: CategoryDao,
    private val syncOrchestrator: OfflineSyncOrchestrator
) : CategoryRepository {
    override suspend fun getCategories(): Result<List<Category>> {
        val local = categoryDao.getCategories().map { it.toDomain() }
        val remote = remoteRepository.getCategories()
        remote.onSuccess {
            categoryDao.upsertCategories(it.map { category -> category.toLocalEntity(System.currentTimeMillis(), true) })
            syncOrchestrator.runSync("categories")
        }
        return when {
            local.isNotEmpty() -> Result.success(local)
            remote.isSuccess -> remote
            else -> Result.success(emptyList())
        }
    }

    override suspend fun createCategory(request: CategoryCreateRequest): Result<Category> =
        remoteRepository.createCategory(request)

    override suspend fun getIncomeCategories(): Result<List<Category>> {
        val local = categoryDao.getCategoriesByType("income").map { it.toDomain() }
        val remote = remoteRepository.getIncomeCategories()
        return when {
            local.isNotEmpty() -> Result.success(local)
            remote.isSuccess -> remote
            else -> Result.success(emptyList())
        }
    }

    override suspend fun getExpenseCategories(): Result<List<Category>> {
        val local = categoryDao.getCategoriesByType("expense").map { it.toDomain() }
        val remote = remoteRepository.getExpenseCategories()
        return when {
            local.isNotEmpty() -> Result.success(local)
            remote.isSuccess -> remote
            else -> Result.success(emptyList())
        }
    }

    override suspend fun deleteCategory(categoryId: Int): Result<Boolean> =
        remoteRepository.deleteCategory(categoryId)
}
