package com.example.moneymate.ui.offline

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun OfflineSnackbarHost(
    isOffline: Boolean,
    snackbarHostState: SnackbarHostState
) {
    LaunchedEffect(isOffline) {
        if (isOffline) {
            snackbarHostState.showSnackbar(
                message = "You are offline. Changes will sync when connection is restored."
            )
        }
    }

    SnackbarHost(hostState = snackbarHostState)
}
