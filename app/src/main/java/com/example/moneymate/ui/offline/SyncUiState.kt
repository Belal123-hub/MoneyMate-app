package com.example.moneymate.ui.offline

data class SyncUiState(
    val status: SyncStatus = SyncStatus.IDLE,
    val isOffline: Boolean = false,
    val errorMessage: String? = null
)
