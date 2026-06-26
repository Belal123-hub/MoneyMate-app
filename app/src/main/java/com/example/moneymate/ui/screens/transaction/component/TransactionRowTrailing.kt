package com.example.moneymate.ui.screens.transaction.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moneymate.ui.offline.PendingSyncIndicator

@Composable
fun TransactionRowTrailing(
    amountText: String,
    amountColor: Color,
    isSynced: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = amountText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = amountColor
        )
        if (!isSynced) {
            PendingSyncIndicator(isSynced = false)
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete transaction",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
