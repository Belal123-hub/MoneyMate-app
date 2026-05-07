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

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun removeById(id: Long)

    @Query("UPDATE pending_operations SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)
}
