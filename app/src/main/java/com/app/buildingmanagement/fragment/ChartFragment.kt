package com.app.buildingmanagement.fragment

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.databinding.FragmentChartBinding
import com.app.buildingmanagement.MonthPickerDialog
import com.app.buildingmanagement.data.SharedDataManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ChartFragment : Fragment(), SharedDataManager.DataUpdateListener {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var roomsRef: DatabaseReference

    private var selectedElectricMode = "Tháng"
    private var selectedWaterMode = "Tháng"

    private val firebaseDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val firebaseMonthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val displayDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
    private val displayMonthFormatter = SimpleDateFormat("MM/yyyy", Locale("vi", "VN"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeFirebase()
        setupSpinners()
        setupDatePickers()
        setDefaultRanges()

        SharedDataManager.addListener(this)
        loadChartDataWithCache()
    }

    override fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (_binding == null || !isAdded) return
        loadChartDataFromSnapshot(roomSnapshot)
    }

    override fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (_binding == null || !isAdded) return
        loadChartDataFromSnapshot(roomSnapshot)
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        roomsRef = database.getReference("rooms")
    }

    private fun loadChartDataWithCache() {
        val cachedSnapshot = SharedDataManager.getCachedRoomSnapshot()
        val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()

        if (cachedSnapshot != null && cachedRoomNumber != null) {
            loadChartDataFromSnapshot(cachedSnapshot)
        } else {
            loadChartDataFromFirebase()
        }
    }

    private fun loadChartDataFromSnapshot(roomSnapshot: DataSnapshot) {
        val electricMap = mutableMapOf<String, Int>()
        val waterMap = mutableMapOf<String, Int>()

        val historySnapshot = roomSnapshot.child("history")

        for (dateSnapshot in historySnapshot.children) {
            val dateKey = dateSnapshot.key ?: continue

            val waterValue = dateSnapshot.child("water").getValue(Long::class.java)?.toInt()
            val electricValue = dateSnapshot.child("electric").getValue(Long::class.java)?.toInt()

            if (waterValue != null) {
                waterMap[dateKey] = waterValue
            }
            if (electricValue != null) {
                electricMap[dateKey] = electricValue
            }
        }

        val fromDateElectric = binding.fromDateElectric.text.toString()
        val toDateElectric = binding.toDateElectric.text.toString()
        val fromDateWater = binding.fromDateWater.text.toString()
        val toDateWater = binding.toDateWater.text.toString()

        drawChart(electricMap, fromDateElectric, toDateElectric, selectedElectricMode, true)
        drawChart(waterMap, fromDateWater, toDateWater, selectedWaterMode, false)
    }

    private fun loadChartDataFromFirebase() {
        val phone = auth.currentUser?.phoneNumber ?: return

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (roomSnapshot in snapshot.children) {
                    val tenantsSnapshot = roomSnapshot.child("tenants")
                    var phoneFound = false

                    for (tenantSnapshot in tenantsSnapshot.children) {
                        val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInTenant == phone) {
                            phoneFound = true
                            val roomNumber = roomSnapshot.key
                            if (roomNumber != null) {
                                SharedDataManager.setCachedData(roomSnapshot, roomNumber, phone)
                            }
                            loadChartDataFromSnapshot(roomSnapshot)
                            break
                        }
                    }

                    if (phoneFound) {
                        break
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupSpinners() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Tháng", "Ngày")
        )

        binding.modeSpinnerElectric.adapter = adapter
        binding.modeSpinnerWater.adapter = adapter
        binding.modeSpinnerElectric.setSelection(0)
        binding.modeSpinnerWater.setSelection(0)

        binding.modeSpinnerElectric.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedElectricMode = adapter.getItem(position) ?: "Tháng"
                setDefaultRange(true)
                loadChartDataWithCache()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.modeSpinnerWater.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedWaterMode = adapter.getItem(position) ?: "Tháng"
                setDefaultRange(false)
                loadChartDataWithCache()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupDatePickers() {
        setupDatePicker(binding.fromDateElectric, true)
        setupDatePicker(binding.toDateElectric, true)
        setupDatePicker(binding.fromDateWater, false)
        setupDatePicker(binding.toDateWater, false)
    }

    private fun setupDatePicker(editText: EditText, isElectric: Boolean) {
        editText.setOnClickListener {
            val mode = if (isElectric) selectedElectricMode else selectedWaterMode
            val calendar = Calendar.getInstance()

            if (mode == "Tháng") {
                showMonthPicker(editText, calendar)
            } else {
                showDatePicker(editText, calendar)
            }
        }
    }

    private fun showMonthPicker(editText: EditText, calendar: Calendar) {
        val text = editText.text.toString()
        val parts = text.split("/")

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

        try {
            MonthPickerDialog(
                context = requireContext(),
                selectedMonth = selectedMonth,
                selectedYear = selectedYear
            ) { pickedMonth, pickedYear ->
                calendar.set(Calendar.YEAR, pickedYear)
                calendar.set(Calendar.MONTH, pickedMonth)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                editText.setText(displayMonthFormatter.format(calendar.time))
                loadChartDataWithCache()
            }.show()
        } catch (e: Exception) {
            DatePickerDialog(
                requireContext(),
                { _, year, month, _ ->
                    calendar.set(year, month, 1)
                    editText.setText(displayMonthFormatter.format(calendar.time))
                    loadChartDataWithCache()
                },
                selectedYear,
                selectedMonth,
                1
            ).show()
        }
    }

    private fun showDatePicker(editText: EditText, calendar: Calendar) {
        val text = editText.text.toString()
        val date = try {
            displayDateFormatter.parse(text)
        } catch (e: Exception) {
            null
        }
        if (date != null) calendar.time = date

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day)
                editText.setText(displayDateFormatter.format(calendar.time))
                loadChartDataWithCache()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
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
                binding.fromDateElectric.setText(displayDateFormatter.format(fromDate))
                binding.toDateElectric.setText(displayDateFormatter.format(toDate))
            } else {
                binding.fromDateElectric.setText(displayMonthFormatter.format(fromDate))
                binding.toDateElectric.setText(displayMonthFormatter.format(toDate))
            }
        } else {
            if (selectedWaterMode == "Ngày") {
                binding.fromDateWater.setText(displayDateFormatter.format(fromDate))
                binding.toDateWater.setText(displayDateFormatter.format(toDate))
            } else {
                binding.fromDateWater.setText(displayMonthFormatter.format(fromDate))
                binding.toDateWater.setText(displayMonthFormatter.format(toDate))
            }
        }
    }

    private fun parseDateInput(value: String, mode: String): Date? {
        return try {
            if (mode == "Ngày") {
                displayDateFormatter.parse(value)
            } else {
                val parts = value.split("/")
                if (parts.size == 2) {
                    firebaseMonthFormatter.parse("${parts[1]}-${parts[0]}")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawChart(map: Map<String, Int>, fromDate: String, toDate: String, mode: String, isElectric: Boolean) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        val from = parseDateInput(fromDate, mode) ?: return
        val to = parseDateInput(toDate, mode) ?: return

        val cal = Calendar.getInstance()
        cal.time = from

        var index = 0f
        while (!cal.time.after(to)) {
            val key = if (mode == "Ngày") firebaseDateFormatter.format(cal.time) else firebaseMonthFormatter.format(cal.time)

            val value = calculateConsumption(map, key, mode, cal)

            entries.add(BarEntry(index, value.toFloat()))

            val label = formatLabel(key, mode)
            labels.add(label)

            index++
            if (mode == "Ngày") cal.add(Calendar.DAY_OF_MONTH, 1) else cal.add(Calendar.MONTH, 1)
        }

        val chart = if (isElectric) binding.electricChart else binding.waterChart
        val label = if (isElectric) "Điện (kWh)" else "Nước (m³)"
        val color = if (isElectric) "#FF6B35" else "#42A5F5"
        setupChart(chart, entries, labels, label, color)
    }

    private fun calculateConsumption(map: Map<String, Int>, key: String, mode: String, cal: Calendar): Int {
        return if (mode == "Ngày") {
            val prevDay = Calendar.getInstance().apply {
                time = cal.time
                add(Calendar.DAY_OF_MONTH, -1)
            }
            val currKey = firebaseDateFormatter.format(cal.time)
            val prevKey = firebaseDateFormatter.format(prevDay.time)
            val curr = map[currKey]
            val prev = map[prevKey]
            if (prev != null && curr != null) curr - prev else 0
        } else {
            val prevMonth = Calendar.getInstance().apply {
                time = cal.time
                add(Calendar.MONTH, -1)
            }
            val prevMonthKey = firebaseMonthFormatter.format(prevMonth.time)
            
            val currentMonthData = map.filterKeys { it.startsWith(key) }.toSortedMap()
            val prevMonthData = map.filterKeys { it.startsWith(prevMonthKey) }.toSortedMap()
            
            val currentValues = currentMonthData.values.toList()
            val prevValues = prevMonthData.values.toList()
            
            val currentMaxValue = currentValues.maxOrNull() ?: 0
            val prevMonthLastValue = prevValues.lastOrNull()
            
            if (prevMonthLastValue != null) {
                currentMaxValue - prevMonthLastValue
            } else {
                val currentMinValue = currentValues.minOrNull() ?: 0
                currentMaxValue - currentMinValue
            }
        }
    }

    private fun formatLabel(key: String, mode: String): String {
        val parts = key.split("-")
        return if (mode == "Ngày") {
            "${parts[2]}/${parts[1]}"
        } else {
            val year = parts[0].takeLast(2)
            "${parts[1]}/$year"
        }
    }

    private fun setupChart(
        chart: com.github.mikephil.charting.charts.BarChart,
        entries: List<BarEntry>,
        labels: List<String>,
        label: String,
        colorHex: String
    ) {
        val dataSet = BarDataSet(entries, label).apply {
            color = Color.parseColor(colorHex)
            valueTextSize = 10f
            valueTextColor = Color.BLACK
            setDrawValues(true)
        }

        chart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            setTouchEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            data = BarData(dataSet)
            setExtraOffsets(0f, 0f, 0f, 3f)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                xAxis.yOffset = 12f
                setDrawGridLines(false)
                labelRotationAngle = 0f
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                gridLineWidth = 0.5f
                textColor = Color.parseColor("#666666")
                textSize = 10f
            }

            description.text = ""
            legend.apply {
                isEnabled = true
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                xOffset = -5f
                textColor = Color.parseColor("#333333")
            }
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SharedDataManager.removeListener(this)
        _binding = null
    }
}