package com.example.moneymate.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.offline.OfflineSyncOrchestrator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncOrchestrator: OfflineSyncOrchestrator by inject()

    override suspend fun doWork(): Result {
        return try {
            syncOrchestrator.runSync("default")
                .fold(
                    onSuccess = { Result.success() },
                    onFailure = { Result.retry() }
                )
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
