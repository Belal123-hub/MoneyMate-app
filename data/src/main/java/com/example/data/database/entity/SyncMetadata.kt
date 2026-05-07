package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey
    @ColumnInfo(name = "resource_name")
    val resourceName: String,
    @ColumnInfo(name = "last_sync_timestamp")
    val lastSyncTimestamp: Long
)
