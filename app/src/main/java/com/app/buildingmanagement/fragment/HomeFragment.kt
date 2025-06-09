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

        val phone = auth.currentUser?.phoneNumber

        if (phone != null) {
            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var latestElectric = -1
                    var latestWater = -1
                    var startOfMonthElectric: Int? = null
                    var startOfMonthWater: Int? = null
                    var endOfMonthElectric: Int? = null
                    var endOfMonthWater: Int? = null
                    var roomNumber: String? = null

                    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                    for (roomSnapshot in snapshot.children) {
                        val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInRoom == phone) {
                            // Lấy số phòng từ key của room
                            roomNumber = roomSnapshot.key

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
                                    val lastDay = monthDates.last()

                                    val firstSnapshot = historySnapshot.child(firstDay)
                                    val lastSnapshot = historySnapshot.child(lastDay)

                                    val firstElectric = firstSnapshot.child("electric").getValue(Long::class.java)?.toInt()
                                    val lastElectric = lastSnapshot.child("electric").getValue(Long::class.java)?.toInt()

                                    val firstWater = firstSnapshot.child("water").getValue(Long::class.java)?.toInt()
                                    val lastWater = lastSnapshot.child("water").getValue(Long::class.java)?.toInt()

                                    if (firstElectric != null && lastElectric != null) {
                                        startOfMonthElectric = firstElectric
                                        endOfMonthElectric = lastElectric
                                    }

                                    if (firstWater != null && lastWater != null) {
                                        startOfMonthWater = firstWater
                                        endOfMonthWater = lastWater
                                    }
                                }
                            }
                            break
                        }
                    }

                    // Tính toán tiêu thụ tháng hiện tại
                    val electricUsed = if (startOfMonthElectric != null && endOfMonthElectric != null)
                        endOfMonthElectric - startOfMonthElectric else 0

                    val waterUsed = if (startOfMonthWater != null && endOfMonthWater != null)
                        endOfMonthWater - startOfMonthWater else 0

                    // Cập nhật UI với dữ liệu mới
                    updateUI(roomNumber, latestElectric, latestWater, electricUsed, waterUsed)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Hiển thị lỗi
                    binding.tvRoomNumber.text = "Phòng N/A"
                    binding.tvElectric.text = "0 kWh"
                    binding.tvWater.text = "0 m³"
                    binding.tvElectricUsed.text = "0 kWh"
                    binding.tvWaterUsed.text = "0 m³"
                }
            }

            roomsRef?.addValueEventListener(valueEventListener!!)
        }
    }

    private fun updateUI(roomNumber: String?, latestElectric: Int, latestWater: Int, electricUsed: Int, waterUsed: Int) {
        // Cập nhật số phòng
        binding.tvRoomNumber.text = if (roomNumber != null) "Phòng $roomNumber" else "Phòng N/A"

        // Cập nhật chỉ số hiện tại (loại bỏ text mô tả dài)
        binding.tvElectric.text = if (latestElectric != -1) "$latestElectric kWh" else "0 kWh"
        binding.tvWater.text = if (latestWater != -1) "$latestWater m³" else "0 m³"

        // Cập nhật tiêu thụ tháng hiện tại (loại bỏ text dài)
        binding.tvElectricUsed.text = "$electricUsed kWh"
        binding.tvWaterUsed.text = "$waterUsed m³"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        valueEventListener?.let {
            roomsRef?.removeEventListener(it)
        }
        _binding = null
    }
}
