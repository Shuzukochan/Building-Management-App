package com.app.buildingmanagement.fragment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.R
import com.app.buildingmanagement.data.FirebaseDataState
import com.app.buildingmanagement.fragment.ui.chart.UsageChart
import com.app.buildingmanagement.fragment.ui.chart.ChartConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext

private data class PickerInfo(val isElectric: Boolean, val isFromDate: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartDatePicker(
    initialDateString: String,
    formatter: SimpleDateFormat,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = remember(initialDateString) {
        try {
            formatter.parse(initialDateString)?.time
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = (2020..Calendar.getInstance().get(Calendar.YEAR))
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
                        onDateSelected(formatter.format(calendar.time))
                    }
                    onDismiss()
                }
            ) {
                Text("Chọn")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

class ChartFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    
    private var selectedElectricMode by mutableStateOf("Tháng")
    private var selectedWaterMode by mutableStateOf("Tháng")
    private var fromDateElectric by mutableStateOf("")
    private var toDateElectric by mutableStateOf("")
    private var fromDateWater by mutableStateOf("")
    private var toDateWater by mutableStateOf("")
    
    private var electricData by mutableStateOf<Map<String, Float>>(emptyMap())
    private var waterData by mutableStateOf<Map<String, Float>>(emptyMap())

    private val displayDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
    private val displayMonthFormatter = SimpleDateFormat("MM/yyyy", Locale("vi", "VN"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                FragmentChartScreen()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeFirebase()
        setDefaultRanges()
        loadChartData()
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
    }

    private fun loadChartData() {
        FirebaseDataState.getHistoryData { electricMap, waterMap ->
            if (isAdded) {
                electricData = electricMap
                waterData = waterMap
            }
        }
    }

    private fun setDefaultRanges() {
        setDefaultRange(true)
        setDefaultRange(false)
    }

    private fun setDefaultRange(isElectric: Boolean) {
        val calendar = Calendar.getInstance()
        val toDate = calendar.time

        val mode = if (isElectric) selectedElectricMode else selectedWaterMode

        if (mode == "Ngày") {
            calendar.add(Calendar.DAY_OF_MONTH, -6)
        } else {
            calendar.add(Calendar.MONTH, -5)
        }
        val fromDate = calendar.time

        if (isElectric) {
            if (selectedElectricMode == "Ngày") {
                fromDateElectric = displayDateFormatter.format(fromDate)
                toDateElectric = displayDateFormatter.format(toDate)
            } else {
                fromDateElectric = displayMonthFormatter.format(fromDate)
                toDateElectric = displayMonthFormatter.format(toDate)
            }
        } else {
            if (selectedWaterMode == "Ngày") {
                fromDateWater = displayDateFormatter.format(fromDate)
                toDateWater = displayDateFormatter.format(toDate)
            } else {
                fromDateWater = displayMonthFormatter.format(fromDate)
                toDateWater = displayMonthFormatter.format(toDate)
            }
        }
    }

    @Composable
    fun FragmentChartScreen() {
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        
        val dimen = com.app.buildingmanagement.fragment.ui.home.responsiveDimension()
        
        val headerHeight = dimen.titleTextSize.value.dp + com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp
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
            Text(
                text = "Thống kê tiêu thụ",
                fontSize = dimen.titleTextSize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            
            Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                            setDefaultRange(true)
                            loadChartData()
                        },
                        onFromDateClick = { showDatePicker(true, true) },
                        onToDateClick = { showDatePicker(true, false) }
                    )
                }
                
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
                            setDefaultRange(false)
                            loadChartData()
                        },
                        onFromDateClick = { showDatePicker(false, true) },
                        onToDateClick = { showDatePicker(false, false) }
                    )
                }
            }
        }
    }

    @Composable
    fun ModernChartCard(
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
    fun CompactModeSpinner(
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
    fun CompactDateField(
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

    private fun showDatePicker(isElectric: Boolean, isFromDate: Boolean) {
        val mode = if (isElectric) selectedElectricMode else selectedWaterMode
        val calendar = Calendar.getInstance()

        if (mode == "Tháng") {
            showMonthPicker(isElectric, isFromDate, calendar)
        } else {
            showDayPicker(isElectric, isFromDate, calendar)
        }
    }

    private fun showMonthPicker(isElectric: Boolean, isFromDate: Boolean, calendar: Calendar) {
        val currentText = if (isElectric) {
            if (isFromDate) fromDateElectric else toDateElectric
        } else {
            if (isFromDate) fromDateWater else toDateWater
        }
        
        val parts = currentText.split("/")
        val selectedMonth = if (parts.size == 2) {
            parts[0].toIntOrNull()?.minus(1) ?: calendar.get(Calendar.MONTH)
        } else {
            calendar.get(Calendar.MONTH)
        }
        val selectedYear = if (parts.size == 2) {
            parts[1].toIntOrNull() ?: calendar.get(Calendar.YEAR)
        } else {
            calendar.get(Calendar.YEAR)
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, _ ->
                calendar.set(year, month, 1)
                val formattedDate = displayMonthFormatter.format(calendar.time)
                
                if (isElectric) {
                    if (isFromDate) fromDateElectric = formattedDate
                    else toDateElectric = formattedDate
                } else {
                    if (isFromDate) fromDateWater = formattedDate
                    else toDateWater = formattedDate
                }
                loadChartData()
            },
            selectedYear,
            selectedMonth,
            1
        ).show()
    }

    private fun showDayPicker(isElectric: Boolean, isFromDate: Boolean, calendar: Calendar) {
        val currentText = if (isElectric) {
            if (isFromDate) fromDateElectric else toDateElectric
        } else {
            if (isFromDate) fromDateWater else toDateWater
        }
        
        val date = try {
            displayDateFormatter.parse(currentText)
        } catch (e: Exception) {
            null
        }
        if (date != null) calendar.time = date

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day)
                val formattedDate = displayDateFormatter.format(calendar.time)
                
                if (isElectric) {
                    if (isFromDate) fromDateElectric = formattedDate
                    else toDateElectric = formattedDate
                } else {
                    if (isFromDate) fromDateWater = formattedDate
                    else toDateWater = formattedDate
                }
                loadChartData()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}

// ============================================================================
// EXTERNAL COMPOSABLE FUNCTION - LIKE HomeScreen()
// ============================================================================

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

    var pickerInfo by remember { mutableStateOf<PickerInfo?>(null) }

    val displayDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")) }
    val displayMonthFormatter = remember { SimpleDateFormat("MM/yyyy", Locale("vi", "VN")) }

    // Initialize data
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
        FirebaseDataState.getHistoryData { elecData, watData ->
            electricData = elecData
            waterData = watData
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val dimen = com.app.buildingmanagement.fragment.ui.home.responsiveDimension()

    val headerHeight = dimen.titleTextSize.value.dp + com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp
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

        Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp))

        // Charts container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Electric Chart Card
            Box(modifier = Modifier.weight(1f)) {
                StandaloneModernChartCard(
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
                        setDefaultRanges(
                            mode, selectedWaterMode,
                            displayDateFormatter, displayMonthFormatter
                        ) { fromElec, toElec, fromWat, toWat ->
                            fromDateElectric = fromElec
                            toDateElectric = toElec
                            fromDateWater = fromWat
                            toDateWater = toWat
                        }
                        FirebaseDataState.getHistoryData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    },
                    onFromDateClick = { pickerInfo = PickerInfo(isElectric = true, isFromDate = true) },
                    onToDateClick = { pickerInfo = PickerInfo(isElectric = true, isFromDate = false) }
                )
            }

            // Water Chart Card
            Box(modifier = Modifier.weight(1f)) {
                StandaloneModernChartCard(
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
                         setDefaultRanges(
                            selectedElectricMode, mode,
                            displayDateFormatter, displayMonthFormatter
                        ) { fromElec, toElec, fromWat, toWat ->
                            fromDateElectric = fromElec
                            toDateElectric = toElec
                            fromDateWater = fromWat
                            toDateWater = toWat
                        }
                        FirebaseDataState.getHistoryData { elecData, watData ->
                            electricData = elecData
                            waterData = watData
                        }
                    },
                    onFromDateClick = { pickerInfo = PickerInfo(isElectric = false, isFromDate = true) },
                    onToDateClick = { pickerInfo = PickerInfo(isElectric = false, isFromDate = false) }
                )
            }
        }
    }

    // --- Date Picker Dialogs ---
    pickerInfo?.let { info ->
        val mode = if (info.isElectric) selectedElectricMode else selectedWaterMode
        if (mode == "Tháng") {
            val currentDate = if (info.isElectric) {
                if (info.isFromDate) fromDateElectric else toDateElectric
            } else {
                if (info.isFromDate) fromDateWater else toDateWater
            }
            val cal = Calendar.getInstance()
            if (currentDate.isNotEmpty()) {
                try {
                    cal.time = displayMonthFormatter.parse(currentDate)!!
                } catch (e: Exception) { /* use default */ }
            }

            com.app.buildingmanagement.MonthPickerDialog(
                selectedMonth = cal.get(Calendar.MONTH),
                selectedYear = cal.get(Calendar.YEAR),
                onMonthYearSelected = { month, year ->
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    val formatted = displayMonthFormatter.format(cal.time)
                    if (info.isElectric) {
                        if (info.isFromDate) fromDateElectric = formatted else toDateElectric = formatted
                    } else {
                        if (info.isFromDate) fromDateWater = formatted else toDateWater = formatted
                    }
                    FirebaseDataState.getHistoryData { elecData, watData ->
                        electricData = elecData
                        waterData = watData
                    }
                    pickerInfo = null
                },
                onDismiss = { pickerInfo = null }
            )
        } else { // mode == "Ngày"
            val currentDate = if (info.isElectric) {
                if (info.isFromDate) fromDateElectric else toDateElectric
            } else {
                if (info.isFromDate) fromDateWater else toDateWater
            }
            
            val pickerInitialDate = if (info.isElectric) {
                if (info.isFromDate) fromDateElectric else toDateElectric
            } else {
                if (info.isFromDate) fromDateWater else toDateWater
            }

            ChartDatePicker(
                initialDateString = pickerInitialDate,
                formatter = displayDateFormatter,
                onDateSelected = { formatted ->
                    if (info.isElectric) {
                        if (info.isFromDate) fromDateElectric = formatted else toDateElectric = formatted
                    } else {
                        if (info.isFromDate) fromDateWater = formatted else toDateWater = formatted
                    }
                    FirebaseDataState.getHistoryData { elecData, watData ->
                        electricData = elecData
                        waterData = watData
                    }
                    pickerInfo = null
                },
                onDismiss = { pickerInfo = null }
            )
        }
    }
}

@Composable
private fun StandaloneModernChartCard(
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
            // Header
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
                
                StandaloneCompactModeSpinner(selectedMode, onModeSelected)
            }
            
            // Date range
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StandaloneCompactDateField(
                    label = "Từ:",
                    value = fromDate,
                    onClick = onFromDateClick,
                    modifier = Modifier.weight(1f)
                )
                
                StandaloneCompactDateField(
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
private fun StandaloneCompactModeSpinner(
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
private fun StandaloneCompactDateField(
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

// Helper function for setting default ranges
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