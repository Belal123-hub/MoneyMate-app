package com.example.moneymate.ui.components

import androidx.compose.ui.graphics.Color
import com.example.domain.transaction.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

fun formatTransactionAddedTime(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    val dateTime = parseToLocalDateTime(createdAt) ?: return createdAt
    val date = dateTime.toLocalDate()
    val now = LocalDate.now()
    val time = dateTime.format(timeFormatter)
    return when (date) {
        now -> "Today, $time"
        now.minusDays(1) -> "Yesterday, $time"
        else -> "${date.format(dateFormatter)} · $time"
    }
}

private fun parseToLocalDateTime(raw: String): LocalDateTime? {
    val trimmed = raw.trim()
    val zone = ZoneId.systemDefault()
    try {
        return Instant.parse(trimmed).atZone(zone).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        // continue
    }
    try {
        return java.time.OffsetDateTime.parse(trimmed).atZoneSameInstant(zone).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        // continue
    }
    val localPatterns = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault()),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    )
    val normalized = trimmed.replace("Z", "").substringBefore('+')
    for (formatter in localPatterns) {
        try {
            return LocalDateTime.parse(normalized, formatter)
        } catch (_: DateTimeParseException) {
            // try next
        }
    }
    try {
        return LocalDate.parse(trimmed.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
    } catch (_: DateTimeParseException) {
        return null
    }
}

fun transactionAmountColor(type: String): Color =
    when (type.trim().lowercase(Locale.getDefault())) {
        "income" -> Color(0xFF10B981)
        "expense" -> Color(0xFFEF4444)
        else -> Color(0xFF666666)
    }

fun formatTransactionAmountText(
    transaction: TransactionEntity,
    currencySymbol: String = "$"
): String =
    when (transaction.type.trim().lowercase(Locale.getDefault())) {
        "income" -> "+$currencySymbol${transaction.amount}"
        "expense" -> "-$currencySymbol${transaction.amount}"
        else -> "$currencySymbol${transaction.amount}"
    }
