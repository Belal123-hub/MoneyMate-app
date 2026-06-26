package com.example.moneymate.ui.screens.transaction.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.transaction.model.SavingsSuggestion
import com.example.domain.transaction.model.SavingsSuggestionData

@Composable
fun AISuggestionsCard(
    suggestionsData: SavingsSuggestionData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI Insights",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1C1E)
                    )
                    Text(
                        text = "Personalized recommendations",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = Color(0xFF70C1B3),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Suggestions list - Using Column with verticalScroll instead of LazyColumn
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                suggestionsData.suggestions.forEach { suggestion ->
                    SuggestionItem(suggestion = suggestion)
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(suggestion: SavingsSuggestion) {
    val (icon, backgroundColor, borderColor) = when (suggestion.type) {
        "alert" -> Triple(
            Icons.Default.Warning,
            Color(0xFFFF6B6B).copy(alpha = 0.1f),
            Color(0xFFFF6B6B)
        )
        "suggestion" -> Triple(
            Icons.Default.Lightbulb,
            Color(0xFF4ECDC4).copy(alpha = 0.1f),
            Color(0xFF4ECDC4)
        )
        "opportunity" -> Triple(
            Icons.Default.TrendingUp,
            Color(0xFFFFB84D).copy(alpha = 0.1f),
            Color(0xFFFFB84D)
        )
        else -> Triple(
            Icons.Default.Info,
            Color.Gray.copy(alpha = 0.1f),
            Color.Gray
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icon
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = borderColor.copy(alpha = 0.2f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = suggestion.type,
                    tint = borderColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = suggestion.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1E)
            )
            Text(
                text = suggestion.message,
                fontSize = 12.sp,
                color = Color.Gray
            )

            // Amount if present
            suggestion.amount?.let { amount ->
                Text(
                    text = "$${String.format("%.2f", amount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = borderColor
                )
            }
        }
    }
}
