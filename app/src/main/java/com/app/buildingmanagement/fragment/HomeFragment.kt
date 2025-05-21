package com.app.buildingmanagement.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var valueEventListener: ValueEventListener? = null
    private var roomsRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        roomsRef = database.getReference("rooms")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        binding.tvUsedMonth.text = "Tiêu thụ tháng ${currentMonth.split("-")[1].toInt()}"

        val phone = auth.currentUser?.phoneNumber

        if (phone != null) {
            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var latestElectric = -1
                    var latestWater = -1
                    var startOfMonthElectric: Int? = null
                    var startOfMonthWater: Int? = null

                    for (roomSnapshot in snapshot.children) {
                        val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInRoom == phone) {
                            val nodesSnapshot = roomSnapshot.child("nodes")

                            for (nodeSnapshot in nodesSnapshot.children) {
                                val lastData = nodeSnapshot.child("lastData")
                                val waterValue = lastData.child("water").getValue(Long::class.java)?.toInt()
                                val electricValue = lastData.child("electric").getValue(Long::class.java)?.toInt()

                                if (waterValue != null) latestWater = waterValue
                                if (electricValue != null) latestElectric = electricValue

                                val historySnapshot = nodeSnapshot.child("history")
                                val monthDates = historySnapshot.children
                                    .mapNotNull { it.key }
                                    .filter { it.startsWith(currentMonth) }
                                    .sorted()

                                if (monthDates.isNotEmpty()) {
                                    val firstDay = monthDates.first()
                                    val firstDaySnapshot = historySnapshot.child(firstDay)

                                    val firstWater = firstDaySnapshot.child("water").getValue(Long::class.java)?.toInt()
                                    val firstElectric = firstDaySnapshot.child("electric").getValue(Long::class.java)?.toInt()

                                    if (firstWater != null && startOfMonthWater == null) startOfMonthWater = firstWater
                                    if (firstElectric != null && startOfMonthElectric == null) startOfMonthElectric = firstElectric
                                }
                            }
                            break
                        }
                    }

                    val electricUsed = if (latestElectric != -1 && startOfMonthElectric != null) latestElectric - startOfMonthElectric else 0
                    val waterUsed = if (latestWater != -1 && startOfMonthWater != null) latestWater - startOfMonthWater else 0

                    binding.tvElectric.text = if (latestElectric != -1) "Điện hiện tại: $latestElectric kWh" else "Điện hiện tại: N/A"
                    binding.tvWater.text = if (latestWater != -1) "Nước hiện tại: $latestWater m³" else "Nước hiện tại: N/A"
                    binding.tvElectricUsed.text = "Tổng tiêu thụ tháng: $electricUsed kWh"
                    binding.tvWaterUsed.text = "Tổng tiêu thụ tháng: $waterUsed m³"
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.tvElectric.text = "Lỗi"
                    binding.tvWater.text = "Lỗi"
                    binding.tvElectricUsed.text = "Lỗi"
                    binding.tvWaterUsed.text = "Lỗi"
                }
            }

            roomsRef?.addValueEventListener(valueEventListener!!)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        valueEventListener?.let {
            roomsRef?.removeEventListener(it)
        }
        _binding = null
    }
}
