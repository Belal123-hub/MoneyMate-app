package com.example.moneymate.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PendingSyncIndicator(
    isSynced: Boolean,
    modifier: Modifier = Modifier,
    /** Use on wallet cards / colored backgrounds so the badge stays visible. */
    highContrast: Boolean = false
) {
    if (isSynced) return

    val backgroundColor = if (highContrast) {
        Color(0xFFF59E0B)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val iconTint = if (highContrast) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Pending sync",
            tint = iconTint,
            modifier = Modifier.size(15.dp)
        )
    }
}
