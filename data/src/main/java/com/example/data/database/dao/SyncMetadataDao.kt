package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.SyncMetadata

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE resource_name = :resourceName LIMIT 1")
    suspend fun getByResourceName(resourceName: String): SyncMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadata)
}
