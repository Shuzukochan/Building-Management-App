package com.app.buildingmanagement.fragment.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChartData(
    val label: String,
    val value: Float,
    val color: Color = Color(0xFF2196F3)
)

@Composable
fun ComposeBarChart(
    data: List<ChartData>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF2196F3),
    backgroundColor: Color = Color.White,
    chartHeight: Dp = 300.dp,
    showValues: Boolean = true,
    animationDuration: Int = 1000,
    showGrid: Boolean = true,
    gradientBars: Boolean = true
) {
    val animatedValues = remember(data) {
        data.map { Animatable(0f) }
    }
    
    // Animate chart bars
    LaunchedEffect(data) {
        delay(100) // Small delay for better UX
        animatedValues.forEachIndexed { index, animatable ->
            launch {
                animatable.animateTo(
                    targetValue = data.getOrNull(index)?.value ?: 0f,
                    animationSpec = tween(durationMillis = animationDuration)
                )
            }
        }
    }
    
    // Nếu tất cả value đều là 0, ép maxValue = 1f để luôn vẽ chart
    val allZero = data.all { it.value == 0f }

    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        // Chart area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawBarChart(
                    data = data,
                    animatedValues = animatedValues.map { it.value },
                    barColor = barColor,
                    showValues = showValues,
                    showGrid = showGrid,
                    gradientBars = gradientBars
                )
            }
            // Nếu tất cả value đều là 0, hiển thị thông báo
            if (allZero) {
                Text(
                    text = "Không có dữ liệu",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun DrawScope.drawBarChart(
    data: List<ChartData>,
    animatedValues: List<Float>,
    barColor: Color,
    showValues: Boolean,
    showGrid: Boolean,
    gradientBars: Boolean
) {
    if (data.isEmpty()) return
    
    val availableWidth = size.width
    val availableHeight = size.height - 40.dp.toPx() // Reserve space for values
    
    // Calculate max/min values for proper scaling
    val maxValue = data.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    val minValue = 0f

    // Tính toán bar width dựa trên available space và số lượng data points
    val spacing = 12.dp.toPx() // Fixed spacing between bars
    val totalSpacing = spacing * (data.size - 1) + (spacing * 2) // spaces between bars + margins
    val calculatedBarWidth = (availableWidth - totalSpacing) / data.size
    val finalBarWidth = maxOf(calculatedBarWidth, 24.dp.toPx()) // Minimum 24dp width
    
    val valueRange = maxValue - minValue
    if (valueRange <= 0) return
    
    // Draw grid lines if enabled
    if (showGrid) {
        drawGridLines(availableHeight)
    }
    
    data.forEachIndexed { index, chartData ->
        val animatedValue = animatedValues.getOrNull(index) ?: 0f
        val barHeight = (animatedValue / valueRange) * availableHeight
        
        val startX = spacing + (index * (finalBarWidth + spacing))
        val startY = availableHeight - barHeight + 20.dp.toPx()
        
        // Draw bar with rounded corners and optional gradient
        if (gradientBars && barHeight > 0) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(barColor.copy(alpha = 0.8f), barColor),
                    startY = startY,
                    endY = startY + barHeight
                ),
                topLeft = Offset(startX, startY),
                size = Size(finalBarWidth, barHeight),
                cornerRadius = CornerRadius(ChartConstants.BarCornerRadius.toPx())
            )
        } else {
            drawRoundRect(
                color = barColor,
                topLeft = Offset(startX, startY),
                size = Size(finalBarWidth, barHeight),
                cornerRadius = CornerRadius(ChartConstants.BarCornerRadius.toPx())
            )
        }
        
        // Draw value text
        if (showValues && animatedValue > 0) {
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = 11.sp.toPx()
                color = Color(0xFF333333).toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            val valueText = if (animatedValue < 10f) {
                String.format(java.util.Locale.getDefault(), "%.2f", animatedValue)
            } else if (animatedValue < 100f) {
                String.format(java.util.Locale.getDefault(), "%.1f", animatedValue)
            } else {
                animatedValue.toInt().toString()
            }
            drawContext.canvas.nativeCanvas.drawText(
                valueText,
                startX + finalBarWidth / 2,
                startY - 8.dp.toPx(),
                paint
            )
        }
        // Vẽ label trục hoành căn giữa cột
        val labelPaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            textSize = 11.sp.toPx()
            color = Color(0xFF666666).toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            chartData.label,
            startX + finalBarWidth / 2,
            size.height - 4.dp.toPx(), // sát đáy chart
            labelPaint
        )
    }
}

