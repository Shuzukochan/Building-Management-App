package com.app.buildingmanagement.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.buildingmanagement.R
import com.app.buildingmanagement.data.FirebaseDataState
import com.app.buildingmanagement.fragment.ui.chart.ChartConstants
import com.app.buildingmanagement.fragment.ui.chart.UsageChart
import com.app.buildingmanagement.fragment.ui.home.HomeConstants
import com.app.buildingmanagement.fragment.ui.home.responsiveDimension
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChartScreen() {
    val auth = remember { FirebaseAuth.getInstance() }
    val database = remember { FirebaseDatabase.getInstance() }

    var selectedElectricMode by remember { mutableStateOf("Tháng") }
    var selectedWaterMode by remember { mutableStateOf("Tháng") }
    var fromDateElectric by remember { mutableStateOf("") }
    var toDateElectric by remember { mutableStateOf("") }
    var fromDateWater by remember { mutableStateOf("") }
    var toDateWater by remember { mutableStateOf("") }
    
    var electricData by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var waterData by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    val displayDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")) }
    val displayMonthFormatter = remember { SimpleDateFormat("MM/yyyy", Locale("vi", "VN")) }

    // Load data and set defaults
    LaunchedEffect(Unit) {
        setDefaultRanges(
            selectedElectricMode, selectedWaterMode,
            displayDateFormatter, displayMonthFormatter
        ) { fromElec, toElec, fromWat, toWat ->
            fromDateElectric = fromElec
            toDateElectric = toElec
            fromDateWater = fromWat
            toDateWater = toWat
        }
        loadChartData { elecData, watData ->
            electricData = elecData
            waterData = watData
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    val dimen = responsiveDimension()
    
    val headerHeight = dimen.titleTextSize.value.dp + HomeConstants.SPACING_XXL.dp
    val totalPadding = dimen.mainPadding * 2
    val navigationHeight = 80.dp
    val cardSpacing = 16.dp
    
    val availableHeight = screenHeight - headerHeight - totalPadding - navigationHeight - 20.dp
    val singleCardHeight = (availableHeight - cardSpacing) / 2
    val finalChartHeight = singleCardHeight.coerceAtLeast(180.dp)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(dimen.mainPadding)
    ) {
        // Header
        Text(
            text = "Thống kê tiêu thụ",
            fontSize = dimen.titleTextSize,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        
        Spacer(modifier = Modifier.height(HomeConstants.SPACING_XXL.dp))
        
        // Charts container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Electric Chart Card
            Box(modifier = Modifier.weight(1f)) {
                ModernChartCard(
                    title = "Tiêu thụ điện",
                    icon = Icons.Default.Bolt,
                    iconColor = ChartConstants.ElectricColor,
                    selectedMode = selectedElectricMode,
                    fromDate = fromDateElectric,
                    toDate = toDateElectric,
                    data = electricData,
                    chartColor = ChartConstants.ElectricColor,
                    chartHeight = finalChartHeight,
                    onModeSelected = { mode ->
                        selectedElectricMode = mode
                        setDefaultRange(true, mode, displayDateFormatter, displayMonthFormatter) { from, to ->
                            fromDateElectric = from
                            toDateElectric = to
                        }
                        loadChartData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    },
                    onFromDateClick = { showDatePicker(true, true, selectedElectricMode, fromDateElectric, displayDateFormatter, displayMonthFormatter) { date ->
                        fromDateElectric = date
                        loadChartData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    }},
                    onToDateClick = { showDatePicker(true, false, selectedElectricMode, toDateElectric, displayDateFormatter, displayMonthFormatter) { date ->
                        toDateElectric = date
                        loadChartData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    }}
                )
            }
            
            // Water Chart Card
            Box(modifier = Modifier.weight(1f)) {
                ModernChartCard(
                    title = "Tiêu thụ nước",
                    icon = Icons.Default.WaterDrop,
                    iconColor = ChartConstants.WaterColor,
                    selectedMode = selectedWaterMode,
                    fromDate = fromDateWater,
                    toDate = toDateWater,
                    data = waterData,
                    chartColor = ChartConstants.WaterColor,
                    chartHeight = finalChartHeight,
                    onModeSelected = { mode ->
                        selectedWaterMode = mode
                        setDefaultRange(false, mode, displayDateFormatter, displayMonthFormatter) { from, to ->
                            fromDateWater = from
                            toDateWater = to
                        }
                        loadChartData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    },
                    onFromDateClick = { showDatePicker(false, true, selectedWaterMode, fromDateWater, displayDateFormatter, displayMonthFormatter) { date ->
                        fromDateWater = date
                        loadChartData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    }},
                    onToDateClick = { showDatePicker(false, false, selectedWaterMode, toDateWater, displayDateFormatter, displayMonthFormatter) { date ->
                        toDateWater = date
                        loadChartData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    }}
                )
            }
        }
    }
}

@Composable
private fun ModernChartCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    selectedMode: String,
    fromDate: String,
    toDate: String,
    data: Map<String, Float>,
    chartColor: Color,
    chartHeight: Dp = ChartConstants.ChartHeight,
    onModeSelected: (String) -> Unit,
    onFromDateClick: () -> Unit,
    onToDateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Compact header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF2C3E50),
                        maxLines = 1
                    )
                }
                
                CompactModeSpinner(selectedMode, onModeSelected)
            }
            
            // Date range
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactDateField(
                    label = "Từ:",
                    value = fromDate,
                    onClick = onFromDateClick,
                    modifier = Modifier.weight(1f)
                )
                
                CompactDateField(
                    label = "Đến:",
                    value = toDate,
                    onClick = onToDateClick,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                UsageChart(
                    title = "",
                    data = data,
                    fromDate = fromDate,
                    toDate = toDate,
                    mode = selectedMode,
                    chartColor = chartColor,
                    chartHeight = chartHeight,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun CompactModeSpinner(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf("Tháng", "Ngày")
    
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = selectedMode,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            text = mode,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactDateField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF666666),
            modifier = Modifier.padding(end = 4.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    Color(0xFFF5F5F5),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = value.ifEmpty { "Chọn ngày" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (value.isEmpty()) Color(0xFF999999) else Color(0xFF333333),
                    maxLines = 1
                )
            }
        }
    }
}

