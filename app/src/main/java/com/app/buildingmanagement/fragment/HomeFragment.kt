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

        val currentMonth = SimpleDateFormat("M", Locale.getDefault()).format(Date())
        binding.tvUsedMonth.text = "Tiêu thụ tháng $currentMonth"
        super.onViewCreated(view, savedInstanceState)

        val phone = auth.currentUser?.phoneNumber
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val yearMonth = yearMonthFormat.format(Date())

        if (phone != null) {
            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var electric = -1
                    var water = -1
                    var electricStart = -1
                    var waterStart = -1

                    fun findEarliestInMonth(historySnapshot: DataSnapshot, yearMonth: String): Int? {
                        val keys = historySnapshot.children.mapNotNull { it.key }
                        val matchingDates = keys.filter { it.startsWith(yearMonth) }.sorted()
                        if (matchingDates.isNotEmpty()) {
                            return historySnapshot.child(matchingDates.first()).getValue()?.toString()?.toIntOrNull()
                        }
                        return null
                    }

                    for (roomSnapshot in snapshot.children) {
                        val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInRoom == phone) {
                            electric = roomSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: -1
                            water = roomSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: -1

                            roomSnapshot.ref.child("electricHistory").child(today).setValue(electric)
                            roomSnapshot.ref.child("waterHistory").child(today).setValue(water)

                            electricStart = findEarliestInMonth(roomSnapshot.child("electricHistory"), yearMonth) ?: electric
                            waterStart = findEarliestInMonth(roomSnapshot.child("waterHistory"), yearMonth) ?: water

                            break
                        }
                    }

                    val electricUsed = if (electricStart != -1) electric - electricStart else 0
                    val waterUsed = if (waterStart != -1) water - waterStart else 0

                    binding.tvElectric.text = "Điện: $electric kWh"
                    binding.tvWater.text = "Nước: $water m³"
                    binding.tvElectricUsed.text = "Đã dùng: $electricUsed kWh"
                    binding.tvWaterUsed.text = "Đã dùng: $waterUsed m³"
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