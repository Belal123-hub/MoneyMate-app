package com.example.moneymate.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier
) {
    val (icon, label, bgColor) = when (status) {
        SyncStatus.IDLE -> Triple(Icons.Default.CloudQueue, "Synced", MaterialTheme.colorScheme.secondaryContainer)
        SyncStatus.SYNCING -> Triple(Icons.Default.Sync, "Syncing...", MaterialTheme.colorScheme.tertiaryContainer)
        SyncStatus.OFFLINE -> Triple(Icons.Default.CloudOff, "Offline", MaterialTheme.colorScheme.errorContainer)
        SyncStatus.ERROR -> Triple(Icons.Default.Error, "Sync error", MaterialTheme.colorScheme.errorContainer)
    }

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.rotate(if (status == SyncStatus.SYNCING) 12f else 0f),
            tint = Color.Unspecified
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
