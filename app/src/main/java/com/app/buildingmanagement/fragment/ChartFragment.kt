package com.app.buildingmanagement.fragment

import android.app.DatePickerDialog
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
import com.app.buildingmanagement.dialog.MonthPickerDialog
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class ChartFragment : Fragment() {

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

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        roomsRef = database.getReference("rooms")

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Tháng", "Ngày"))
        binding.modeSpinnerElectric.adapter = adapter
        binding.modeSpinnerWater.adapter = adapter
        binding.modeSpinnerElectric.setSelection(0)
        binding.modeSpinnerWater.setSelection(0)

        binding.modeSpinnerElectric.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedElectricMode = adapter.getItem(position) ?: "Tháng"
                setDefaultRange(true)
                loadChartData()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.modeSpinnerWater.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedWaterMode = adapter.getItem(position) ?: "Tháng"
                setDefaultRange(false)
                loadChartData()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        setupDatePicker(binding.fromDateElectric, true)
        setupDatePicker(binding.toDateElectric, true)
        setupDatePicker(binding.fromDateWater, false)
        setupDatePicker(binding.toDateWater, false)

        setDefaultRange(true)
        setDefaultRange(false)
        loadChartData()
    }

    private fun setupDatePicker(editText: EditText, isElectric: Boolean) {
        editText.setOnClickListener {
            val mode = if (isElectric) selectedElectricMode else selectedWaterMode

            val calendar = Calendar.getInstance()

            if (mode == "Tháng") {
                val text = editText.text.toString()
                val parts = text.split("/")
                if (parts.size == 2) {
                    val selectedMonth = parts[0].toIntOrNull()?.minus(1) ?: calendar.get(Calendar.MONTH)
                    val selectedYear = parts[1].toIntOrNull() ?: calendar.get(Calendar.YEAR)

                    MonthPickerDialog(
                        context = requireContext(),
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear
                    ) { pickedMonth, pickedYear ->
                        calendar.set(Calendar.YEAR, pickedYear)
                        calendar.set(Calendar.MONTH, pickedMonth)
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        editText.setText(displayMonthFormatter.format(calendar.time))
                        validateAndLoadChart(calendar.time, isElectric, mode, editText)
                    }.show()
                }
            } else {
                val text = editText.text.toString()
                val date = try {
                    displayDateFormatter.parse(text)
                } catch (e: Exception) {
                    null
                }
                if (date != null) calendar.time = date

                DatePickerDialog(requireContext(), { _, year, month, day ->
                    calendar.set(year, month, day)
                    editText.setText(displayDateFormatter.format(calendar.time))
                    validateAndLoadChart(calendar.time, isElectric, mode, editText)
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
    }

    private fun setDefaultRange(isElectric: Boolean) {
        val calendar = Calendar.getInstance()
        val toDate = calendar.time
        if ((if (isElectric) selectedElectricMode else selectedWaterMode) == "Ngày") {
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

    private fun validateAndLoadChart(selectedDate: Date, isElectric: Boolean, mode: String, editText: EditText) {
        try {
            val from = if (isElectric) binding.fromDateElectric.text.toString() else binding.fromDateWater.text.toString()
            val to = if (isElectric) binding.toDateElectric.text.toString() else binding.toDateWater.text.toString()

            val fromDate = if (mode == "Ngày") displayDateFormatter.parse(from) else displayMonthFormatter.parse(from)
            val toDate = if (mode == "Ngày") displayDateFormatter.parse(to) else displayMonthFormatter.parse(to)

            if (fromDate == null || toDate == null) {
                Toast.makeText(requireContext(), "Không thể phân tích ngày hợp lệ", Toast.LENGTH_SHORT).show()
                return
            }

            if (fromDate.after(toDate)) {
                Toast.makeText(requireContext(), "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc", Toast.LENGTH_SHORT).show()
                return
            }

            val diff = if (mode == "Ngày") {
                ((toDate.time - fromDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
            } else {
                val calFrom = Calendar.getInstance().apply { time = fromDate }
                val calTo = Calendar.getInstance().apply { time = toDate }
                val yearDiff = calTo.get(Calendar.YEAR) - calFrom.get(Calendar.YEAR)
                val monthDiff = calTo.get(Calendar.MONTH) - calFrom.get(Calendar.MONTH)
                yearDiff * 12 + monthDiff + 1
            }

            if (diff > 7) {
                Toast.makeText(requireContext(), "Khoảng thời gian không được vượt quá 7 ${if (mode == "Ngày") "ngày" else "tháng"}", Toast.LENGTH_SHORT).show()
            } else {
                loadChartData()
            }
        } catch (e: ParseException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Lỗi khi xử lý ngày tháng", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseDateInput(value: String, mode: String): Date? {
        return try {
            if (mode == "Ngày") displayDateFormatter.parse(value)
            else {
                val parts = value.split("/")
                if (parts.size == 2) firebaseMonthFormatter.parse("${parts[1]}-${parts[0]}") else null
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

            val value = if (mode == "Ngày") {
                val nextDay = Calendar.getInstance().apply {
                    time = cal.time
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                val prevKey = firebaseDateFormatter.format(cal.time)
                val currKey = firebaseDateFormatter.format(nextDay.time)
                val prev = map[prevKey]
                val curr = map[currKey]
                if (prev != null && curr != null) curr - prev else 0
            } else {
                val filtered = map.filterKeys { it.startsWith(key) }.toSortedMap()
                if (filtered.size >= 2) {
                    val first = filtered.values.first()
                    val last = filtered.values.last()
                    last - first
                } else 0
            }

            entries.add(BarEntry(index, value.toFloat()))
            val label = if (mode == "Ngày") {
                val parts = key.split("-")
                "${parts[2]}/${parts[1]}"
            } else {
                val parts = key.split("-")
                "${parts[1]}/${parts[0]}"
            }
            labels.add(label)
            index++
            if (mode == "Ngày") cal.add(Calendar.DAY_OF_MONTH, 1) else cal.add(Calendar.MONTH, 1)
        }

        val chart = if (isElectric) binding.electricChart else binding.waterChart
        val label = if (isElectric) "Điện (kWh)" else "Nước (m³)"
        val color = if (isElectric) android.R.color.holo_blue_dark else android.R.color.holo_green_dark
        setupChart(chart, entries, labels, label, color)
    }

    private fun setupChart(
        chart: com.github.mikephil.charting.charts.BarChart,
        entries: List<BarEntry>,
        labels: List<String>,
        label: String,
        colorRes: Int
    ) {
        val dataSet = BarDataSet(entries, label)
        dataSet.color = resources.getColor(colorRes, null)
        dataSet.valueTextSize = 12f

        chart.apply {
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            setTouchEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            data = BarData(dataSet)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                setLabelRotationAngle(0f)
            }

            description.text = ""
            legend.isEnabled = true
            legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
            invalidate()
        }
    }

    private fun loadChartData() {
        val phone = auth.currentUser?.phoneNumber ?: return

        val fromDateElectric = binding.fromDateElectric.text.toString()
        val toDateElectric = binding.toDateElectric.text.toString()
        val fromDateWater = binding.fromDateWater.text.toString()
        val toDateWater = binding.toDateWater.text.toString()

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val electricMap = mutableMapOf<String, Int>()
                val waterMap = mutableMapOf<String, Int>()

                for (roomSnapshot in snapshot.children) {
                    val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                    if (phoneInRoom == phone) {
                        val nodesSnapshot = roomSnapshot.child("nodes")

                        for (nodeSnapshot in nodesSnapshot.children) {
                            val historySnapshot = nodeSnapshot.child("history")
                            for (dateSnapshot in historySnapshot.children) {
                                val dateKey = dateSnapshot.key ?: continue

                                val waterValue = dateSnapshot.child("water").getValue(Int::class.java)
                                val electricValue = dateSnapshot.child("electric").getValue(Int::class.java)

                                if (waterValue != null) {
                                    waterMap[dateKey] = (waterMap[dateKey] ?: 0) + waterValue
                                }
                                if (electricValue != null) {
                                    electricMap[dateKey] = (electricMap[dateKey] ?: 0) + electricValue
                                }
                            }
                        }

                        break  // chỉ lấy phòng khớp phone đầu tiên
                    }
                }

                drawChart(electricMap, fromDateElectric, toDateElectric, selectedElectricMode, true)
                drawChart(waterMap, fromDateWater, toDateWater, selectedWaterMode, false)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}