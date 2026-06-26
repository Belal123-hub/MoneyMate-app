package com.example.moneymate.ui.screens.transaction.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.*
import com.example.domain.transaction.model.SpendingForecastData

@Composable
fun SpendingForecastChart(
    spendingForecast: SpendingForecastData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp),
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
                        text = "Spending Forecast",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1C1E)
                    )
                    Text(
                        text = "End of month prediction",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                // Confidence badge
                val confidenceColor = when (spendingForecast.confidence) {
                    "high" -> Color(0xFF4ECDC4)
                    "medium" -> Color(0xFFFFB84D)
                    else -> Color(0xFFFF6B6B)
                }

                Surface(
                    color = confidenceColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = spendingForecast.confidence.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = confidenceColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Key metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$${String.format("%.2f", spendingForecast.spentSoFar)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                    Text(
                        text = "Spent So Far",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$${String.format("%.2f", spendingForecast.forecastEndOfMonth)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1C1E)
                    )
                    Text(
                        text = "Predicted Total",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$${String.format("%.2f", spendingForecast.dailyAverageSpending)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4ECDC4)
                    )
                    Text(
                        text = "Daily Average",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Simple projection chart
            if (spendingForecast.daysInMonth > 0) {
                val projectionPoints = List(spendingForecast.daysInMonth) { day ->
                    val projectedDaily = spendingForecast.dailyAverageSpending * (day + 1)
                    Point(
                        x = day.toFloat(),
                        y = projectedDaily.toFloat()
                    )
                }

                val line = Line(
                    dataPoints = projectionPoints,
                    lineStyle = LineStyle(
                        color = Color(0xFFFF6B6B)
                        // lineWidth removed as it doesn't exist in this API version
                    ),
                    intersectionPoint = IntersectionPoint(
                        color = Color(0xFFFF6B6B),
                        radius = 2.dp  // Changed from 2f to 2.dp
                    )
                )

                // Create x-axis data
                val xAxisData = AxisData.Builder()
                    .axisStepSize(40.dp)
                    .steps(spendingForecast.daysInMonth - 1)
                    .labelData { i -> "${i + 1}" }
                    .labelAndAxisLinePadding(15.dp)
                    .build()

                // Create y-axis data
                val yAxisData = AxisData.Builder()
                    .steps(4)
                    .labelData { i ->
                        val maxVal = spendingForecast.forecastEndOfMonth.toFloat()
                        "$${(maxVal * (i + 1) / 5 / 1000).toInt()}K"
                    }
                    .labelAndAxisLinePadding(20.dp)
                    .build()

                // Create line plot data
                val linePlotData = LinePlotData(
                    lines = listOf(line)
                )

                // Create line chart data with the correct structure
                val lineChartData = LineChartData(
                    linePlotData = linePlotData,
                    xAxisData = xAxisData,
                    yAxisData = yAxisData,
                    backgroundColor = Color.Transparent
                )

                LineChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    lineChartData = lineChartData
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Days info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Day ${spendingForecast.daysElapsed} of ${spendingForecast.daysInMonth}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                val remaining = spendingForecast.forecastEndOfMonth - spendingForecast.spentSoFar
                Text(
                    text = "Remaining forecast: $${String.format("%.2f", remaining)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (remaining > 0) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                )
            }
        }
    }
}