// Helper functions
private fun setDefaultRanges(
    selectedElectricMode: String, 
    selectedWaterMode: String,
    displayDateFormatter: SimpleDateFormat,
    displayMonthFormatter: SimpleDateFormat,
    callback: (String, String, String, String) -> Unit
) {
    val calendar = Calendar.getInstance()
    val toDate = calendar.time

    // Electric
    calendar.time = toDate
    if (selectedElectricMode == "Ngày") {
        calendar.add(Calendar.DAY_OF_MONTH, -6)
    } else {
        calendar.add(Calendar.MONTH, -5)
    }
    val fromElectric = if (selectedElectricMode == "Ngày") {
        displayDateFormatter.format(calendar.time)
    } else {
        displayMonthFormatter.format(calendar.time)
    }
    val toElectric = if (selectedElectricMode == "Ngày") {
        displayDateFormatter.format(toDate)
    } else {
        displayMonthFormatter.format(toDate)
    }

    // Water
    calendar.time = toDate
    if (selectedWaterMode == "Ngày") {
        calendar.add(Calendar.DAY_OF_MONTH, -6)
    } else {
        calendar.add(Calendar.MONTH, -5)
    }
    val fromWater = if (selectedWaterMode == "Ngày") {
        displayDateFormatter.format(calendar.time)
    } else {
        displayMonthFormatter.format(calendar.time)
    }
    val toWater = if (selectedWaterMode == "Ngày") {
        displayDateFormatter.format(toDate)
    } else {
        displayMonthFormatter.format(toDate)
    }

    callback(fromElectric, toElectric, fromWater, toWater)
}

private fun setDefaultRange(
    isElectric: Boolean,
    mode: String,
    displayDateFormatter: SimpleDateFormat,
    displayMonthFormatter: SimpleDateFormat,
    callback: (String, String) -> Unit
) {
    val calendar = Calendar.getInstance()
    val toDate = calendar.time

    if (mode == "Ngày") {
        calendar.add(Calendar.DAY_OF_MONTH, -6)
    } else {
        calendar.add(Calendar.MONTH, -5)
    }
    val fromDate = calendar.time

    val fromFormatted = if (mode == "Ngày") {
        displayDateFormatter.format(fromDate)
    } else {
        displayMonthFormatter.format(fromDate)
    }
    val toFormatted = if (mode == "Ngày") {
        displayDateFormatter.format(toDate)
    } else {
        displayMonthFormatter.format(toDate)
    }

    callback(fromFormatted, toFormatted)
}

private fun loadChartData(callback: (Map<String, Float>, Map<String, Float>) -> Unit) {
    FirebaseDataState.getHistoryData { electricMap, waterMap ->
        callback(electricMap, waterMap)
    }
}

private fun showDatePicker(
    isElectric: Boolean,
    isFromDate: Boolean,
    mode: String,
    currentDate: String,
    displayDateFormatter: SimpleDateFormat,
    displayMonthFormatter: SimpleDateFormat,
    onDateSelected: (String) -> Unit
) {
    // This would need Context, implement as needed
} 