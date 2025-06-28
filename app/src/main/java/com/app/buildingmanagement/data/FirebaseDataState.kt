package com.app.buildingmanagement.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object FirebaseDataState {
    private const val TAG = "FirebaseDataState"
    private const val CACHE_PREFS = "firebase_data_cache"
    
    // Cache keys
    private const val KEY_ROOM_NUMBER = "room_number"
    private const val KEY_ELECTRIC_USED = "electric_used"
    private const val KEY_WATER_USED = "water_used"
    private const val KEY_ELECTRIC_READING = "electric_reading"
    private const val KEY_WATER_READING = "water_reading"
    private const val KEY_ELECTRIC_PRICE = "electric_price"
    private const val KEY_WATER_PRICE = "water_price"
    private const val KEY_LAST_CACHE_TIME = "last_cache_time"
    private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 phút
    
    // Compose states cho usage data
    var electricUsed by mutableStateOf("-- kWh")
    var waterUsed by mutableStateOf("-- m³")
    var roomNumber by mutableStateOf("--")
    var electricReading by mutableStateOf("-- kWh")
    var waterReading by mutableStateOf("-- m³")
    var electricPrice by mutableIntStateOf(3300) // Default fallback
    var waterPrice by mutableIntStateOf(15000) // Default fallback
    var isPricesLoaded by mutableStateOf(false)
    var isDataLoaded by mutableStateOf(false)
    var isLoading by mutableStateOf(true)
    var buildingId by mutableStateOf<String?>(null)
    
    // User info states
    var userName by mutableStateOf("--")
    var isUserDataLoaded by mutableStateOf(false)
    
    // Payment status cho PayFragment
    var suggestedPaymentMonth by mutableStateOf("")
    var isPaymentDataLoaded by mutableStateOf(false)
    private var currentMonth = ""
    private var previousMonth = ""
    
    private var cachePrefs: SharedPreferences? = null
    
    private var database: FirebaseDatabase? = null
    private var phoneToRoomRef: DatabaseReference? = null
    private var roomDataRef: DatabaseReference? = null
    private var phoneEventListener: ValueEventListener? = null
    private var roomEventListener: ValueEventListener? = null
    private var currentUserPhone: String? = null
    private var currentBuildingId: String? = null
    private var currentRoomId: String? = null

    fun initialize(context: Context) {
        database = FirebaseDatabase.getInstance()
        phoneToRoomRef = database?.getReference("phone_to_room")
        cachePrefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        
        // Load cache first để hiển thị instant
        loadFromCache()
        
        // Start realtime data loading
        startRealtimeDataLoading()
    }
    
    private fun getUserCacheSuffix(): String {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        // Ưu tiên dùng UID, nếu không có thì dùng phone, nếu không có thì "unknown"
        return user?.uid ?: user?.phoneNumber ?: "unknown"
    }
    
    private fun loadFromCache() {
        val suffix = getUserCacheSuffix()
        val lastCacheTime = cachePrefs?.getLong(KEY_LAST_CACHE_TIME + "_" + suffix, 0) ?: 0
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastCacheTime < CACHE_DURATION_MS) {
            // Cache còn fresh, load ngay
            roomNumber = cachePrefs?.getString(KEY_ROOM_NUMBER + "_" + suffix, "--") ?: "--"
            electricUsed = cachePrefs?.getString(KEY_ELECTRIC_USED + "_" + suffix, "-- kWh") ?: "-- kWh"
            waterUsed = cachePrefs?.getString(KEY_WATER_USED + "_" + suffix, "-- m³") ?: "-- m³"
            electricReading = cachePrefs?.getString(KEY_ELECTRIC_READING + "_" + suffix, "-- kWh") ?: "-- kWh"
            waterReading = cachePrefs?.getString(KEY_WATER_READING + "_" + suffix, "-- m³") ?: "-- m³"
            electricPrice = cachePrefs?.getInt(KEY_ELECTRIC_PRICE + "_" + suffix, 3300) ?: 3300
            waterPrice = cachePrefs?.getInt(KEY_WATER_PRICE + "_" + suffix, 15000) ?: 15000
            userName = cachePrefs?.getString("user_name_$suffix", "--") ?: "--"
            buildingId = cachePrefs?.getString("building_id_$suffix", null)
            
            if (roomNumber != "--") {
                isLoading = false
                isDataLoaded = true
                if (userName != "--") {
                    isUserDataLoaded = true
                }
            }
        }
    }
    
    private fun saveToCache() {
        val suffix = getUserCacheSuffix()
        cachePrefs?.edit()?.apply {
            putString(KEY_ROOM_NUMBER + "_" + suffix, roomNumber)
            putString(KEY_ELECTRIC_USED + "_" + suffix, electricUsed)
            putString(KEY_WATER_USED + "_" + suffix, waterUsed)
            putString(KEY_ELECTRIC_READING + "_" + suffix, electricReading)
            putString(KEY_WATER_READING + "_" + suffix, waterReading)
            putInt(KEY_ELECTRIC_PRICE + "_" + suffix, electricPrice)
            putInt(KEY_WATER_PRICE + "_" + suffix, waterPrice)
            putString("user_name_$suffix", userName)
            putString("building_id_$suffix", buildingId)
            putLong(KEY_LAST_CACHE_TIME + "_" + suffix, System.currentTimeMillis())
            apply()
        }
    }
    
    private fun startRealtimeDataLoading() {
        val auth = FirebaseAuth.getInstance()
        val rawPhone = auth.currentUser?.phoneNumber
        
        if (rawPhone == null) {
            setErrorState()
            return
        }
        
        // Chuyển đổi phone number từ format quốc tế (+84...) sang format local (0...)
        val phone = convertPhoneFormat(rawPhone)
        currentUserPhone = phone
        
        // Bước 1: Lấy thông tin phòng và building từ phone_to_room
        phoneEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "❌ Phone data NOT FOUND for: $phone")
                }
                processPhoneToRoomData(snapshot, phone)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Phone lookup cancelled: ${error.message}")
                setErrorState()
            }
        }
        
        phoneToRoomRef?.child(phone)?.addValueEventListener(phoneEventListener!!)
    }

    private fun convertPhoneFormat(internationalPhone: String): String {
        // Chuyển đổi từ +84123456789 sang 0123456789
        return if (internationalPhone.startsWith("+84")) {
            "0" + internationalPhone.substring(3)
        } else if (internationalPhone.startsWith("84")) {
            "0" + internationalPhone.substring(2)
        } else {
            internationalPhone // Giữ nguyên nếu đã đúng format
        }
    }

    private fun processPhoneToRoomData(snapshot: DataSnapshot, userPhone: String) {
        if (!snapshot.exists()) {
            tryFallbackOldStructure(userPhone)
            return
        }
        
        val buildingIdValue = snapshot.child("buildingId").getValue(String::class.java)
        val roomId = snapshot.child("roomId").getValue(String::class.java)
        val name = snapshot.child("name").getValue(String::class.java)
        

        if (name != null) {
            userName = name
            isUserDataLoaded = true
        }
        
        if (buildingIdValue == null || roomId == null) {
            tryFallbackOldStructure(userPhone)
            return
        }
        
        currentBuildingId = buildingIdValue
        buildingId = buildingIdValue
        currentRoomId = roomId
        
        roomNumber = "Phòng $roomId"
        isLoading = false

        loadBuildingPrices(buildingIdValue)
        setupRoomDataListener(buildingIdValue, roomId)
    }

    private fun tryFallbackOldStructure(userPhone: String) {
        val roomsRef = database?.getReference("rooms")
        
        roomsRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundUserName: String? = null
                var foundBuildingId: String? = null
                var foundRoom: String? = null

                snapshot.children.forEach { buildingSnapshot ->
                    val roomsSnapshot = buildingSnapshot.child("rooms")
                    for (roomSnapshot in roomsSnapshot.children) {
                        val tenantsSnapshot = roomSnapshot.child("tenants")

                        for (tenantSnapshot in tenantsSnapshot.children) {
                            val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                            if (phoneInTenant == userPhone) {
                                foundBuildingId = buildingSnapshot.key
                                foundRoom = roomSnapshot.key
                                foundUserName = tenantSnapshot.child("name").getValue(String::class.java)
                                break
                            }
                        }
                        if (foundRoom != null) break
                    }
                }

                if (foundBuildingId != null && foundRoom != null && foundUserName != null) {
                    userName = foundUserName
                    isUserDataLoaded = true
                    roomNumber = "Phòng $foundRoom"
                    isDataLoaded = true
                    isLoading = false
                    currentBuildingId = foundBuildingId
                    buildingId = foundBuildingId
                    currentRoomId = foundRoom
                    setupRoomDataListener(foundBuildingId, foundRoom)
                    loadBuildingPrices(foundBuildingId)
                    saveToCache()
                } else {
                    setErrorState()
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                setErrorState()
            }
        })
    }

    private fun setupRoomDataListener(buildingId: String, roomId: String) {
        roomEventListener?.let { listener ->
            roomDataRef?.removeEventListener(listener)
        }
        

        roomDataRef = database?.getReference("buildings")?.child(buildingId)?.child("rooms")?.child(roomId)
        
        roomEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                processRoomData(snapshot)
                
                checkPaymentStatusAndSuggestMonth(buildingId, roomId)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Room data cancelled: ${error.message}")
                setErrorState()
            }
        }
        
        roomDataRef?.addValueEventListener(roomEventListener!!)
    }

    private fun processRoomData(roomSnapshot: DataSnapshot) {
        if (!roomSnapshot.exists()) {
            setErrorState()
            return
        }
        
        updateDataFromSnapshot(roomSnapshot)
    }

    private fun updateDataFromSnapshot(roomSnapshot: DataSnapshot) {
        try {
            val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val prevMonth = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
            }
            val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevMonth.time)
            
            val historySnapshot = roomSnapshot.child("history")
            val allHistoryData = mutableMapOf<String, Pair<Float?, Float?>>()
            for (dateSnapshot in historySnapshot.children) {
                val dateKey = dateSnapshot.key ?: continue
                val electric = dateSnapshot.child("electric").getValue(Double::class.java)?.toFloat()
                    ?: dateSnapshot.child("electric").getValue(Long::class.java)?.toFloat()
                val water = dateSnapshot.child("water").getValue(Double::class.java)?.toFloat()
                    ?: dateSnapshot.child("water").getValue(Long::class.java)?.toFloat()
                allHistoryData[dateKey] = Pair(electric, water)
            }
            
            val electricUsedValue = calculateMonthlyConsumptionFloat(allHistoryData, currentMonth, prevMonthKey, true)
            val waterUsedValue = calculateMonthlyConsumptionFloat(allHistoryData, currentMonth, prevMonthKey, false)
            
            electricUsed = formatUsageValue(electricUsedValue, "kWh")
            waterUsed = formatUsageValue(waterUsedValue, "m³")
            
            // Get latest meter readings
            var latestElectric: Double = -1.0
            var latestWater: Double = -1.0
            val nodesSnapshot = roomSnapshot.child("nodes")
            
            for (nodeSnapshot in nodesSnapshot.children) {
                val lastData = nodeSnapshot.child("lastData")

                val waterValue = lastData.child("water").getValue(Double::class.java)
                    ?: lastData.child("water").getValue(Long::class.java)?.toDouble()
                val electricValue = lastData.child("electric").getValue(Double::class.java)
                    ?: lastData.child("electric").getValue(Long::class.java)?.toDouble()


                if (waterValue != null && waterValue > latestWater) latestWater = waterValue
                if (electricValue != null && electricValue > latestElectric) latestElectric = electricValue
            }

            electricReading = if (latestElectric > -1) "${String.format(Locale.getDefault(), "%.2f", latestElectric)} kWh" else "0 kWh"
            waterReading = if (latestWater > -1) "${String.format(Locale.getDefault(), "%.2f", latestWater)} m³" else "0 m³"

            isDataLoaded = true
            isLoading = false
            
            // Save to cache cho lần tới
            saveToCache()
            
        } catch (_: Exception) {
            setErrorState()
        }
    }

    private fun calculateMonthlyConsumptionFloat(
        allHistoryData: Map<String, Pair<Float?, Float?>>, 
        currentMonth: String, 
        prevMonthKey: String, 
        isElectric: Boolean
    ): Float {
        val currentMonthData = allHistoryData
            .filterKeys { it.startsWith(currentMonth) }
            .toSortedMap()
        
        val prevMonthData = allHistoryData
            .filterKeys { it.startsWith(prevMonthKey) }
            .toSortedMap()
        
        val currentValues = currentMonthData.values.mapNotNull { 
            if (isElectric) it.first else it.second 
        }
        val prevValues = prevMonthData.values.mapNotNull { 
            if (isElectric) it.first else it.second 
        }
        
        if (currentValues.isEmpty()) {
            return 0f
        }
        
        val currentMaxValue = currentValues.maxOrNull() ?: 0f
        val prevMonthLastValue = prevValues.lastOrNull()
        
        return if (prevMonthLastValue != null) {
            (currentMaxValue - prevMonthLastValue).coerceAtLeast(0f)
        } else {
            val currentMinValue = currentValues.minOrNull() ?: 0f
            (currentMaxValue - currentMinValue).coerceAtLeast(0f)
        }
    }

    private fun formatUsageValue(value: Float, unit: String): String {
        return when {
            value < 10f -> String.format(Locale.getDefault(), "%.2f %s", value, unit)
            value < 100f -> String.format(Locale.getDefault(), "%.1f %s", value, unit)
            else -> String.format(Locale.getDefault(), "%.0f %s", value, unit)
        }
    }

    private fun setErrorState() {
        electricUsed = "0 kWh"
        waterUsed = "0 m³"
        roomNumber = "Lỗi kết nối"
        electricReading = "0 kWh"
        waterReading = "0 m³"
        isDataLoaded = false
        isLoading = false
    }

    fun cleanup() {
        phoneEventListener?.let { listener ->
            phoneToRoomRef?.child(currentUserPhone ?: "")?.removeEventListener(listener)
        }
        roomEventListener?.let { listener ->
            roomDataRef?.removeEventListener(listener)
        }
        
        phoneEventListener = null
        roomEventListener = null
        phoneToRoomRef = null
        roomDataRef = null
        database = null
        currentUserPhone = null
        currentBuildingId = null
        currentRoomId = null
    }

    fun getHistoryData(callback: (electricMap: Map<String, Float>, waterMap: Map<String, Float>) -> Unit) {
        if (currentBuildingId != null && currentRoomId != null) {
            val historyRef = database?.getReference("buildings")
                ?.child(currentBuildingId!!)
                ?.child("rooms")
                ?.child(currentRoomId!!)
                ?.child("history")
            
            historyRef?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        processHistoryData(snapshot, callback)
                    } else {
                        loadHistoryFromOldStructure(callback)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load history from new structure: ${error.message}")
                    loadHistoryFromOldStructure(callback)
                }
            })
        } else if (currentRoomId != null) {
            // Fallback to old structure
            loadHistoryFromOldStructure(callback)
        } else {
            callback(emptyMap(), emptyMap())
        }
    }
    
    private fun loadHistoryFromOldStructure(callback: (electricMap: Map<String, Float>, waterMap: Map<String, Float>) -> Unit) {
        if (currentRoomId == null) {
            callback(emptyMap(), emptyMap())
            return
        }
        
        val historyRef = database?.getReference("rooms")
            ?.child(currentRoomId!!)
            ?.child("history")
        
        historyRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                processHistoryData(snapshot, callback)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load history from old structure: ${error.message}")
                callback(emptyMap(), emptyMap())
            }
        })
    }
    
    private fun processHistoryData(snapshot: DataSnapshot, callback: (electricMap: Map<String, Float>, waterMap: Map<String, Float>) -> Unit) {
        val electricMap = mutableMapOf<String, Float>()
        val waterMap = mutableMapOf<String, Float>()
        
        for (dateSnapshot in snapshot.children) {
            val dateKey = dateSnapshot.key ?: continue
            val waterValue = dateSnapshot.child("water").getValue(Double::class.java)
                ?: dateSnapshot.child("water").getValue(Long::class.java)?.toDouble()
            val electricValue = dateSnapshot.child("electric").getValue(Double::class.java)
                ?: dateSnapshot.child("electric").getValue(Long::class.java)?.toDouble()
            if (waterValue != null) {
                waterMap[dateKey] = waterValue.toFloat()
            }
            if (electricValue != null) {
                electricMap[dateKey] = electricValue.toFloat()
            }
        }
        callback(electricMap, waterMap)
    }
    
    private fun checkPaymentStatusAndSuggestMonth(buildingId: String, roomId: String) {
        // Tính toán current month và previous month
        val calendar = Calendar.getInstance()
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        currentMonth = monthKeyFormat.format(calendar.time)
        
        val prevCalendar = Calendar.getInstance()
        prevCalendar.add(Calendar.MONTH, -1)
        previousMonth = monthKeyFormat.format(prevCalendar.time)

        val paymentRef = database?.getReference("buildings")
            ?.child(buildingId)
            ?.child("rooms")
            ?.child(roomId)
            ?.child("payments")
            ?.child(previousMonth)
        
        paymentRef?.get()?.addOnSuccessListener { paymentSnapshot ->
            val isPaid = paymentSnapshot.exists() && 
                        paymentSnapshot.child("status").getValue(String::class.java) == "PAID"
            
            suggestedPaymentMonth = when {
                isPaid -> {
                    currentMonth
                }
                else -> {
                    previousMonth
                }
            }
            
            isPaymentDataLoaded = true
        }?.addOnFailureListener { error ->
            suggestedPaymentMonth = previousMonth
            isPaymentDataLoaded = true
        }
    }
    
    fun getCurrentBuildingId(): String? = currentBuildingId
    fun getCurrentRoomId(): String? = currentRoomId
    fun getCurrentMonth(): String = currentMonth

    fun refreshPaymentStatus() {
        if (currentBuildingId != null && currentRoomId != null) {
            checkPaymentStatusAndSuggestMonth(currentBuildingId!!, currentRoomId!!)
        }
    }
    
    private fun loadBuildingPrices(buildingId: String) {
        if (buildingId.isEmpty()) {
            Log.e(TAG, "Building ID is empty, cannot load prices.")
            return
        }
        val pricesRef = database?.getReference("buildings")?.child(buildingId)
        pricesRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val elecPrice = snapshot.child("price_electric").getValue(Int::class.java)
                    val watPrice = snapshot.child("price_water").getValue(Int::class.java)

                    if (elecPrice != null) {
                        electricPrice = elecPrice
                    }
                    if (watPrice != null) {
                        waterPrice = watPrice
                    }
                    isPricesLoaded = true
                    saveToCache() // Cache prices
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load prices: ${error.message}")
            }
        })
    }
    
    fun getBuildingPrices(): Pair<Int, Int> = Pair(electricPrice, waterPrice)
    fun getMonthlyConsumption(targetMonth: String, callback: (electricUsage: Double, waterUsage: Double) -> Unit) {
        getHistoryData { electricMap, waterMap ->
            val prevCalendar = Calendar.getInstance().apply {
                val parts = targetMonth.split("-")
                if (parts.size >= 2) {
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                    add(Calendar.MONTH, -1)
                }
            }
            val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevCalendar.time)
            
            val electricUsage = calculateMonthlyConsumptionFromMaps(electricMap, targetMonth, prevMonthKey)
            val waterUsage = calculateMonthlyConsumptionFromMaps(waterMap, targetMonth, prevMonthKey)
            
            callback(electricUsage, waterUsage)
        }
    }
    
    private fun calculateMonthlyConsumptionFromMaps(
        dataMap: Map<String, Float>,
        currentMonth: String,
        prevMonth: String
    ): Double {
        val currentMonthData = dataMap.filterKeys { it.startsWith(currentMonth) }.toSortedMap()
        val prevMonthData = dataMap.filterKeys { it.startsWith(prevMonth) }.toSortedMap()
        
        if (currentMonthData.isEmpty()) return 0.0
        
        val currentMaxValue = currentMonthData.values.maxOrNull() ?: 0f
        val prevMonthLastValue = prevMonthData.values.lastOrNull()
        
        val result = if (prevMonthLastValue != null) {
            currentMaxValue - prevMonthLastValue
        } else {
            val currentMinValue = currentMonthData.values.minOrNull() ?: 0f
            currentMaxValue - currentMinValue
        }
        
        return maxOf(0.0, result.toDouble())
    }
}
