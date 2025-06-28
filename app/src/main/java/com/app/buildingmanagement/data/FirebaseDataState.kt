package com.app.buildingmanagement.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
    var electricPrice by mutableStateOf(3300) // Default fallback
    var waterPrice by mutableStateOf(15000) // Default fallback
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
        
        // Debug log
        Log.d(TAG, "Initialized, checking phone...")
        
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
                Log.d(TAG, "✅ Loaded from cache for $suffix: $roomNumber")
            }
        } else {
            Log.d(TAG, "🔄 Cache expired for $suffix, will load fresh data")
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
        Log.d(TAG, "💾 Saved to cache for $suffix: $roomNumber")
    }
    
    private fun startRealtimeDataLoading() {
        val auth = FirebaseAuth.getInstance()
        val rawPhone = auth.currentUser?.phoneNumber
        
        Log.d(TAG, "Raw user phone = $rawPhone")
        
        if (rawPhone == null) {
            Log.d(TAG, "No phone number, setting error state")
            setErrorState()
            return
        }
        
        // Chuyển đổi phone number từ format quốc tế (+84...) sang format local (0...)
        val phone = convertPhoneFormat(rawPhone)
        Log.d(TAG, "Converted phone = $phone")
        
        currentUserPhone = phone
        
        // DEBUG: Test database connection first
        testDatabaseConnection(phone)
        
        // Bước 1: Lấy thông tin phòng và building từ phone_to_room
        phoneEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Phone lookup data received: ${snapshot.exists()}")
                if (snapshot.exists()) {
                    Log.d(TAG, "Data content: ${snapshot.value}")
                    Log.d(TAG, "Data keys: ${snapshot.children.map { it.key }}")
                } else {
                    Log.e(TAG, "❌ Phone data NOT FOUND for: $phone")
                    Log.d(TAG, "🔍 Checking phone_to_room root...")
                    
                    // Check if phone_to_room exists at all
                    phoneToRoomRef?.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(rootSnapshot: DataSnapshot) {
                            Log.d(TAG, "phone_to_room root exists: ${rootSnapshot.exists()}")
                            if (rootSnapshot.exists()) {
                                Log.d(TAG, "Available phones: ${rootSnapshot.children.map { it.key }}")
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Failed to check phone_to_room root: ${error.message}")
                        }
                    })
                }
                processPhoneToRoomData(snapshot, phone)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Phone lookup cancelled: ${error.message}")
                setErrorState()
            }
        }
        
        Log.d(TAG, "Setting up listener for phone: $phone")
        phoneToRoomRef?.child(phone)?.addValueEventListener(phoneEventListener!!)
    }
    
    private fun testDatabaseConnection(phone: String) {
        database?.getReference("phone_to_room")?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "🔍 phone_to_room root - exists: ${snapshot.exists()}, children: ${snapshot.childrenCount}")
                if (snapshot.exists()) {
                    Log.d(TAG, "Available phone numbers:")
                    snapshot.children.forEach { child ->
                        Log.d(TAG, "  - ${child.key}")
                    }
                    
                    // Kiểm tra xem phone có tồn tại không
                    if (snapshot.hasChild(phone)) {
                        Log.d(TAG, "✅ Phone $phone exists in database")
                        val phoneData = snapshot.child(phone)
                        Log.d(TAG, "Phone data: buildingId=${phoneData.child("buildingId").value}, roomId=${phoneData.child("roomId").value}")
                    } else {
                        Log.e(TAG, "❌ Phone $phone NOT found in database")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database test failed: ${error.message}")
            }
        })
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
            Log.d(TAG, "Phone data not found in phone_to_room, trying fallback...")
            // FALLBACK: Thử tìm trong old structure rooms
            tryFallbackOldStructure(userPhone)
            return
        }
        
        val buildingIdValue = snapshot.child("buildingId").getValue(String::class.java)
        val roomId = snapshot.child("roomId").getValue(String::class.java)
        val name = snapshot.child("name").getValue(String::class.java)
        
        Log.d(TAG, "Found buildingId = $buildingIdValue, roomId = $roomId, name = $name")
        
        if (name != null) {
            userName = name
            isUserDataLoaded = true
        }
        
        if (buildingIdValue == null || roomId == null) {
            Log.d(TAG, "Missing buildingId or roomId, trying fallback...")
            tryFallbackOldStructure(userPhone)
            return
        }
        
        currentBuildingId = buildingIdValue
        buildingId = buildingIdValue // Cập nhật state
        currentRoomId = roomId
        
        // *** HIỂN thị room number NGAY LẬP TỨC ***
        roomNumber = "Phòng $roomId"
        isLoading = false  // Dừng loading ngay khi có room number
        Log.d(TAG, "Room number set immediately: Phòng $roomId")
        
        // Bước 2: Load building prices và setup room data listener
        loadBuildingPrices(buildingIdValue)
        setupRoomDataListener(buildingIdValue, roomId)
    }

    private fun tryFallbackOldStructure(userPhone: String) {
        Log.d(TAG, "🔄 Trying fallback to old structure...")
        
        // Thử tìm user trong rooms (old structure)
        val roomsRef = database?.getReference("rooms")
        
        roomsRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Old rooms structure - exists: ${snapshot.exists()}, children: ${snapshot.childrenCount}")
                
                var foundBuildingId: String? = null
                var foundRoom: String? = null
                var foundRoomSnapshot: DataSnapshot? = null
                var foundUserName: String? = null

                snapshot.children.forEach { buildingSnapshot ->
                    val roomsSnapshot = buildingSnapshot.child("rooms")
                    for (roomSnapshot in roomsSnapshot.children) {
                        val tenantsSnapshot = roomSnapshot.child("tenants")

                        for (tenantSnapshot in tenantsSnapshot.children) {
                            val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                            if (phoneInTenant == userPhone) {
                                foundBuildingId = buildingSnapshot.key
                                foundRoom = roomSnapshot.key
                                foundRoomSnapshot = roomSnapshot
                                foundUserName = tenantSnapshot.child("name").getValue(String::class.java)
                                break
                            }
                        }
                        if (foundRoom != null) break
                    }
                }

                if (foundBuildingId != null && foundRoom != null && foundUserName != null) {
                    Log.d(TAG, "Fallback found user: $foundUserName in room $foundRoom, building $foundBuildingId")
                    userName = foundUserName ?: "--"
                    isUserDataLoaded = true
                    roomNumber = "Phòng $foundRoom"
                    isDataLoaded = true
                    isLoading = false
                    currentBuildingId = foundBuildingId
                    buildingId = foundBuildingId
                    currentRoomId = foundRoom
                    setupRoomDataListener(foundBuildingId!!, foundRoom!!)
                    loadBuildingPrices(foundBuildingId!!)
                    saveToCache()
                } else {
                    Log.e(TAG, "User not found in fallback structure.")
                    setErrorState()
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Fallback search cancelled: ${error.message}")
                setErrorState()
            }
        })
    }

    private fun setupRoomDataListener(buildingId: String, roomId: String) {
        // Cleanup listener cũ nếu có
        roomEventListener?.let { listener ->
            roomDataRef?.removeEventListener(listener)
        }
        
        val roomPath = "buildings/$buildingId/rooms/$roomId"
        Log.d(TAG, "Setting up room listener for path: $roomPath")
        
        roomDataRef = database?.getReference("buildings")?.child(buildingId)?.child("rooms")?.child(roomId)
        
        roomEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Room data received: ${snapshot.exists()}")
                processRoomData(snapshot, roomId)
                
                // Kiểm tra payment status và suggest month cho PayFragment
                checkPaymentStatusAndSuggestMonth(buildingId, roomId)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Room data cancelled: ${error.message}")
                setErrorState()
            }
        }
        
        roomDataRef?.addValueEventListener(roomEventListener!!)
    }

    private fun processRoomData(roomSnapshot: DataSnapshot, roomId: String) {
        if (!roomSnapshot.exists()) {
            setErrorState()
            return
        }
        
        updateDataFromSnapshot(roomSnapshot, roomId)
    }

    private fun updateDataFromSnapshot(roomSnapshot: DataSnapshot, roomNum: String) {
        try {
            // Room number đã được set trong processPhoneToRoomData()
            Log.d(TAG, "Updating data for room: $roomNum")
            
            // Calculate usage data
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
            
            Log.d(TAG, "Nodes snapshot exists: ${nodesSnapshot.exists()}")
            Log.d(TAG, "Number of nodes: ${nodesSnapshot.childrenCount}")
            
            for (nodeSnapshot in nodesSnapshot.children) {
                val nodeId = nodeSnapshot.key
                Log.d(TAG, "Processing node: $nodeId")
                
                val lastData = nodeSnapshot.child("lastData")
                Log.d(TAG, "LastData exists: ${lastData.exists()}")
                
                // Thử đọc dưới dạng Double trước, rồi Long
                val waterValue = lastData.child("water").getValue(Double::class.java) 
                    ?: lastData.child("water").getValue(Long::class.java)?.toDouble()
                val electricValue = lastData.child("electric").getValue(Double::class.java)
                    ?: lastData.child("electric").getValue(Long::class.java)?.toDouble()

                Log.d(TAG, "Node $nodeId - Water: $waterValue, Electric: $electricValue")

                if (waterValue != null && waterValue > latestWater) latestWater = waterValue
                if (electricValue != null && electricValue > latestElectric) latestElectric = electricValue
            }
            
            Log.d(TAG, "Final readings - Electric: $latestElectric, Water: $latestWater")
            
            electricReading = if (latestElectric > -1) "${String.format("%.2f", latestElectric)} kWh" else "0 kWh"
            waterReading = if (latestWater > -1) "${String.format("%.2f", latestWater)} m³" else "0 m³"
            
            isDataLoaded = true
            isLoading = false
            
            // Save to cache cho lần tới
            saveToCache()
            
        } catch (e: Exception) {
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

    // Function để lấy history data cho charts
    fun getHistoryData(callback: (electricMap: Map<String, Float>, waterMap: Map<String, Float>) -> Unit) {
        // Thử new structure trước
        if (currentBuildingId != null && currentRoomId != null) {
            Log.d(TAG, "Loading history from new structure: buildings/$currentBuildingId/rooms/$currentRoomId")
            
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
                        Log.d(TAG, "No history in new structure, trying old structure...")
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
            Log.d(TAG, "Loading history from old structure: rooms/$currentRoomId")
            loadHistoryFromOldStructure(callback)
        } else {
            Log.e(TAG, "No room ID available for history data")
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
        Log.d(TAG, "History data loaded: Electric=${electricMap.size}, Water=${waterMap.size}")
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
        
        Log.d(TAG, "🔍 Checking payment status for month: $previousMonth")
        
        // Kiểm tra payment status của tháng trước
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
                // Nếu tháng trước đã thanh toán, suggest tháng hiện tại (tạm tính)
                isPaid -> {
                    Log.d(TAG, "✅ Tháng trước ($previousMonth) đã thanh toán → Suggest tháng hiện tại: $currentMonth")
                    currentMonth
                }
                // Nếu tháng trước chưa thanh toán, suggest tháng trước
                else -> {
                    Log.d(TAG, "⏰ Tháng trước ($previousMonth) chưa thanh toán → Suggest tháng trước: $previousMonth")
                    previousMonth
                }
            }
            
            isPaymentDataLoaded = true
            Log.d(TAG, "💡 Suggested payment month set to: $suggestedPaymentMonth")
        }?.addOnFailureListener { error ->
            Log.e(TAG, "❌ Failed to check payment status: ${error.message}")
            // Fallback: suggest previous month
            suggestedPaymentMonth = previousMonth
            isPaymentDataLoaded = true
        }
    }
    
    // Function để get current building and room IDs
    fun getCurrentBuildingId(): String? = currentBuildingId
    fun getCurrentRoomId(): String? = currentRoomId
    fun getCurrentMonth(): String = currentMonth
    fun getPreviousMonth(): String = previousMonth
    
    // Function để refresh payment status (gọi sau khi thanh toán)
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
                    Log.d(TAG, "Prices loaded: E=$electricPrice, W=$waterPrice")
                } else {
                    Log.w(TAG, "Prices node does not exist for building $buildingId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load prices: ${error.message}")
            }
        })
    }
    
    // Function to get building prices (for PayFragment)
    fun getBuildingPrices(): Pair<Int, Int> = Pair(electricPrice, waterPrice)
    
    // Function để tính consumption cho tháng cụ thể (for PayFragment)
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