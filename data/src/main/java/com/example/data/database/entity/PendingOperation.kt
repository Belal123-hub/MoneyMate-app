package com.example.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_operations")
data class PendingOperation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "resource_type")
    val resourceType: String,
    @ColumnInfo(name = "resource_id")
    val resourceId: Int,
    @ColumnInfo(name = "operation_type")
    val operationType: String,
    val payload: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)
