package com.app.buildingmanagement.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.databinding.FragmentHomeBinding
import com.app.buildingmanagement.firebase.FCMHelper
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
    private var fcmTokenSent = false // Flag để tránh gửi token nhiều lần

    companion object {
        private const val TAG = "HomeFragment"
    }

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

        // Đăng ký topic all_residents ngay khi đăng nhập
        subscribeToAllResidents()

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

                            // Gửi FCM token lên Firebase khi đã biết số phòng (chỉ gửi 1 lần)
                            if (!fcmTokenSent && roomNumber != null) {
                                sendFCMTokenToFirebase(roomNumber)
                                fcmTokenSent = true
                            }

                            // Lấy chỉ số hiện tại từ nodes (giữ nguyên như cũ)
                            val nodesSnapshot = roomSnapshot.child("nodes")
                            for (nodeSnapshot in nodesSnapshot.children) {
                                val lastData = nodeSnapshot.child("lastData")
                                val waterValue = lastData.child("water").getValue(Long::class.java)?.toInt()
                                val electricValue = lastData.child("electric").getValue(Long::class.java)?.toInt()

                                if (waterValue != null) latestWater = waterValue
                                if (electricValue != null) latestElectric = electricValue
                            }

                            // Lấy dữ liệu tháng hiện tại từ history
                            val historySnapshot = roomSnapshot.child("history")
                            val monthDates = historySnapshot.children
                                .mapNotNull { it.key }
                                .filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && it.startsWith(currentMonth) }
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
                    Log.e(TAG, "Database error: ${error.message}", error.toException())
                    // Hiển thị lỗi
                    binding.tvRoomNumber.text = "Phòng N/A"
                    binding.tvElectric.text = "0 kWh"
                    binding.tvWater.text = "0 m³"
                    binding.tvElectricUsed.text = "0 kWh"
                    binding.tvWaterUsed.text = "0 m³"
                }
            }

            roomsRef?.addValueEventListener(valueEventListener!!)
        } else {
            Log.w(TAG, "User phone number is null")
        }
    }

    private fun subscribeToAllResidents() {
        // Đăng ký topic all_residents ngay khi đăng nhập
        FCMHelper.subscribeToTopic("all_residents")
        Log.d(TAG, "Subscribed to topic: all_residents")
    }

    private fun sendFCMTokenToFirebase(roomNumber: String) {
        // Lấy FCM token từ SharedPreferences
        val sharedPref = requireActivity().getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("fcm_token", null)

        if (token != null) {
            // Chỉ gửi token và status (đơn giản hóa)
            val fcmData = mapOf(
                "token" to token,
                "status" to "active"
            )

            // Gửi lên Firebase theo đường dẫn rooms/{roomNumber}/FCM
            database.getReference("rooms")
                .child(roomNumber)
                .child("FCM")
                .setValue(fcmData)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token saved successfully for room: $roomNumber")
                    Log.d(TAG, "Token: $token")

                    // Đăng ký topics và gửi topics lên Firebase
                    subscribeToTopicsAndSendToFirebase(roomNumber)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving FCM token for room: $roomNumber", e)
                }
        } else {
            Log.w(TAG, "FCM token not found in SharedPreferences")
            // Thử lấy token mới nếu chưa có
            requestNewFCMToken(roomNumber)
        }
    }

    private fun requestNewFCMToken(roomNumber: String) {
        FCMHelper.getToken { token ->
            if (token != null) {
                // Lưu token mới vào SharedPreferences
                val sharedPref = requireActivity().getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                sharedPref.edit().putString("fcm_token", token).apply()

                // Gửi token lên Firebase
                sendFCMTokenToFirebase(roomNumber)
            } else {
                Log.e(TAG, "Failed to get new FCM token")
            }
        }
    }

    private fun subscribeToTopicsAndSendToFirebase(roomNumber: String) {
        try {
            // Đăng ký topic cho phòng cụ thể
            FCMHelper.subscribeToTopic("room_$roomNumber")

            // Xử lý topic tầng dựa trên chữ số đầu của room
            val floor = roomNumber.substring(0, 1) // Lấy ký tự đầu làm tầng
            val floorTopic = "floor_$floor"
            FCMHelper.subscribeToTopic(floorTopic)

            Log.d(TAG, "Subscribed to topics: room_$roomNumber, $floorTopic")

            // Tạo danh sách topics để gửi lên Firebase
            val topics = listOf(
                "all_residents",
                "room_$roomNumber",
                floorTopic
            )

            // Gửi danh sách topics lên Firebase
            sendTopicsToFirebase(roomNumber, topics)

        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topics", e)
        }
    }

    private fun sendTopicsToFirebase(roomNumber: String, topics: List<String>) {
        // Gửi danh sách topics lên Firebase để web có thể sử dụng
        database.getReference("rooms")
            .child(roomNumber)
            .child("FCM")
            .child("topics")
            .setValue(topics)
            .addOnSuccessListener {
                Log.d(TAG, "Topics sent to Firebase successfully for room: $roomNumber")
                Log.d(TAG, "Topics: $topics")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending topics to Firebase for room: $roomNumber", e)
            }
    }

    private fun updateUI(roomNumber: String?, latestElectric: Int, latestWater: Int, electricUsed: Int, waterUsed: Int) {
        // Cập nhật số phòng
        binding.tvRoomNumber.text = if (roomNumber != null) "Phòng $roomNumber" else "Phòng N/A"

        // Cập nhật chỉ số hiện tại
        binding.tvElectric.text = if (latestElectric != -1) "$latestElectric kWh" else "0 kWh"
        binding.tvWater.text = if (latestWater != -1) "$latestWater m³" else "0 m³"

        // Cập nhật tiêu thụ tháng hiện tại
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

    // Method để refresh FCM token nếu cần
    fun refreshFCMToken() {
        fcmTokenSent = false
        val phone = auth.currentUser?.phoneNumber
        if (phone != null) {
            // Trigger lại việc gửi token
            roomsRef?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (roomSnapshot in snapshot.children) {
                        val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInRoom == phone) {
                            val roomNumber = roomSnapshot.key
                            if (roomNumber != null) {
                                sendFCMTokenToFirebase(roomNumber)
                            }
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error refreshing FCM token", error.toException())
                }
            })
        }
    }
}
