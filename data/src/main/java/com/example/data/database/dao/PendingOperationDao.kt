package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.PendingOperation

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operations ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingOperation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: PendingOperation): Long

    @Query("UPDATE pending_operations SET payload = :payload WHERE id = :id")
    suspend fun updatePayload(id: Long, payload: String?)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun removeById(id: Long)

    @Query("UPDATE pending_operations SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query(
        "UPDATE pending_operations SET resource_id = :newId WHERE resource_id = :oldId AND resource_type = 'wallet'"
    )
    suspend fun remapWalletResourceIds(oldId: Int, newId: Int)

    @Query(
        "UPDATE pending_operations SET resource_id = :newId WHERE resource_id = :oldId AND resource_type = 'goal'"
    )
    suspend fun remapGoalResourceIds(oldId: Int, newId: Int)

    @Query(
        "UPDATE pending_operations SET resource_id = :newId WHERE resource_id = :oldId AND resource_type = 'transaction'"
    )
    suspend fun remapTransactionResourceIds(oldId: Int, newId: Int)

    @Query("DELETE FROM pending_operations WHERE resource_type = :resourceType AND resource_id = :resourceId")
    suspend fun removeAllPendingForResource(resourceType: String, resourceId: Int)

    @Query(
        "DELETE FROM pending_operations WHERE resource_type = :resourceType AND resource_id = :resourceId AND operation_type = :operationType"
    )
    suspend fun removeByResourceAndOperation(resourceType: String, resourceId: Int, operationType: String)
}
