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

    private var auth: FirebaseAuth? = null
    private var database: FirebaseDatabase? = null
    private var roomsRef: DatabaseReference? = null
    private var valueEventListener: ValueEventListener? = null

    // State management
    private var fcmTokenSent = false
    private var currentRoomNumber: String? = null
    private var isFragmentActive = false
    private var currentPhoneNumber: String? = null // Track current user

    companion object {
        private const val TAG = "HomeFragment"
        private const val FCM_PREFS = "fcm_prefs"
        private const val FCM_TOKEN_KEY = "fcm_token"

        // User-specific cache keys
        private const val CACHE_PREFS = "home_data_cache"
        private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L

        // Rate limiting
        private const val MIN_UPDATE_INTERVAL = 1000L
        private var lastUpdateTime = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance()
            roomsRef = database?.getReference("rooms")
            currentPhoneNumber = auth?.currentUser?.phoneNumber
            Log.d(TAG, "HomeFragment created for user: ${currentPhoneNumber?.take(10)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            _binding = FragmentHomeBinding.inflate(inflater, container, false)
            binding.root
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating layout", e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isFragmentActive = true

        // Check if user changed and handle cache accordingly
        checkUserChangeAndCache()

        // Load user-specific cached data INSTANTLY
        loadUserSpecificCachedData()

        // Subscribe to topics
        subscribeToAllResidents()

        // Setup data loading
        setupDataLoading()
    }

    /**
     * Check if user changed since last app open and clear cache if needed
     */
    private fun checkUserChangeAndCache() {
        try {
            val currentUser = currentPhoneNumber
            val context = requireContext()
            val sharedPref = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val lastUser = sharedPref.getString("last_logged_user", null)

            if (lastUser != null && lastUser != currentUser) {
                Log.w(TAG, "⚠️ User changed from ${lastUser?.take(10)}... to ${currentUser?.take(10)}... - Clearing old cache")
                clearAllUserCaches()
            }

            // Update last logged user
            sharedPref.edit().putString("last_logged_user", currentUser).apply()

        } catch (e: Exception) {
            Log.e(TAG, "Error checking user change", e)
        }
    }

    /**
     * Clear all cached data when user changes
     */
    private fun clearAllUserCaches() {
        try {
            val context = requireContext()
            val sharedPref = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

            // Get all keys and remove user-specific cache data (but keep last_logged_user)
            val allKeys = sharedPref.all.keys
            val editor = sharedPref.edit()

            allKeys.forEach { key ->
                if (key != "last_logged_user") {
                    editor.remove(key)
                }
            }

            editor.apply()
            Log.d(TAG, "✅ All user caches cleared")

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user caches", e)
        }
    }

    /**
     * Load cached data specific to current user - INSTANT display
     */
    private fun loadUserSpecificCachedData() {
        try {
            val phoneNumber = currentPhoneNumber
            if (phoneNumber.isNullOrBlank()) {
                Log.w(TAG, "No phone number available for cache loading")
                initializeLoadingUI()
                return
            }

            val context = requireContext()
            val sharedPref = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

            // User-specific cache keys (clean phone number for key)
            val userPrefix = phoneNumber.replace("+", "").replace("-", "").replace(" ", "")
            val cacheTimestamp = sharedPref.getLong("${userPrefix}_timestamp", 0)
            val currentTime = System.currentTimeMillis()

            // Check cache validity (24 hours)
            val isCacheValid = (currentTime - cacheTimestamp) < CACHE_EXPIRY_MS

            if (isCacheValid) {
                val cachedRoomNumber = sharedPref.getString("${userPrefix}_room", null)
                val cachedElectric = sharedPref.getInt("${userPrefix}_electric", -1)
                val cachedWater = sharedPref.getInt("${userPrefix}_water", -1)
                val cachedElectricUsed = sharedPref.getInt("${userPrefix}_electric_used", -1)
                val cachedWaterUsed = sharedPref.getInt("${userPrefix}_water_used", -1)

                if (cachedRoomNumber != null && cachedElectric != -1) {
                    // Display cached data INSTANTLY
                    val cachedData = RoomData(
                        roomNumber = cachedRoomNumber,
                        latestElectric = cachedElectric,
                        latestWater = cachedWater,
                        electricUsed = cachedElectricUsed,
                        waterUsed = cachedWaterUsed
                    )

                    updateUIWithData(cachedData, fromCache = true)
                    currentRoomNumber = cachedRoomNumber

                    Log.d(TAG, "✅ Loaded user-specific cached data for ${phoneNumber.take(10)}... room: $cachedRoomNumber")
                    return
                }
            } else {
                Log.d(TAG, "User cache expired or invalid, age: ${(currentTime - cacheTimestamp) / 1000 / 60} minutes")
            }

            // No valid cache, show loading
            initializeLoadingUI()

        } catch (e: Exception) {
            Log.e(TAG, "Error loading user-specific cached data", e)
            initializeLoadingUI()
        }
    }

    /**
     * Cache data with user-specific keys
     */
    private fun cacheUserSpecificData(roomData: RoomData) {
        try {
            val phoneNumber = currentPhoneNumber
            if (phoneNumber.isNullOrBlank()) {
                Log.w(TAG, "No phone number available for caching")
                return
            }

            val context = requireContext()
            val sharedPref = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

            // User-specific cache keys (clean phone number)
            val userPrefix = phoneNumber.replace("+", "").replace("-", "").replace(" ", "")

            val success = sharedPref.edit().apply {
                putString("${userPrefix}_room", roomData.roomNumber)
                putInt("${userPrefix}_electric", roomData.latestElectric)
                putInt("${userPrefix}_water", roomData.latestWater)
                putInt("${userPrefix}_electric_used", roomData.electricUsed)
                putInt("${userPrefix}_water_used", roomData.waterUsed)
                putLong("${userPrefix}_timestamp", System.currentTimeMillis())
            }.commit() // Use commit() for immediate write

            if (success) {
                Log.d(TAG, "✅ User-specific data cached for ${phoneNumber.take(10)}... room: ${roomData.roomNumber}")
            } else {
                Log.e(TAG, "❌ Failed to cache user-specific data")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error caching user-specific data", e)
        }
    }

    /**
     * Loading state - only shown when no valid cache exists
     */
    private fun initializeLoadingUI() {
        try {
            binding.tvRoomNumber.text = "Đang tải..."
            binding.tvElectric.text = "-- kWh"
            binding.tvWater.text = "-- m³"
            binding.tvElectricUsed.text = "-- kWh"
            binding.tvWaterUsed.text = "-- m³"
            Log.d(TAG, "Loading UI initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing loading UI", e)
        }
    }

    private fun subscribeToAllResidents() {
        try {
            FCMHelper.subscribeToTopic("all_residents")
            Log.d(TAG, "✅ Subscribed to topic: all_residents")
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to all_residents topic", e)
        }
    }

    private fun setupDataLoading() {
        val phoneNumber = currentPhoneNumber

        if (phoneNumber.isNullOrBlank()) {
            Log.w(TAG, "User phone number is null or empty")
            showErrorState("Không thể xác định số điện thoại")
            return
        }

        Log.d(TAG, "Setting up Firebase listener for user: ${phoneNumber.take(10)}...")
        setupFirebaseListener(phoneNumber)
    }

    private fun setupFirebaseListener(phoneNumber: String) {
        try {
            // Remove existing listener
            removeFirebaseListener()

            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    handleDataChange(snapshot, phoneNumber)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error: ${error.message}", error.toException())
                    if (isFragmentActive && currentRoomNumber == null) {
                        showErrorState("Lỗi kết nối: ${error.message}")
                    }
                }
            }

            roomsRef?.addValueEventListener(valueEventListener!!)
            Log.d(TAG, "✅ Firebase listener attached for user: ${phoneNumber.take(10)}...")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Firebase listener", e)
            if (currentRoomNumber == null) {
                showErrorState("Lỗi thiết lập kết nối")
            }
        }
    }

    private fun handleDataChange(snapshot: DataSnapshot, phoneNumber: String) {
        // Rate limiting
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < MIN_UPDATE_INTERVAL) {
            Log.d(TAG, "Update skipped due to rate limiting")
            return
        }
        lastUpdateTime = currentTime

        if (!isFragmentActive) {
            Log.d(TAG, "Fragment not active, skipping data update")
            return
        }

        try {
            Log.d(TAG, "Processing Firebase data for user: ${phoneNumber.take(10)}...")

            val roomData = findUserRoomData(snapshot, phoneNumber)

            if (roomData.roomNumber != null) {
                currentRoomNumber = roomData.roomNumber

                // Cache user-specific data
                cacheUserSpecificData(roomData)

                // FCM setup
                if (!fcmTokenSent) {
                    sendFCMTokenToFirebase(roomData.roomNumber)
                    fcmTokenSent = true
                }

                // Update UI - smooth transition
                updateUIWithData(roomData, fromCache = false)

            } else {
                Log.w(TAG, "User room not found in Firebase for user: ${phoneNumber.take(10)}...")
                if (currentRoomNumber == null) {
                    showErrorState("Không tìm thấy phòng của bạn")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling data change", e)
            if (currentRoomNumber == null) {
                showErrorState("Lỗi xử lý dữ liệu")
            }
        }
    }

    private data class RoomData(
        val roomNumber: String?,
        val latestElectric: Int,
        val latestWater: Int,
        val electricUsed: Int,
        val waterUsed: Int
    )

    private fun findUserRoomData(snapshot: DataSnapshot, phoneNumber: String): RoomData {
        var latestElectric = -1
        var latestWater = -1
        var electricUsed = 0
        var waterUsed = 0
        var foundRoomNumber: String? = null

        try {
            val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

            for (roomSnapshot in snapshot.children) {
                val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)

                if (phoneInRoom == phoneNumber) {
                    foundRoomNumber = roomSnapshot.key
                    Log.d(TAG, "✅ Found user ${phoneNumber.take(10)}... in room: $foundRoomNumber")

                    // Get current readings from nodes
                    val nodesSnapshot = roomSnapshot.child("nodes")
                    for (nodeSnapshot in nodesSnapshot.children) {
                        val lastData = nodeSnapshot.child("lastData")
                        val waterValue = lastData.child("water").getValue(Long::class.java)?.toInt()
                        val electricValue = lastData.child("electric").getValue(Long::class.java)?.toInt()

                        if (waterValue != null && waterValue > latestWater) {
                            latestWater = waterValue
                        }
                        if (electricValue != null && electricValue > latestElectric) {
                            latestElectric = electricValue
                        }
                    }

                    // Calculate monthly usage from history
                    val monthlyUsage = calculateMonthlyUsage(roomSnapshot, currentMonth)
                    electricUsed = monthlyUsage.first
                    waterUsed = monthlyUsage.second

                    break
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error finding user room data", e)
        }

        return RoomData(
            roomNumber = foundRoomNumber,
            latestElectric = if (latestElectric == -1) 0 else latestElectric,
            latestWater = if (latestWater == -1) 0 else latestWater,
            electricUsed = electricUsed,
            waterUsed = waterUsed
        )
    }

    private fun calculateMonthlyUsage(roomSnapshot: DataSnapshot, currentMonth: String): Pair<Int, Int> {
        return try {
            val historySnapshot = roomSnapshot.child("history")
            val monthDates = historySnapshot.children
                .mapNotNull { it.key }
                .filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && it.startsWith(currentMonth) }
                .sorted()

            if (monthDates.size >= 2) {
                val firstDay = monthDates.first()
                val lastDay = monthDates.last()

                val firstSnapshot = historySnapshot.child(firstDay)
                val lastSnapshot = historySnapshot.child(lastDay)

                val firstElectric = firstSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                val lastElectric = lastSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                val firstWater = firstSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0
                val lastWater = lastSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0

                val electricUsed = maxOf(0, lastElectric - firstElectric)
                val waterUsed = maxOf(0, lastWater - firstWater)

                Pair(electricUsed, waterUsed)
            } else {
                Log.d(TAG, "Insufficient data for monthly calculation (${monthDates.size} entries)")
                Pair(0, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating monthly usage", e)
            Pair(0, 0)
        }
    }

    // [Keep all FCM methods the same as previous version...]
    private fun sendFCMTokenToFirebase(roomNumber: String) {
        try {
            val token = getFCMTokenFromPrefs()

            if (token != null) {
                Log.d(TAG, "Sending existing FCM token for room: $roomNumber")
                saveFCMTokenToFirebase(roomNumber, token)
            } else {
                Log.d(TAG, "No existing token, requesting new one for room: $roomNumber")
                requestAndSaveFCMToken(roomNumber)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending FCM token to Firebase", e)
        }
    }

    private fun getFCMTokenFromPrefs(): String? {
        return try {
            val context = requireContext()
            val sharedPref = context.getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE)
            sharedPref.getString(FCM_TOKEN_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token from preferences", e)
            null
        }
    }

    private fun saveFCMTokenToFirebase(roomNumber: String, token: String) {
        try {
            val fcmData = mapOf(
                "token" to token,
                "status" to "active",
                "updated_at" to System.currentTimeMillis()
            )

            database?.getReference("rooms")
                ?.child(roomNumber)
                ?.child("FCM")
                ?.setValue(fcmData)
                ?.addOnSuccessListener {
                    Log.d(TAG, "✅ FCM token saved successfully for room: $roomNumber")
                    subscribeToTopicsAndSendToFirebase(roomNumber)
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error saving FCM token for room: $roomNumber", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveFCMTokenToFirebase", e)
        }
    }

    private fun requestAndSaveFCMToken(roomNumber: String) {
        try {
            FCMHelper.getToken { token ->
                if (token != null && isFragmentActive) {
                    saveTokenToPrefs(token)
                    saveFCMTokenToFirebase(roomNumber, token)
                } else {
                    Log.e(TAG, "Failed to get new FCM token or fragment not active")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting new FCM token", e)
        }
    }

    private fun saveTokenToPrefs(token: String) {
        try {
            val context = requireContext()
            val sharedPref = context.getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE)
            val success = sharedPref.edit()
                .putString(FCM_TOKEN_KEY, token)
                .commit()

            if (success) {
                Log.d(TAG, "✅ FCM token saved to SharedPreferences")
            } else {
                Log.e(TAG, "❌ Failed to save FCM token to SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving token to preferences", e)
        }
    }

    private fun subscribeToTopicsAndSendToFirebase(roomNumber: String) {
        try {
            FCMHelper.subscribeToTopic("room_$roomNumber")

            val floor = roomNumber.take(1)
            val floorTopic = "floor_$floor"
            FCMHelper.subscribeToTopic(floorTopic)

            Log.d(TAG, "✅ Subscribed to topics: room_$roomNumber, $floorTopic")

            val topics = listOf(
                "all_residents",
                "room_$roomNumber",
                floorTopic
            )

            sendTopicsToFirebase(roomNumber, topics)

        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topics", e)
        }
    }

    private fun sendTopicsToFirebase(roomNumber: String, topics: List<String>) {
        try {
            database?.getReference("rooms")
                ?.child(roomNumber)
                ?.child("FCM")
                ?.child("topics")
                ?.setValue(topics)
                ?.addOnSuccessListener {
                    Log.d(TAG, "✅ Topics sent to Firebase successfully for room: $roomNumber")
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error sending topics to Firebase for room: $roomNumber", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendTopicsToFirebase", e)
        }
    }

    private fun updateUIWithData(roomData: RoomData, fromCache: Boolean = false) {
        try {
            if (!isFragmentActive || _binding == null) {
                Log.d(TAG, "Fragment not active or binding null, skipping UI update")
                return
            }

            binding.tvRoomNumber.text = roomData.roomNumber?.let { "Phòng $it" } ?: "Phòng N/A"
            binding.tvElectric.text = "${roomData.latestElectric} kWh"
            binding.tvWater.text = "${roomData.latestWater} m³"
            binding.tvElectricUsed.text = "${roomData.electricUsed} kWh"
            binding.tvWaterUsed.text = "${roomData.waterUsed} m³"

            val source = if (fromCache) "user cache" else "Firebase"
            Log.d(TAG, "✅ UI updated for room: ${roomData.roomNumber} (from $source)")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI", e)
        }
    }

    private fun showErrorState(message: String) {
        try {
            if (!isFragmentActive || _binding == null) return

            binding.tvRoomNumber.text = "Lỗi"
            binding.tvElectric.text = "-- kWh"
            binding.tvWater.text = "-- m³"
            binding.tvElectricUsed.text = "-- kWh"
            binding.tvWaterUsed.text = "-- m³"

            Log.e(TAG, "❌ Error state shown: $message")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing error state", e)
        }
    }

    private fun removeFirebaseListener() {
        try {
            valueEventListener?.let { listener ->
                roomsRef?.removeEventListener(listener)
                Log.d(TAG, "✅ Firebase listener removed")
            }
            valueEventListener = null
        } catch (e: Exception) {
            Log.e(TAG, "Error removing Firebase listener", e)
        }
    }

    fun refreshFCMToken() {
        try {
            Log.d(TAG, "Refreshing FCM token...")
            fcmTokenSent = false
            currentRoomNumber?.let { roomNumber ->
                sendFCMTokenToFirebase(roomNumber)
                fcmTokenSent = true
            } ?: run {
                Log.w(TAG, "No room number available for FCM token refresh")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing FCM token", e)
        }
    }

    override fun onResume() {
        super.onResume()
        isFragmentActive = true
        Log.d(TAG, "Fragment resumed")
    }

    override fun onPause() {
        super.onPause()
        isFragmentActive = false
        Log.d(TAG, "Fragment paused")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            isFragmentActive = false
            removeFirebaseListener()
            _binding = null
            Log.d(TAG, "HomeFragment view destroyed and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroyView cleanup", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            auth = null
            database = null
            roomsRef = null
            currentRoomNumber = null
            currentPhoneNumber = null
            Log.d(TAG, "HomeFragment destroyed and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup", e)
        }
    }
}