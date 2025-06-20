package com.app.buildingmanagement.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.R
import com.app.buildingmanagement.databinding.FragmentHomeBinding
import com.app.buildingmanagement.firebase.FCMHelper
import com.app.buildingmanagement.data.SharedDataManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var topicsInfoSent = false
    private var isDataLoaded = false // Track whether data has been loaded
    private var hasCheckedNotificationPermission = false // Track notification permission check

    companion object {
        private const val TAG = "HomeFragment"
        private const val NOTIFICATION_PERMISSION_PREF = "notification_permission_requested"
    }

    // Permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handleNotificationPermissionResult(isGranted)
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
        binding.tvRoomNumber.text = getString(R.string.loading_text)
        binding.tvElectric.text = getString(R.string.loading_value_kwh)
        binding.tvWater.text = getString(R.string.loading_value_m3)
        binding.tvElectricUsed.text = getString(R.string.loading_value_kwh)
        binding.tvWaterUsed.text = getString(R.string.loading_value_m3)
        
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
        binding.tvRoomNumber.text = getString(R.string.error_connection)
        binding.tvElectric.text = getString(R.string.zero_kwh)
        binding.tvWater.text = getString(R.string.zero_m3)
        binding.tvElectricUsed.text = getString(R.string.zero_kwh)
        binding.tvWaterUsed.text = getString(R.string.zero_m3)
    }

    private fun processDataSnapshot(snapshot: DataSnapshot, fromCache: Boolean) {
        Log.d(TAG, "Processing data snapshot (fromCache: $fromCache)")
        
        val phone = auth.currentUser?.phoneNumber ?: return

        var latestElectric = -1
        var latestWater = -1
        var roomNumber: String? = null

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val prevMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }
        val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevMonth.time)

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

                // Gửi topics info lên Firebase (luôn gửi để web có thể targeting)
                if (!topicsInfoSent) {
                    sendTopicsInfoToFirebase(roomNumber)
                    topicsInfoSent = true
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

                // Tính consumption theo logic mới (giống ChartFragment)
                val historySnapshot = roomSnapshot.child("history")
                
                // Lấy tất cả dữ liệu history
                val allHistoryData = mutableMapOf<String, Pair<Int?, Int?>>() // date -> (electric, water)
                for (dateSnapshot in historySnapshot.children) {
                    val dateKey = dateSnapshot.key ?: continue
                    val electric = dateSnapshot.child("electric").getValue(Long::class.java)?.toInt()
                    val water = dateSnapshot.child("water").getValue(Long::class.java)?.toInt()
                    allHistoryData[dateKey] = Pair(electric, water)
                }
                
                // Tính consumption cho điện
                val electricUsed = calculateMonthlyConsumption(allHistoryData, currentMonth, prevMonthKey, true)
                
                // Tính consumption cho nước  
                val waterUsed = calculateMonthlyConsumption(allHistoryData, currentMonth, prevMonthKey, false)

                // Hide loading state và update UI
                hideLoadingState()
                updateUI(roomNumber, latestElectric, latestWater, electricUsed, waterUsed, fromCache)
                isDataLoaded = true
                break
            }
        }
    }

    /**
     * Tính consumption tháng theo logic giống ChartFragment
     */
    private fun calculateMonthlyConsumption(
        allHistoryData: Map<String, Pair<Int?, Int?>>, 
        currentMonth: String, 
        prevMonthKey: String, 
        isElectric: Boolean
    ): Int {
        val type = if (isElectric) "ELECTRIC" else "WATER"
        Log.d(TAG, "=== Calculating $type consumption for month $currentMonth ===")
        
        // Lấy dữ liệu tháng hiện tại
        val currentMonthData = allHistoryData
            .filterKeys { it.startsWith(currentMonth) }
            .toSortedMap()
        
        // Lấy dữ liệu tháng trước
        val prevMonthData = allHistoryData
            .filterKeys { it.startsWith(prevMonthKey) }
            .toSortedMap()
        
        Log.d(TAG, "$type - Current month data: $currentMonthData")
        Log.d(TAG, "$type - Previous month data: $prevMonthData")
        
        // Lấy giá trị cần thiết
        val currentValues = currentMonthData.values.mapNotNull { 
            if (isElectric) it.first else it.second 
        }
        val prevValues = prevMonthData.values.mapNotNull { 
            if (isElectric) it.first else it.second 
        }
        
        Log.d(TAG, "$type - Current month values: $currentValues")
        Log.d(TAG, "$type - Previous month values: $prevValues")
        
        if (currentValues.isEmpty()) {
            Log.d(TAG, "$type - No current month data, returning 0")
            return 0
        }
        
        val currentMaxValue = currentValues.maxOrNull() ?: 0
        val prevMonthLastValue = prevValues.lastOrNull()
        
        Log.d(TAG, "$type - Current max value: $currentMaxValue")
        Log.d(TAG, "$type - Previous month last value: $prevMonthLastValue")
        
        return if (prevMonthLastValue != null) {
            // Trường hợp bình thường: cao nhất tháng này - cuối tháng trước
            val result = currentMaxValue - prevMonthLastValue
            Log.d(TAG, "$type - Normal case: $currentMaxValue - $prevMonthLastValue = $result")
            result
        } else {
            // Trường hợp đặc biệt: cao nhất tháng này - thấp nhất tháng này
            val currentMinValue = currentValues.minOrNull() ?: 0
            val result = currentMaxValue - currentMinValue
            Log.d(TAG, "$type - Special case: $currentMaxValue - $currentMinValue = $result")
            result
        }
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

    /**
     * GỬI TOPICS INFO LÊN FIREBASE ĐỂ WEB CÓ THỂ TARGET
     * LUÔN GỬI BẤT KỂ USER CÓ ĐỒNG Ý HAY KHÔNG
     */
    private fun sendTopicsInfoToFirebase(roomNumber: String) {
        try {
            Log.d(TAG, "Sending topics info to Firebase for room: $roomNumber")

            // Tạo danh sách topics để gửi lên Firebase
            val topics = listOf(
                "all_residents",
                "room_$roomNumber",
                "floor_${roomNumber.substring(0, 1)}"
            )

            // Gửi danh sách topics lên Firebase để web có thể target
            sendTopicsToFirebase(roomNumber, topics)

        } catch (e: Exception) {
            Log.e(TAG, "Error sending topics info", e)
        }
    }

    private fun sendTopicsToFirebase(roomNumber: String, topics: List<String>) {
        // Gửi danh sách topics lên Firebase để web có thể targeting
        database.getReference("rooms")
            .child(roomNumber)
            .child("FCM")
            .child("topics")
            .setValue(topics)
            .addOnSuccessListener {
                Log.d(TAG, "Topics info sent to Firebase successfully for room: $roomNumber")
                Log.d(TAG, "Topics: $topics")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending topics info to Firebase for room: $roomNumber", e)
            }
    }

    private fun updateUI(roomNumber: String?, latestElectric: Int, latestWater: Int, electricUsed: Int, waterUsed: Int, fromCache: Boolean) {
        Log.d(TAG, "Updating UI for room: $roomNumber (fromCache: $fromCache)")
        
        // Cập nhật số phòng
        binding.tvRoomNumber.text = if (roomNumber != null) {
            getString(R.string.room_number, roomNumber)
        } else {
            getString(R.string.room_na)
        }

        // Cập nhật chỉ số hiện tại
        binding.tvElectric.text = if (latestElectric != -1) {
            getString(R.string.value_kwh, latestElectric)
        } else {
            getString(R.string.zero_kwh)
        }
        
        binding.tvWater.text = if (latestWater != -1) {
            getString(R.string.value_m3, latestWater)
        } else {
            getString(R.string.zero_m3)
        }

        // Cập nhật tiêu thụ tháng hiện tại
        binding.tvElectricUsed.text = getString(R.string.value_kwh, electricUsed)
        binding.tvWaterUsed.text = getString(R.string.value_m3, waterUsed)
        
        // Log để debug
        val dataSource = if (fromCache) "CACHE" else "FIREBASE"
        Log.d(TAG, "UI updated from $dataSource: Room=$roomNumber, Electric=$latestElectric/$electricUsed, Water=$latestWater/$waterUsed")
        
        // Hỏi quyền thông báo sau khi UI đã được cập nhật và chỉ một lần
        if (!hasCheckedNotificationPermission && !fromCache && roomNumber != null) {
            hasCheckedNotificationPermission = true
            checkNotificationPermissionAfterDataLoad()
        }
    }

    private fun checkNotificationPermissionAfterDataLoad() {
        // Chỉ cần check từ Android 13 (API 33) trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sharedPref = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val hasRequestedBefore = sharedPref.getBoolean(NOTIFICATION_PERMISSION_PREF, false)

            // KIỂM TRA CẢ SYSTEM PERMISSION VÀ USER PREFERENCE
            val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val userEnabledNotifications = appSettings.getBoolean("notifications_enabled", true)

            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // ĐÃ CÓ SYSTEM PERMISSION - KIỂM TRA USER PREFERENCE
                    Log.d(TAG, "System permission granted. User preference: $userEnabledNotifications")
                    
                    if (userEnabledNotifications) {
                        // User muốn nhận thông báo - subscribe topics
                        Log.d(TAG, "User wants notifications - subscribing to topics")
                        val roomNumber = SharedDataManager.getCachedRoomNumber()
                        if (roomNumber != null) {
                            FCMHelper.subscribeToUserBuildingTopics(roomNumber)
                            Log.d(TAG, "Auto-subscribed to topics for room: $roomNumber")
                        }
                    } else {
                        // User đã tắt thông báo trong Settings - tôn trọng lựa chọn
                        Log.d(TAG, "User disabled notifications in Settings - respecting preference")
                    }
                }
                hasRequestedBefore -> {
                    // Đã hỏi trước đó và bị từ chối, không hỏi lại nữa
                    Log.d(TAG, "Notification permission was denied before, not asking again")
                }
                else -> {
                    // Chưa hỏi bao giờ, hiển thị dialog giải thích sau 2 giây để user có thời gian hiểu UI
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isAdded && _binding != null) {
                            showNotificationPermissionDialog()
                        }
                    }, 2000) // Delay 2 giây
                }
            }
        } else {
            // Android < 13 - không cần permission, kiểm tra user preference
            Log.d(TAG, "Android version < 13, checking user preference")
            
            val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val userEnabledNotifications = appSettings.getBoolean("notifications_enabled", true)
            
            if (userEnabledNotifications) {
                Log.d(TAG, "User wants notifications (Android < 13) - subscribing")
                val roomNumber = SharedDataManager.getCachedRoomNumber()
                if (roomNumber != null) {
                    FCMHelper.subscribeToUserBuildingTopics(roomNumber)
                    Log.d(TAG, "Auto-subscribed to topics for room: $roomNumber (Android < 13)")
                }
            } else {
                Log.d(TAG, "User disabled notifications (Android < 13) - not subscribing")
            }
        }
    }

    private fun showNotificationPermissionDialog() {
        if (!isAdded || _binding == null) return

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.notification_permission_title))
            .setMessage(getString(R.string.notification_permission_message))
            .setPositiveButton(getString(R.string.notification_permission_allow)) { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton(getString(R.string.notification_permission_deny)) { _, _ ->
                handleNotificationPermissionDenied()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Đảm bảo màu nút hiển thị rõ ràng
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.nav_selected))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.nav_unselected))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleNotificationPermissionResult(isGranted: Boolean) {
        if (!isAdded) return

        // Lưu trạng thái đã hỏi quyền vào app_prefs
        val appPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().putBoolean(NOTIFICATION_PERMISSION_PREF, true).apply()

        if (isGranted) {
            Log.d(TAG, "Notification permission granted - subscribing to topics")

            // LƯU VÀO CÙNG SHARED PREFS VỚI SETTINGS FRAGMENT
            val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            appSettings.edit().putBoolean("notifications_enabled", true).apply()

            // SUBSCRIBE VÀO TOPICS KHI USER ĐỒNG Ý
            val roomNumber = SharedDataManager.getCachedRoomNumber()
            if (roomNumber != null) {
                FCMHelper.subscribeToUserBuildingTopics(roomNumber)
                Log.d(TAG, "Subscribed to topics for room: $roomNumber")
            }

            Toast.makeText(requireContext(), getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "Notification permission denied")
            handleNotificationPermissionDenied()
        }
    }

    private fun handleNotificationPermissionDenied() {
        if (!isAdded) return

        // Lưu trạng thái đã hỏi quyền vào app_prefs
        val appPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().putBoolean(NOTIFICATION_PERMISSION_PREF, true).apply()

        // LƯU VÀO CÙNG SHARED PREFS VỚI SETTINGS FRAGMENT
        val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appSettings.edit().putBoolean("notifications_enabled", false).apply()

        // KHÔNG SUBSCRIBE TOPICS KHI USER TỪ CHỐI
        Log.d(TAG, "Notification permission denied - not subscribing to topics")

        Toast.makeText(requireContext(), getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
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
}