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
import com.app.buildingmanagement.data.SharedDataManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), SharedDataManager.DataUpdateListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var valueEventListener: ValueEventListener? = null
    private var roomsRef: DatabaseReference? = null
    private var fcmTokenSent = false
    private var isDataLoaded = false // Track whether data has been loaded

    companion object {
        private const val TAG = "HomeFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        roomsRef = database.getReference("rooms")
        
        // Register as listener cho SharedDataManager
        SharedDataManager.addListener(this)
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

        Log.d(TAG, "HomeFragment loaded - checking for cached data first")

        // 1. KIỂM TRA CACHE TRƯỚC
        val cachedSnapshot = SharedDataManager.getCachedRoomSnapshot()
        val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()
        
        if (cachedSnapshot != null && cachedRoomNumber != null) {
            Log.d(TAG, "✅ Using cached data immediately for room: $cachedRoomNumber")
            // Hiển thị dữ liệu từ cache ngay lập tức
            processDataSnapshot(cachedSnapshot, fromCache = true)
            isDataLoaded = true
        } else {
            Log.d(TAG, "❌ No cached data available - showing loading state")
            showLoadingState()
        }

        // 2. LUÔN LUÔN LOAD FRESH DATA TỪ FIREBASE (update nếu có thay đổi)
        loadFreshDataFromFirebase()
    }

    private fun showLoadingState() {
        Log.d(TAG, "Showing loading state...")
        
        // Hiển thị skeleton loading thay vì dữ liệu mặc định
        binding.tvRoomNumber.text = "Đang tải..."
        binding.tvElectric.text = "-- kWh"
        binding.tvWater.text = "-- m³"
        binding.tvElectricUsed.text = "-- kWh"
        binding.tvWaterUsed.text = "-- m³"
        
        // Có thể thêm shimmer effect hoặc progress indicator ở đây
        binding.tvRoomNumber.alpha = 0.6f
        binding.tvElectric.alpha = 0.6f
        binding.tvWater.alpha = 0.6f
        binding.tvElectricUsed.alpha = 0.6f
        binding.tvWaterUsed.alpha = 0.6f
    }

    private fun hideLoadingState() {
        Log.d(TAG, "Hiding loading state...")
        
        // Restore normal appearance
        binding.tvRoomNumber.alpha = 1.0f
        binding.tvElectric.alpha = 1.0f
        binding.tvWater.alpha = 1.0f
        binding.tvElectricUsed.alpha = 1.0f
        binding.tvWaterUsed.alpha = 1.0f
    }

    private fun loadFreshDataFromFirebase() {
        val phone = auth.currentUser?.phoneNumber

        if (phone != null) {
            Log.d(TAG, "Loading fresh data from Firebase for phone: $phone")
            
            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Fresh data received from Firebase")
                    processDataSnapshot(snapshot, fromCache = false)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase error: ${error.message}", error.toException())
                    hideLoadingState()
                    showErrorState()
                }
            }

            roomsRef?.addValueEventListener(valueEventListener!!)
        } else {
            Log.w(TAG, "User phone number is null")
            hideLoadingState()
            showErrorState()
        }
    }

    private fun showErrorState() {
        binding.tvRoomNumber.text = "Lỗi kết nối"
        binding.tvElectric.text = "0 kWh"
        binding.tvWater.text = "0 m³"
        binding.tvElectricUsed.text = "0 kWh"
        binding.tvWaterUsed.text = "0 m³"
    }

    private fun processDataSnapshot(snapshot: DataSnapshot, fromCache: Boolean) {
        Log.d(TAG, "Processing data snapshot (fromCache: $fromCache)")
        
        val phone = auth.currentUser?.phoneNumber ?: return

        var latestElectric = -1
        var latestWater = -1
        var startOfMonthElectric: Int? = null
        var startOfMonthWater: Int? = null
        var endOfMonthElectric: Int? = null
        var endOfMonthWater: Int? = null
        var roomNumber: String? = null

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        // Duyệt qua tất cả các phòng
        for (roomSnapshot in snapshot.children) {
            val tenantsSnapshot = roomSnapshot.child("tenants")
            var phoneFound = false

            // Duyệt qua tất cả các thành viên trong phòng
            for (tenantSnapshot in tenantsSnapshot.children) {
                val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                if (phoneInTenant == phone) {
                    phoneFound = true
                    roomNumber = roomSnapshot.key
                    Log.d(TAG, "Found matching phone in room: $roomNumber")
                    break
                }
            }

            if (phoneFound && roomNumber != null) {
                // Chỉ update cache nếu đây là fresh data từ Firebase
                if (!fromCache) {
                    SharedDataManager.setCachedData(roomSnapshot, roomNumber, phone)
                    Log.d(TAG, "Cache updated with fresh data for room: $roomNumber")
                }

                // Gửi FCM token nếu chưa gửi
                if (!fcmTokenSent) {
                    sendFCMTokenToFirebase(roomNumber)
                    fcmTokenSent = true
                }

                // Lấy chỉ số hiện tại từ nodes
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

        // Hide loading state và update UI
        hideLoadingState()
        updateUI(roomNumber, latestElectric, latestWater, electricUsed, waterUsed, fromCache)
        isDataLoaded = true
    }

    // Implement DataUpdateListener methods
    override fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String) {
        Log.d(TAG, "Data updated callback received for room: $roomNumber")
        // Process updated data nếu fragment đang active
        if (isAdded && _binding != null) {
            processDataSnapshot(roomSnapshot, fromCache = false)
        }
    }

    override fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String) {
        Log.d(TAG, "Cache ready callback received for room: $roomNumber")
        // Chỉ process nếu chưa load data và fragment đang active
        if (isAdded && _binding != null && !isDataLoaded) {
            processDataSnapshot(roomSnapshot, fromCache = true)
        }
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

                    // CHỈ GỬI TOPICS INFO LÊN FIREBASE ĐỂ WEB BIẾT - KHÔNG SUBSCRIBE
                    sendTopicsInfoToFirebase(roomNumber)
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

    /**
     * CHỈ GỬI TOPICS INFO LÊN FIREBASE ĐỂ WEB CÓ THỂ TARGET
     * KHÔNG TỰ ĐỘNG SUBSCRIBE TOPICS
     */
    private fun sendTopicsInfoToFirebase(roomNumber: String) {
        try {
            Log.d(TAG, "Sending topics info to Firebase for room: $roomNumber (subscription handled elsewhere)")

            // Tạo danh sách topics để gửi lên Firebase
            val topics = listOf(
                "all_residents",
                "room_$roomNumber",
                "floor_${roomNumber.substring(0, 1)}"
            )

            // Chỉ gửi danh sách topics lên Firebase để web có thể target
            sendTopicsToFirebase(roomNumber, topics)

        } catch (e: Exception) {
            Log.e(TAG, "Error sending topics info", e)
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
                Log.d(TAG, "Topics info sent to Firebase successfully for room: $roomNumber")
                Log.d(TAG, "Topics info: $topics")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending topics info to Firebase for room: $roomNumber", e)
            }
    }

    private fun updateUI(roomNumber: String?, latestElectric: Int, latestWater: Int, electricUsed: Int, waterUsed: Int, fromCache: Boolean) {
        Log.d(TAG, "Updating UI for room: $roomNumber (fromCache: $fromCache)")
        
        // Cập nhật số phòng
        binding.tvRoomNumber.text = if (roomNumber != null) "Phòng $roomNumber" else "Phòng N/A"

        // Cập nhật chỉ số hiện tại
        binding.tvElectric.text = if (latestElectric != -1) "$latestElectric kWh" else "0 kWh"
        binding.tvWater.text = if (latestWater != -1) "$latestWater m³" else "0 m³"

        // Cập nhật tiêu thụ tháng hiện tại
        binding.tvElectricUsed.text = "$electricUsed kWh"
        binding.tvWaterUsed.text = "$waterUsed m³"
        
        // Log để debug
        val dataSource = if (fromCache) "CACHE" else "FIREBASE"
        Log.d(TAG, "UI updated from $dataSource: Room=$roomNumber, Electric=$latestElectric/$electricUsed, Water=$latestWater/$waterUsed")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Remove Firebase listener
        valueEventListener?.let {
            roomsRef?.removeEventListener(it)
        }
        
        // Remove from SharedDataManager
        SharedDataManager.removeListener(this)
        
        _binding = null
        Log.d(TAG, "HomeFragment destroyed and cleaned up")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure cleanup
        SharedDataManager.removeListener(this)
    }

    // Method để refresh FCM token nếu cần
    fun refreshFCMToken() {
        fcmTokenSent = false
        val phone = auth.currentUser?.phoneNumber
        if (phone != null) {
            // Trigger lại việc gửi token
            roomsRef?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Duyệt qua tất cả các phòng
                    for (roomSnapshot in snapshot.children) {
                        val tenantsSnapshot = roomSnapshot.child("tenants")
                        var phoneFound = false

                        // Duyệt qua tất cả các thành viên trong phòng
                        for (tenantSnapshot in tenantsSnapshot.children) {
                            val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                            if (phoneInTenant == phone) {
                                phoneFound = true
                                val roomNumber = roomSnapshot.key
                                if (roomNumber != null) {
                                    sendFCMTokenToFirebase(roomNumber)
                                }
                                break
                            }
                        }

                        if (phoneFound) {
                            break // Thoát khỏi vòng lặp rooms khi đã tìm thấy
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