private fun DrawScope.drawGridLines(
    availableHeight: Float
) {
    val gridColor = Color(0xFFE0E0E0)
    val gridLineCount = 5
    val gridSpacing = availableHeight / gridLineCount
    
    for (i in 0..gridLineCount) {
        val y = 20.dp.toPx() + (i * gridSpacing)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

// Compose Chart for Electric/Water usage
@Composable
fun UsageChart(
    title: String,
    data: Map<String, Float>,
    fromDate: String,
    toDate: String,
    mode: String,
    chartColor: Color,
    chartHeight: Dp = ChartConstants.ChartHeight,
    modifier: Modifier = Modifier
) {
    val chartData = remember(data, fromDate, toDate, mode) {
        processChartData(data, fromDate, toDate, mode)
    }
    
    Column(modifier = modifier) {
        // Title (if provided)
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        
        // Chart
        ComposeBarChart(
            data = chartData.map { 
                ChartData(
                    label = it.first,
                    value = it.second,
                    color = chartColor
                )
            },
            barColor = chartColor,
            chartHeight = chartHeight,
            showValues = true,
            showGrid = true,
            gradientBars = true,
            animationDuration = ChartConstants.DEFAULT_ANIMATION_DURATION
        )
    }
}

private fun processChartData(
    data: Map<String, Float>,
    fromDate: String,
    toDate: String,
    mode: String
): List<Pair<String, Float>> {
    val dateFormatter = if (mode == "Ngày") {
        @Suppress("DEPRECATION")
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("vi", "VN"))
    } else {
        @Suppress("DEPRECATION")
        java.text.SimpleDateFormat("MM/yyyy", java.util.Locale("vi", "VN"))
    }
    val firebaseFormatter = if (mode == "Ngày") {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    } else {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
    }
    try {
        val from = dateFormatter.parse(fromDate) ?: return emptyList()
        val to = dateFormatter.parse(toDate) ?: return emptyList()
        val calendar = java.util.Calendar.getInstance()
        calendar.time = from
        val result = mutableListOf<Pair<String, Float>>()
        while (!calendar.time.after(to)) {
            val key = firebaseFormatter.format(calendar.time)
            val consumption = calculateConsumption(data, key, mode, calendar)
            val displayLabel = if (mode == "Ngày") {
                val parts = key.split("-")
                "${parts[2]}/${parts[1]}"
            } else {
                val parts = key.split("-")
                val year = parts[0].takeLast(2)
                "${parts[1]}/$year"
            }
            // Luôn add label, nếu không có dữ liệu thì value = 0f
            result.add(Pair(displayLabel, if (consumption > 0f) consumption else 0f))
            if (mode == "Ngày") {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            } else {
                calendar.add(java.util.Calendar.MONTH, 1)
            }
        }
        return result
    } catch (_: Exception) {
        return emptyList()
    }
}

private fun calculateConsumption(
    data: Map<String, Float>,
    key: String,
    mode: String,
    calendar: java.util.Calendar
): Float {
    val firebaseDateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val firebaseMonthFormatter = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
    
    return if (mode == "Ngày") {
        val prevDay = java.util.Calendar.getInstance().apply {
            time = calendar.time
            add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val currKey = firebaseDateFormatter.format(calendar.time)
        val prevKey = firebaseDateFormatter.format(prevDay.time)
        val curr = data[currKey]
        val prev = data[prevKey]
        if (prev != null && curr != null) curr - prev else 0f
    } else {
        val prevMonth = java.util.Calendar.getInstance().apply {
            time = calendar.time
            add(java.util.Calendar.MONTH, -1)
        }
        val prevMonthKey = firebaseMonthFormatter.format(prevMonth.time)
        
        val currentMonthData = data.filterKeys { it.startsWith(key) }.toSortedMap()
        val prevMonthData = data.filterKeys { it.startsWith(prevMonthKey) }.toSortedMap()
        
        val currentValues = currentMonthData.values.toList()
        val prevValues = prevMonthData.values.toList()
        
        val currentMaxValue = currentValues.maxOrNull() ?: 0f
        val prevMonthLastValue = prevValues.lastOrNull()
        
        if (prevMonthLastValue != null) {
            currentMaxValue - prevMonthLastValue
        } else {
            val currentMinValue = currentValues.minOrNull() ?: 0f
            currentMaxValue - currentMinValue
        }
    }
}