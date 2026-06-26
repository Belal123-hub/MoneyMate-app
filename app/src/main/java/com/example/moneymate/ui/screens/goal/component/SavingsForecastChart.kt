package com.example.moneymate.ui.screens.goal.component

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
import com.example.domain.transaction.model.SavingsForecastData

@Composable
fun SavingsForecastChart(
    savingsForecast: SavingsForecastData,
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
                        text = "Savings Forecast",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1C1E)
                    )
                    Text(
                        text = "${savingsForecast.monthsAhead} month projection",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Current savings highlight
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4ECDC4).copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$${String.format("%.2f", savingsForecast.currentSavings)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ECDC4)
                        )
                        Text(
                            text = "Current Savings",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = Color(0xFF4ECDC4).copy(alpha = 0.3f)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$${String.format("%.2f", savingsForecast.averageMonthlySaving)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1C1E)
                        )
                        Text(
                            text = "Monthly Average",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Projection chart
            if (savingsForecast.projections.isNotEmpty()) {
                val projectionPoints = savingsForecast.projections.mapIndexed { index, projection ->
                    Point(
                        x = index.toFloat(),
                        y = projection.cumulativeTotal.toFloat()
                    )
                }

                val line = Line(
                    dataPoints = projectionPoints,
                    lineStyle = LineStyle(color = Color(0xFF4ECDC4)),
                    intersectionPoint = IntersectionPoint(
                        color = Color(0xFF4ECDC4),
                        radius = 3.dp
                    )
                )

                val maxCumulative = savingsForecast.projections.maxOfOrNull { it.cumulativeTotal } ?: 0.0
                val steps = 4

                // Create x-axis data
                val xAxisData = AxisData.Builder()
                    .axisStepSize(50.dp)
                    .steps(maxOf(0, savingsForecast.projections.size - 1))
                    .labelData { i ->
                        if (i < savingsForecast.projections.size) {
                            savingsForecast.projections[i].month.take(3)
                        } else ""
                    }
                    .labelAndAxisLinePadding(15.dp)
                    .build()

                // Create y-axis data
                val yAxisData = AxisData.Builder()
                    .steps(steps)
                    .labelData { i ->
                        val stepValue = maxCumulative * (i + 1) / (steps + 1)
                        if (stepValue >= 1000) {
                            "$${(stepValue / 1000).toInt()}K"
                        } else {
                            "$${stepValue.toInt()}"
                        }
                    }
                    .labelAndAxisLinePadding(20.dp)
                    .build()

                // Create line plot data with the line
                val linePlotData = LinePlotData(
                    lines = listOf(line)
                )

                // Create line chart data
                val lineChartData = LineChartData(
                    linePlotData = linePlotData,
                    xAxisData = xAxisData,
                    yAxisData = yAxisData,
                    backgroundColor = Color.Transparent
                )

                LineChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    lineChartData = lineChartData
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Final projection
                val finalProjection = savingsForecast.projections.lastOrNull()
                finalProjection?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF70C1B3).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Projected Total (${it.month})",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "$${String.format("%.2f", it.cumulativeTotal)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF70C1B3)
                                )
                            }

                            Text(
                                text = "+$${String.format("%.2f", it.projectedAmount)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4ECDC4)
                            )
                        }
                    }
                }
            }
        }
    }
}