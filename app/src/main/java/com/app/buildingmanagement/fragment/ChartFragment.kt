package com.app.buildingmanagement.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.databinding.FragmentChartBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
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
                loadChartData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.modeSpinnerWater.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedWaterMode = adapter.getItem(position) ?: "Tháng"
                loadChartData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        loadChartData()
    }

    private fun loadChartData() {
        val phone = auth.currentUser?.phoneNumber ?: return

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (roomSnapshot in snapshot.children) {
                    val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                    if (phoneInRoom == phone) {
                        val electricHistory = roomSnapshot.child("electricHistory")
                        val waterHistory = roomSnapshot.child("waterHistory")

                        val electricMap = electricHistory.children
                            .filter { it.key != null }
                            .associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }

                        val waterMap = waterHistory.children
                            .filter { it.key != null }
                            .associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }

                        // ========= ĐIỆN =========
                        val electricEntries = mutableListOf<BarEntry>()
                        val electricLabels = mutableListOf<String>()

                        if (selectedElectricMode == "Tháng") {
                            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                            val cal = Calendar.getInstance()
                            val months = mutableListOf<String>()
                            for (i in 4 downTo 0) {
                                val clone = cal.clone() as Calendar
                                clone.set(Calendar.DAY_OF_MONTH, 1)
                                clone.add(Calendar.MONTH, -i)
                                months.add(monthFormat.format(clone.time))
                            }

                            var index = 0f
                            for (month in months) {
                                val monthData = electricMap.filterKeys { it.startsWith(month) }.toSortedMap()
                                val dates = monthData.keys.sorted()
                                val diff = if (dates.size >= 2) {
                                    val start = monthData[dates.first()] ?: 0
                                    val end = monthData[dates.last()] ?: 0
                                    end - start
                                } else 0
                                electricEntries.add(BarEntry(index, diff.toFloat()))

                                val parts = month.split("-")
                                val formattedMonth = "${parts[1]}/${parts[0]}"
                                electricLabels.add(formattedMonth)

                                index += 1f
                            }

                        } else {
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val recentDates = mutableListOf<String>()
                            val today = Calendar.getInstance()
                            today.add(Calendar.DAY_OF_MONTH, -5)

                            for (i in 0 until 6) {
                                recentDates.add(formatter.format(today.time))
                                today.add(Calendar.DAY_OF_MONTH, 1)
                            }

                            var index = 0f
                            for (i in 0 until recentDates.size - 1) {
                                val prevKey = recentDates[i]
                                val currKey = recentDates[i + 1]
                                val prev = electricMap[prevKey]
                                val curr = electricMap[currKey]
                                val diff = if (prev != null && curr != null) curr - prev else 0
                                electricEntries.add(BarEntry(index, diff.toFloat()))

                                val parts = prevKey.split("-")
                                val formattedDay = "${parts[2]}/${parts[1]}"
                                electricLabels.add(formattedDay)

                                index += 1f
                            }
                        }

                        setupChart(binding.electricChart, electricEntries, electricLabels, "Điện (kWh)", android.R.color.holo_blue_dark)

                        // ========= NƯỚC =========
                        val waterEntries = mutableListOf<BarEntry>()
                        val waterLabels = mutableListOf<String>()

                        if (selectedWaterMode == "Tháng") {
                            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                            val cal = Calendar.getInstance()
                            val months = mutableListOf<String>()
                            for (i in 4 downTo 0) {
                                val clone = cal.clone() as Calendar
                                clone.set(Calendar.DAY_OF_MONTH, 1)
                                clone.add(Calendar.MONTH, -i)
                                months.add(monthFormat.format(clone.time))
                            }

                            var index = 0f
                            for (month in months) {
                                val monthData = waterMap.filterKeys { it.startsWith(month) }.toSortedMap()
                                val dates = monthData.keys.sorted()
                                val diff = if (dates.size >= 2) {
                                    val start = monthData[dates.first()] ?: 0
                                    val end = monthData[dates.last()] ?: 0
                                    end - start
                                } else 0
                                waterEntries.add(BarEntry(index, diff.toFloat()))

                                val parts = month.split("-")
                                val formattedMonth = "${parts[1]}/${parts[0]}"
                                waterLabels.add(formattedMonth)

                                index += 1f
                            }

                        } else {
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val recentDates = mutableListOf<String>()
                            val today = Calendar.getInstance()
                            today.add(Calendar.DAY_OF_MONTH, -5)

                            for (i in 0 until 6) {
                                recentDates.add(formatter.format(today.time))
                                today.add(Calendar.DAY_OF_MONTH, 1)
                            }

                            var index = 0f
                            for (i in 0 until recentDates.size - 1) {
                                val prevKey = recentDates[i]
                                val currKey = recentDates[i + 1]
                                val prev = waterMap[prevKey]
                                val curr = waterMap[currKey]
                                val diff = if (prev != null && curr != null) curr - prev else 0
                                waterEntries.add(BarEntry(index, diff.toFloat()))

                                val parts = prevKey.split("-")
                                val formattedDay = "${parts[2]}/${parts[1]}"
                                waterLabels.add(formattedDay)

                                index += 1f
                            }
                        }

                        setupChart(binding.waterChart, waterEntries, waterLabels, "Nước (m³)", android.R.color.holo_green_dark)

                        break
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupChart(
        chart: com.github.mikephil.charting.charts.BarChart,
        entries: List<BarEntry>,
        labels: List<String>,
        label: String,
        colorRes: Int
    ) {
        val dataSet = BarDataSet(entries, label)
        dataSet.color = resources.getColor(colorRes)
        dataSet.valueTextSize = 12f

        chart.apply {
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f // ✅ fix cho cột 0 nằm ngang trục
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
