package com.app.buildingmanagement.fragment

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.buildingmanagement.data.FirebaseDataState
import com.app.buildingmanagement.fragment.ui.chart.ChartConstants
import com.app.buildingmanagement.fragment.ui.chart.UsageChart
import com.app.buildingmanagement.fragment.ui.home.HomeConstants
import com.app.buildingmanagement.fragment.ui.home.responsiveDimension
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen() {
    var selectedElectricMode by remember { mutableStateOf("Tháng") }
    var selectedWaterMode by remember { mutableStateOf("Tháng") }
    var fromDateElectric by remember { mutableStateOf("") }
    var toDateElectric by remember { mutableStateOf("") }
    var fromDateWater by remember { mutableStateOf("") }
    var toDateWater by remember { mutableStateOf("") }

    var electricData by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var waterData by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    // Dữ liệu gốc từ Firebase
    var allElectricData by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var allWaterData by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    val displayDateFormatter = remember {
        @Suppress("DEPRECATION")
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
    }
    val displayMonthFormatter = remember {
        @Suppress("DEPRECATION")
        SimpleDateFormat("MM/yyyy", Locale("vi", "VN"))
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var isElectricPicker by remember { mutableStateOf(true) }
    var isFromDatePicker by remember { mutableStateOf(true) }

    var tempDate by remember { mutableStateOf("") }
    var tempMonth by remember { mutableStateOf(Pair(0, 2024)) }

    // Lưu lại khoảng ngày/tháng đã chọn cho từng mode
    var lastDayRangeElectric by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastMonthRangeElectric by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastDayRangeWater by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastMonthRangeWater by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Load data và set mặc định
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
            allElectricData = elecData
            allWaterData = watData
        }
    }

    // Tự động filter lại dữ liệu mỗi khi khoảng ngày/tháng hoặc dữ liệu gốc thay đổi
    LaunchedEffect(fromDateElectric, toDateElectric, selectedElectricMode, allElectricData) {
        electricData = filterDataByDateRange(
            allElectricData, fromDateElectric, toDateElectric, selectedElectricMode, displayDateFormatter, displayMonthFormatter
        )
    }
    LaunchedEffect(fromDateWater, toDateWater, selectedWaterMode, allWaterData) {
        waterData = filterDataByDateRange(
            allWaterData, fromDateWater, toDateWater, selectedWaterMode, displayDateFormatter, displayMonthFormatter
        )
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
                        if (selectedElectricMode != mode) {
                            // Lưu lại khoảng cũ
                            if (selectedElectricMode == "Ngày") lastDayRangeElectric = fromDateElectric to toDateElectric
                            else lastMonthRangeElectric = fromDateElectric to toDateElectric

                            selectedElectricMode = mode
                            if (mode == "Ngày") {
                                val range = lastDayRangeElectric
                                if (range != null) {
                                    fromDateElectric = range.first
                                    toDateElectric = range.second
                                } else {
                                    setDefaultRange(mode, displayDateFormatter, displayMonthFormatter) { from, to ->
                                        fromDateElectric = from
                                        toDateElectric = to
                                    }
                                }
                            } else {
                                val range = lastMonthRangeElectric
                                if (range != null) {
                                    fromDateElectric = range.first
                                    toDateElectric = range.second
                                } else {
                                    setDefaultRange(mode, displayDateFormatter, displayMonthFormatter) { from, to ->
                                        fromDateElectric = from
                                        toDateElectric = to
                                    }
                                }
                            }
                        }
                        loadChartData { elecData, watData ->
                            allElectricData = elecData
                            allWaterData = watData
                        }
                    },
                    onFromDateClick = {
                        if (selectedElectricMode == "Ngày") {
                            isElectricPicker = true
                            isFromDatePicker = true
                            tempDate = fromDateElectric
                            showDatePicker = true
                        } else {
                            isElectricPicker = true
                            isFromDatePicker = true
                            val parts = fromDateElectric.split("/")
                            val month = parts.getOrNull(0)?.toIntOrNull()?.minus(1) ?: 0
                            val year = parts.getOrNull(1)?.toIntOrNull() ?: 2024
                            tempMonth = Pair(month, year)
                            showMonthPicker = true
                        }
                    },
                    onToDateClick = {
                        if (selectedElectricMode == "Ngày") {
                            isElectricPicker = true
                            isFromDatePicker = false
                            tempDate = toDateElectric
                            showDatePicker = true
                        } else {
                            isElectricPicker = true
                            isFromDatePicker = false
                            val parts = toDateElectric.split("/")
                            val month = parts.getOrNull(0)?.toIntOrNull()?.minus(1) ?: 0
                            val year = parts.getOrNull(1)?.toIntOrNull() ?: 2024
                            tempMonth = Pair(month, year)
                            showMonthPicker = true
                        }
                    }
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
                        if (selectedWaterMode != mode) {
                            // Lưu lại khoảng cũ
                            if (selectedWaterMode == "Ngày") lastDayRangeWater = fromDateWater to toDateWater
                            else lastMonthRangeWater = fromDateWater to toDateWater

                            selectedWaterMode = mode
                            if (mode == "Ngày") {
                                val range = lastDayRangeWater
                                if (range != null) {
                                    fromDateWater = range.first
                                    toDateWater = range.second
                                } else {
                                    setDefaultRange(mode, displayDateFormatter, displayMonthFormatter) { from, to ->
                                        fromDateWater = from
                                        toDateWater = to
                                    }
                                }
                            } else {
                                val range = lastMonthRangeWater
                                if (range != null) {
                                    fromDateWater = range.first
                                    toDateWater = range.second
                                } else {
                                    setDefaultRange(mode, displayDateFormatter, displayMonthFormatter) { from, to ->
                                        fromDateWater = from
                                        toDateWater = to
                                    }
                                }
                            }
                        }
                        loadChartData { elecData, watData ->
                            allElectricData = elecData
                            allWaterData = watData
                        }
                    },
                    onFromDateClick = {
                        if (selectedWaterMode == "Ngày") {
                            isElectricPicker = false
                            isFromDatePicker = true
                            tempDate = fromDateWater
                            showDatePicker = true
                        } else {
                            isElectricPicker = false
                            isFromDatePicker = true
                            val parts = fromDateWater.split("/")
                            val month = parts.getOrNull(0)?.toIntOrNull()?.minus(1) ?: 0
                            val year = parts.getOrNull(1)?.toIntOrNull() ?: 2024
                            tempMonth = Pair(month, year)
                            showMonthPicker = true
                        }
                    },
                    onToDateClick = {
                        if (selectedWaterMode == "Ngày") {
                            isElectricPicker = false
                            isFromDatePicker = false
                            tempDate = toDateWater
                            showDatePicker = true
                        } else {
                            isElectricPicker = false
                            isFromDatePicker = false
                            val parts = toDateWater.split("/")
                            val month = parts.getOrNull(0)?.toIntOrNull()?.minus(1) ?: 0
                            val year = parts.getOrNull(1)?.toIntOrNull() ?: 2024
                            tempMonth = Pair(month, year)
                            showMonthPicker = true
                        }
                    }
                )
            }
        }
    }

    // Dialog chọn ngày (Material3)
    if (showDatePicker) {
        val formatter = displayDateFormatter
        val initialMillis = try {
            formatter.parse(tempDate)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) { System.currentTimeMillis() }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance().apply { timeInMillis = millis }
                        val formatted = formatter.format(cal.time)
                        if (isElectricPicker) {
                            if (isFromDatePicker) fromDateElectric = formatted else toDateElectric = formatted
            } else {
                            if (isFromDatePicker) fromDateWater = formatted else toDateWater = formatted
                    }
                        loadChartData { elecData, watData ->
                        electricData = elecData
                        waterData = watData
                    }
                    }
                    showDatePicker = false
                }) { Text("Chọn") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Hủy") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Dialog chọn tháng (MonthPickerDialogCompose)
    if (showMonthPicker) {
        com.app.buildingmanagement.MonthPickerDialog(
            selectedMonth = tempMonth.first,
            selectedYear = tempMonth.second,
            onMonthYearSelected = { month, year ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                }
                val formatted = displayMonthFormatter.format(cal.time)
                if (isElectricPicker) {
                    if (isFromDatePicker) fromDateElectric = formatted else toDateElectric = formatted
                    } else {
                    if (isFromDatePicker) fromDateWater = formatted else toDateWater = formatted
                    }
                loadChartData { elecData, watData ->
                        electricData = elecData
                        waterData = watData
                    }
                showMonthPicker = false
                },
            onDismiss = { showMonthPicker = false }
            )
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
        modifier = modifier,
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
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
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

private fun filterDataByDateRange(
    data: Map<String, Float>,
    fromDate: String,
    toDate: String,
    mode: String,
    displayDateFormatter: SimpleDateFormat,
    displayMonthFormatter: SimpleDateFormat
): Map<String, Float> {
    if (fromDate.isEmpty() || toDate.isEmpty()) return emptyMap()
    val from = try {
        if (mode == "Ngày") displayDateFormatter.parse(fromDate) else displayMonthFormatter.parse(fromDate)
    } catch (_: Exception) { null }
    val to = try {
        if (mode == "Ngày") displayDateFormatter.parse(toDate) else displayMonthFormatter.parse(toDate)
    } catch (_: Exception) { null }
    if (from == null || to == null) return emptyMap()
    val firebaseFormatter = if (mode == "Ngày") SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) else SimpleDateFormat("yyyy-MM", Locale.getDefault())
    return data.filter { (dateKey, _) ->
        val date = try { firebaseFormatter.parse(dateKey) } catch (_: Exception) { null }
        date != null && !date.before(from) && !date.after(to)
    }
}