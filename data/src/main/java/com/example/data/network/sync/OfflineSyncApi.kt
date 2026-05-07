package com.example.data.network.sync

import com.example.data.network.sync.model.SyncPullResponse
import com.example.data.network.sync.model.SyncPushRequest
import com.example.data.network.sync.model.SyncPushResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OfflineSyncApi {
    @POST("api/sync/push")
    suspend fun pushPendingOperations(@Body request: SyncPushRequest): SyncPushResponse

    @GET("api/sync/changes")
    suspend fun pullChanges(
        @Query("last_sync") lastSync: Long
    ): SyncPullResponse
}
