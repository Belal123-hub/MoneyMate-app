package com.example.moneymate.ui.offline

data class SyncUiState(
    val status: SyncStatus = SyncStatus.IDLE,
    val isOffline: Boolean = false,
    val errorMessage: String? = null
)

/** Offline always wins over a loading/syncing overlay in the status chip. */
fun displaySyncStatus(syncStatus: SyncStatus, isContentLoading: Boolean): SyncStatus {
    if (syncStatus == SyncStatus.OFFLINE) return SyncStatus.OFFLINE
    if (isContentLoading) return SyncStatus.SYNCING
    return syncStatus
}
