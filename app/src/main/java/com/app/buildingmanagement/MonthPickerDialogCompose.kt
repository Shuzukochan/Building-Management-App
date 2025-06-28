package com.app.buildingmanagement

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun MonthPickerDialog(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthYearSelected: (month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelectedMonth by remember { mutableIntStateOf(selectedMonth) }
    var currentSelectedYear by remember { mutableIntStateOf(selectedYear) }
    var currentViewYear by remember { mutableIntStateOf(selectedYear) }
    var isAnimating by remember { mutableStateOf(false) }
    
    val months = remember {
        listOf(
            "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4",
            "Tháng 5", "Tháng 6", "Tháng 7", "Tháng 8",
            "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
        )
    }

    val animationAlpha by animateFloatAsState(
        targetValue = if (isAnimating) 0.3f else 1f,
        animationSpec = tween(100),
        finishedListener = { isAnimating = false },
        label = "grid_animation"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Tháng ${currentSelectedMonth + 1}, $currentSelectedYear",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Year Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (!isAnimating) {
                                isAnimating = true
                                currentViewYear--
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Previous Year",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Text(
                        text = String.format(Locale.getDefault(), "%d", currentViewYear),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = {
                            if (!isAnimating) {
                                isAnimating = true
                                currentViewYear++
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next Year",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Month Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(animationAlpha),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(months) { index, month ->
                        MonthItem(
                            month = month,
                            isSelected = index == currentSelectedMonth && currentViewYear == currentSelectedYear,
                            onClick = {
                                currentSelectedMonth = index
                                currentSelectedYear = currentViewYear
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            text = "Hủy",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(
                        onClick = {
                            onMonthYearSelected(currentSelectedMonth, currentSelectedYear)
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = "Chọn",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthItem(
    month: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = month,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Extension function để sử dụng từ Activity/Fragment
@Composable
fun rememberMonthPickerDialog(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthYearSelected: (month: Int, year: Int) -> Unit
): MonthPickerDialogState {
    return remember {
        MonthPickerDialogState(
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            onMonthYearSelected = onMonthYearSelected
        )
    }
}

class MonthPickerDialogState(
    private val selectedMonth: Int,
    private val selectedYear: Int,
    private val onMonthYearSelected: (month: Int, year: Int) -> Unit
) {
    var isVisible by mutableStateOf(false)
        private set

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }

    @Composable
    fun Dialog() {
        if (isVisible) {
            MonthPickerDialog(
                selectedMonth = selectedMonth,
                selectedYear = selectedYear,
                onMonthYearSelected = onMonthYearSelected,
                onDismiss = { hide() }
            )
        }
    }
}
