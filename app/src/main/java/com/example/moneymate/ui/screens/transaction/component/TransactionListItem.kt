package com.example.moneymate.ui.screens.transaction.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.transaction.model.TransactionEntity
import com.example.moneymate.ui.components.formatTransactionAddedTime
import com.example.moneymate.ui.components.formatTransactionAmountText
import com.example.moneymate.ui.components.transactionAmountColor

@Composable
fun TransactionListItem(
    transaction: TransactionEntity,
    isSynced: Boolean = true,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = transaction.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = formatTransactionAddedTime(transaction.createdAt),
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
        }

        TransactionRowTrailing(
            amountText = formatTransactionAmountText(transaction),
            amountColor = transactionAmountColor(transaction.type),
            isSynced = isSynced,
            onDelete = onDelete
        )
    }
}

