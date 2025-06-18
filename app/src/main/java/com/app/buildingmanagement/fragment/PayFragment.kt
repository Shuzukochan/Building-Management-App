package com.app.buildingmanagement.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.buildingmanagement.R
import com.app.buildingmanagement.WebPayActivity
import com.app.buildingmanagement.databinding.FragmentPayBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PayFragment : Fragment() {

    private var _binding: FragmentPayBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var roomsRef: DatabaseReference

    private var totalCost: Int = 0
    private var selectedMonth: String = ""
    private var currentMonth: String = ""
    private var previousMonth: String = ""
    private var userRoomNumber: String? = null

    private var monthKeys: List<String> = emptyList()

    // Fragment state management
    private var isFragmentActive = false

    // Data cache for instant switching
    private data class CachedData(
        val usageData: UsageData,
        val paymentStatus: Boolean,
        val timestamp: Long
    )

    private var cachedMonthData = mutableMapOf<String, CachedData>()
    private val CACHE_EXPIRY_MS = 30_000L // 30 seconds cache

    private data class UsageData(
        val usedElectric: Int,
        val usedWater: Int,
        val electricCost: Int,
        val waterCost: Int,
        val totalCost: Int
    )

    companion object {
        private const val TAG = "PayFragment"
        private const val PAYMENT_REQUEST_CODE = 1001

        // Global cache across all fragments
        private var globalUserRoom: String? = null
        private var globalPhoneNumber: String? = null
        private var globalDataCache = mutableMapOf<String, Any>()
        private var lastCacheTime = 0L
        private val GLOBAL_CACHE_EXPIRY = 60_000L // 1 minute

        /**
         * Clear global cache when user logs out or data becomes invalid
         */
        fun clearGlobalCache() {
            try {
                globalUserRoom = null
                globalPhoneNumber = null
                globalDataCache.clear()
                lastCacheTime = 0L
                Log.d(TAG, "✅ Global cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing global cache", e)
            }
        }

        /**
         * Update global cache from external fragments
         */
        fun updateGlobalCacheFromExternal(roomNumber: String?, phoneNumber: String?) {
            try {
                if (roomNumber != null && phoneNumber != null) {
                    globalUserRoom = roomNumber
                    globalPhoneNumber = phoneNumber
                    lastCacheTime = System.currentTimeMillis()
                    Log.d(TAG, "✅ Global cache updated from external: room=$roomNumber")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating global cache from external", e)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isFragmentActive = true

        try {
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance()
            roomsRef = database.getReference("rooms")

            // Check if we can use global cache for instant load
            val canUseGlobalCache = checkGlobalCacheValidity()

            if (canUseGlobalCache) {
                Log.d(TAG, "Using global cache for instant load")
                initializeWithGlobalCache()
            } else {
                Log.d(TAG, "Global cache not available, initializing normally")
                initializeOptimisticUI()
            }

            // Always continue with data loading for accuracy
            setupPaymentButton()
            findUserRoom()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            showErrorState("Lỗi khởi tạo ứng dụng")
        }
    }

    override fun onResume() {
        super.onResume()
        isFragmentActive = true
        refreshPaymentStatus()
    }

    override fun onPause() {
        super.onPause()
        isFragmentActive = false
    }

    /**
     * Check if global cache is valid and can be used
     */
    private fun checkGlobalCacheValidity(): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val currentPhone = auth.currentUser?.phoneNumber

            val isValid = globalUserRoom != null &&
                    globalPhoneNumber == currentPhone &&
                    (now - lastCacheTime) < GLOBAL_CACHE_EXPIRY

            Log.d(TAG, "Global cache validity: $isValid (room: $globalUserRoom, phone match: ${globalPhoneNumber == currentPhone}, cache age: ${now - lastCacheTime}ms)")

            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error checking global cache validity", e)
            false
        }
    }

    /**
     * Initialize with global cache for instant experience
     */
    private fun initializeWithGlobalCache() {
        try {
            // Use cached data immediately
            userRoomNumber = globalUserRoom

            // Show optimistic UI with cached context
            val cachedMonths = globalDataCache["months"] as? List<String>
            val cachedSelectedMonth = globalDataCache["selectedMonth"] as? String

            if (cachedMonths != null && cachedSelectedMonth != null) {
                Log.d(TAG, "Setting up instant UI with cached data")

                // Set basic data
                monthKeys = cachedMonths
                selectedMonth = cachedSelectedMonth

                // Setup instant UI
                setupInstantUIFromCache(cachedMonths, cachedSelectedMonth)
            } else {
                // Setup basic optimistic UI
                setupBasicOptimisticUI()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing with global cache", e)
            initializeOptimisticUI()
        }
    }

    /**
     * Initialize optimistic UI when no cache available
     */
    private fun initializeOptimisticUI() {
        try {
            setupBasicOptimisticUI()
            Log.d(TAG, "✅ Optimistic UI initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing optimistic UI", e)
        }
    }

    /**
     * Setup basic optimistic UI
     */
    private fun setupBasicOptimisticUI() {
        binding.apply {
            // Show default data instead of loading
            tvElectricDetail.text = "Tiêu thụ điện: 0 × 3.300đ"
            tvElectricAmount.text = "0đ"
            tvWaterDetail.text = "Tiêu thụ nước: 0 × 15.000đ"
            tvWaterAmount.text = "0đ"
            tvTotalAmount.text = "0đ"

            // Default calculation title
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            tvCalculationTitle.text = "Tạm tính đến ngày: $today"

            // Default payment status - optimistic
            val linearLayout = cardPaymentStatus.getChildAt(0) as LinearLayout
            linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_orange)
            ivPaymentStatusIcon.setImageResource(R.drawable.ic_pending)
            tvPaymentStatus.text = "Đang cập nhật thông tin..."
            tvNote.text = "Hệ thống đang tải dữ liệu."

            // Default button state
            btnPayNow.isEnabled = false
            btnPayNow.text = "Vui lòng đợi..."
            btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))

            // Hide notice initially
            cardPaymentNotice.visibility = View.GONE

            // Keep spinner disabled until ready
            spnMonthPicker.isEnabled = false
        }
    }

    /**
     * Setup instant UI from cached data
     */
    private fun setupInstantUIFromCache(months: List<String>, selectedMonth: String) {
        try {
            val displayMonths = months.map { formatMonthForDisplay(it) }

            // Setup spinner immediately
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                displayMonths
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            binding.spnMonthPicker.adapter = adapter
            binding.spnMonthPicker.isEnabled = true

            // Set selection
            val selectedIndex = months.indexOf(selectedMonth)
            if (selectedIndex >= 0) {
                binding.spnMonthPicker.setSelection(selectedIndex)
            }

            // Set listener
            binding.spnMonthPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    handleMonthSelectionChange(position)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Show reasonable default data for selected month
            setupBasicOptimisticUI()
            binding.tvCalculationTitle.text = "Chi tiết tháng ${formatMonthForDisplay(selectedMonth)}"

            Log.d(TAG, "✅ Instant UI setup from cache completed")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up instant UI from cache", e)
            setupBasicOptimisticUI()
        }
    }

    private fun setupPaymentButton() {
        binding.btnPayNow.setOnClickListener {
            if (binding.btnPayNow.isEnabled) {
                openPaymentLink()
            }
        }
    }

    private fun findUserRoom() {
        val phone = auth.currentUser?.phoneNumber ?: return

        Log.d(TAG, "Finding room for phone: ${phone.take(10)}...")

        roomsRef.orderByChild("phone").equalTo(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (!isFragmentActive) return

                        if (snapshot.exists()) {
                            val roomSnapshot = snapshot.children.first()
                            userRoomNumber = roomSnapshot.key

                            Log.d(TAG, "✅ Found user in room: $userRoomNumber")

                            // Update global cache
                            updateGlobalCache()

                            // Load data and setup UI
                            lifecycleScope.launch {
                                loadDataAndSetupUI()
                            }

                        } else {
                            showErrorState("Không tìm thấy phòng của bạn")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing room data", e)
                        showErrorState("Lỗi xử lý dữ liệu phòng")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error finding user room: ${error.message}", error.toException())
                    if (isFragmentActive) {
                        showErrorState("Lỗi tải dữ liệu: ${error.message}")
                    }
                }
            })
    }

    /**
     * Update global cache for other fragments to use
     */
    private fun updateGlobalCache() {
        try {
            globalUserRoom = userRoomNumber
            globalPhoneNumber = auth.currentUser?.phoneNumber
            lastCacheTime = System.currentTimeMillis()

            Log.d(TAG, "✅ Global cache updated")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating global cache", e)
        }
    }

    /**
     * Load data and setup complete UI
     */
    private suspend fun loadDataAndSetupUI() {
        try {
            val roomNumber = userRoomNumber ?: return

            // Load months and history data
            val monthsResult = withContext(Dispatchers.IO) {
                loadMonthsDataSync()
            }

            if (!isFragmentActive || monthsResult == null) return

            val historySnapshot = withContext(Dispatchers.IO) {
                loadHistoryDataSync()
            }

            if (!isFragmentActive || historySnapshot == null) return

            // Determine smart default month
            val defaultMonth = withContext(Dispatchers.IO) {
                determineSmartDefaultMonth(historySnapshot, monthsResult)
            }

            // Update global cache with fresh data
            withContext(Dispatchers.Main) {
                updateGlobalCacheWithData(monthsResult, defaultMonth)
            }

            // Setup complete UI
            withContext(Dispatchers.Main) {
                setupCompleteUI(monthsResult, defaultMonth, historySnapshot)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in loadDataAndSetupUI", e)
            withContext(Dispatchers.Main) {
                showErrorState("Lỗi tải dữ liệu: ${e.message}")
            }
        }
    }

    /**
     * Load months data synchronously
     */
    private fun loadMonthsDataSync(): List<String>? {
        return try {
            val roomNumber = userRoomNumber ?: return null
            val latch = CountDownLatch(1)
            var result: List<String>? = null

            roomsRef.child(roomNumber).child("history")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val rawMonths = mutableSetOf<String>()
                            for (dateSnapshot in snapshot.children) {
                                val dateKey = dateSnapshot.key ?: continue
                                if (dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                    val monthKey = dateKey.substring(0, 7)
                                    rawMonths.add(monthKey)
                                }
                            }
                            result = rawMonths.sorted()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing months", e)
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error loading months: ${error.message}")
                        latch.countDown()
                    }
                })

            latch.await(10, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadMonthsDataSync", e)
            null
        }
    }

    /**
     * Load history data synchronously
     */
    private fun loadHistoryDataSync(): DataSnapshot? {
        return try {
            val roomNumber = userRoomNumber ?: return null
            val latch = CountDownLatch(1)
            var result: DataSnapshot? = null

            roomsRef.child(roomNumber).child("history")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        result = snapshot
                        latch.countDown()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error loading history: ${error.message}")
                        latch.countDown()
                    }
                })

            latch.await(10, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadHistoryDataSync", e)
            null
        }
    }

    /**
     * Determine smart default month based on payment status
     */
    private fun determineSmartDefaultMonth(historySnapshot: DataSnapshot, months: List<String>): String? {
        return try {
            monthKeys = months

            val calendar = Calendar.getInstance()
            val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            currentMonth = monthKeyFormat.format(calendar.time)

            calendar.add(Calendar.MONTH, -1)
            previousMonth = monthKeyFormat.format(calendar.time)

            Log.d(TAG, "=== SMART MONTH SELECTION ===")
            Log.d(TAG, "Current Month: $currentMonth")
            Log.d(TAG, "Previous Month: $previousMonth")
            Log.d(TAG, "Available Months: $monthKeys")

            // Check if previous month exists and has data
            if (!monthKeys.contains(previousMonth)) {
                val selected = if (monthKeys.contains(currentMonth)) currentMonth else monthKeys.lastOrNull()
                Log.d(TAG, "✅ Previous month not available, selected: $selected")
                return selected
            }

            // Calculate previous month usage
            val prevUsageData = processUsageDataFromSnapshot(historySnapshot, previousMonth)
            Log.d(TAG, "Previous month usage: Electric=${prevUsageData.usedElectric}, Water=${prevUsageData.usedWater}, Total=${prevUsageData.totalCost}")

            // If zero cost, use current month
            if (prevUsageData.totalCost <= 0) {
                val selected = if (monthKeys.contains(currentMonth)) currentMonth else monthKeys.lastOrNull()
                Log.d(TAG, "✅ Previous month zero cost, selected: $selected")
                return selected
            }

            // Check payment status
            val paymentStatus = checkPaymentStatusSync(previousMonth)
            Log.d(TAG, "Previous month payment status: $paymentStatus")

            // Final decision
            val shouldUseCurrent = paymentStatus // If paid, use current month
            val selectedMonth = if (shouldUseCurrent && monthKeys.contains(currentMonth)) {
                Log.d(TAG, "✅ Previous month paid, selected current: $currentMonth")
                currentMonth
            } else {
                Log.d(TAG, "✅ Previous month unpaid, selected previous: $previousMonth")
                previousMonth
            }

            return selectedMonth

        } catch (e: Exception) {
            Log.e(TAG, "Error in smart month selection", e)
            monthKeys.lastOrNull()
        }
    }

    /**
     * Check payment status synchronously
     */
    private fun checkPaymentStatusSync(month: String): Boolean {
        return try {
            val roomNumber = userRoomNumber ?: return false
            val latch = CountDownLatch(1)
            var isPaid = false

            roomsRef.child(roomNumber).child("payments").child(month)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val exists = snapshot.exists()
                            val status = snapshot.child("status").getValue(String::class.java)
                            isPaid = exists && status == "PAID"
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing payment status", e)
                        } finally {
                            latch.countDown()
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        latch.countDown()
                    }
                })

            latch.await(5, TimeUnit.SECONDS)
            isPaid

        } catch (e: Exception) {
            Log.e(TAG, "Error in checkPaymentStatusSync", e)
            false
        }
    }

    /**
     * Update global cache with fresh data
     */
    private fun updateGlobalCacheWithData(months: List<String>, defaultMonth: String?) {
        try {
            globalDataCache["months"] = months
            globalDataCache["selectedMonth"] = defaultMonth ?: ""
            lastCacheTime = System.currentTimeMillis()

            Log.d(TAG, "✅ Global cache updated with fresh data")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating global cache with data", e)
        }
    }

    /**
     * Setup complete UI with all data
     */
    private fun setupCompleteUI(months: List<String>, defaultMonth: String?, historySnapshot: DataSnapshot) {
        try {
            // Set all data
            monthKeys = months
            selectedMonth = defaultMonth ?: months.lastOrNull() ?: ""
            val displayMonths = months.map { formatMonthForDisplay(it) }

            // Setup spinner
            setupSpinnerWithData(displayMonths, selectedMonth)

            // Process and display usage data
            val usageData = processUsageDataFromSnapshot(historySnapshot, selectedMonth)
            updateUsageDisplay(usageData)

            // Update payment UI
            lifecycleScope.launch {
                val paymentStatus = withContext(Dispatchers.IO) {
                    checkPaymentStatusSync(selectedMonth)
                }
                updatePaymentUI(usageData, paymentStatus)

                // Cache the data
                cachedMonthData[selectedMonth] = CachedData(usageData, paymentStatus, System.currentTimeMillis())
            }

            // Setup payment status listener
            setupPaymentStatusListener()

            Log.d(TAG, "✅ Complete UI setup for month: $selectedMonth")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up complete UI", e)
            showErrorState("Lỗi thiết lập giao diện")
        }
    }

    /**
     * Setup spinner with data
     */
    private fun setupSpinnerWithData(displayMonths: List<String>, selectedMonth: String) {
        try {
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                displayMonths
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            binding.spnMonthPicker.adapter = adapter

            // Set selection
            val selectedIndex = monthKeys.indexOf(selectedMonth)
            if (selectedIndex >= 0) {
                binding.spnMonthPicker.setSelection(selectedIndex)
            }

            // Enable and set listener
            binding.spnMonthPicker.isEnabled = true
            binding.spnMonthPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    handleMonthSelectionChange(position)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up spinner with data", e)
        }
    }

    /**
     * Handle month selection change with caching
     */
    private fun handleMonthSelectionChange(position: Int) {
        try {
            if (position !in monthKeys.indices) return

            val newMonth = monthKeys[position]
            if (newMonth == selectedMonth) return

            selectedMonth = newMonth
            Log.d(TAG, "Month changed to: $selectedMonth")

            // Check cache first
            val cached = cachedMonthData[selectedMonth]
            val now = System.currentTimeMillis()

            if (cached != null && (now - cached.timestamp) < CACHE_EXPIRY_MS) {
                // Use cached data
                Log.d(TAG, "Using cached data for month: $selectedMonth")
                updateUsageDisplay(cached.usageData)
                updatePaymentUI(cached.usageData, cached.paymentStatus)
            } else {
                // Load fresh data
                lifecycleScope.launch {
                    loadMonthDataFresh(selectedMonth)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling month selection change", e)
        }
    }

    /**
     * Load fresh month data
     */
    private suspend fun loadMonthDataFresh(month: String) {
        try {
            val roomNumber = userRoomNumber ?: return

            val usageData = withContext(Dispatchers.IO) {
                val latch = CountDownLatch(1)
                var result: UsageData? = null

                roomsRef.child(roomNumber).child("history")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            result = processUsageDataFromSnapshot(snapshot, month)
                            latch.countDown()
                        }
                        override fun onCancelled(error: DatabaseError) {
                            latch.countDown()
                        }
                    })

                latch.await(5, TimeUnit.SECONDS)
                result ?: UsageData(0, 0, 0, 0, 0)
            }

            val paymentStatus = withContext(Dispatchers.IO) {
                checkPaymentStatusSync(month)
            }

            // Cache and update UI
            cachedMonthData[month] = CachedData(usageData, paymentStatus, System.currentTimeMillis())

            withContext(Dispatchers.Main) {
                if (selectedMonth == month && isFragmentActive) {
                    updateUsageDisplay(usageData)
                    updatePaymentUI(usageData, paymentStatus)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading fresh month data", e)
        }
    }

    private fun formatMonthForDisplay(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, parts[0].toInt())
            cal.set(Calendar.MONTH, parts[1].toInt() - 1)
            SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(cal.time)
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting month: $monthKey", e)
            monthKey
        }
    }

    /**
     * Process usage data from snapshot
     */
    private fun processUsageDataFromSnapshot(snapshot: DataSnapshot, month: String): UsageData {
        return try {
            val monthDates = snapshot.children
                .mapNotNull { it.key }
                .filter { it.startsWith(month) }
                .sorted()

            var usedElectric = 0
            var usedWater = 0

            if (monthDates.isNotEmpty()) {
                val firstDay = monthDates.first()
                val lastDay = monthDates.last()

                val firstSnapshot = snapshot.child(firstDay)
                val lastSnapshot = snapshot.child(lastDay)

                val firstElectric = firstSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                val lastElectric = lastSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                val firstWater = firstSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0
                val lastWater = lastSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0

                usedElectric = maxOf(0, lastElectric - firstElectric)
                usedWater = maxOf(0, lastWater - firstWater)
            }

            val electricCost = usedElectric * 3300
            val waterCost = usedWater * 15000
            val totalCost = electricCost + waterCost

            this.totalCost = totalCost

            UsageData(usedElectric, usedWater, electricCost, waterCost, totalCost)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing usage data from snapshot", e)
            UsageData(0, 0, 0, 0, 0)
        }
    }

    /**
     * Update usage display
     */
    private fun updateUsageDisplay(usageData: UsageData) {
        try {
            binding.apply {
                tvElectricDetail.text = "Tiêu thụ điện: ${usageData.usedElectric} × 3.300đ"
                tvElectricAmount.text = "${String.format("%,d", usageData.electricCost)}đ"
                tvWaterDetail.text = "Tiêu thụ nước: ${usageData.usedWater} × 15.000đ"
                tvWaterAmount.text = "${String.format("%,d", usageData.waterCost)}đ"
                tvTotalAmount.text = "${String.format("%,d", usageData.totalCost)}đ"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating usage display", e)
        }
    }

    /**
     * Update payment UI based on data and status
     */
    private fun updatePaymentUI(usageData: UsageData, isPaid: Boolean) {
        try {
            val isCurrentMonth = selectedMonth == currentMonth
            val isPreviousMonth = selectedMonth == previousMonth

            // Handle zero cost case
            if (usageData.totalCost == 0) {
                updateZeroCostUI()
            } else {
                updatePaymentStatusCard(isPaid, isCurrentMonth)
                updateCalculationTitle(isCurrentMonth)
                updatePaymentButton(isPaid, isCurrentMonth, isPreviousMonth)
                updatePaymentNotice(isPaid, isCurrentMonth, isPreviousMonth)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating payment UI", e)
        }
    }

    private fun setupPaymentStatusListener() {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("payments")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (isFragmentActive) {
                            Log.d(TAG, "Payment data changed, refreshing...")
                            cachedMonthData.remove(selectedMonth) // Clear cache
                            refreshPaymentStatus()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Payment listener error: ${error.message}")
                    }
                })
        }
    }

    private fun refreshPaymentStatus() {
        if (userRoomNumber != null && isFragmentActive) {
            lifecycleScope.launch {
                loadMonthDataFresh(selectedMonth)
            }
        }
    }

    private fun updateZeroCostUI() {
        try {
            val linearLayout = binding.cardPaymentStatus.getChildAt(0) as LinearLayout
            linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_green)

            binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_check_circle)
            binding.tvPaymentStatus.text = "Không cần thanh toán tháng ${getDisplayMonth()}"
            binding.tvNote.text = "Tháng này không phát sinh chi phí."

            binding.btnPayNow.isEnabled = false
            binding.btnPayNow.text = "✅ Không cần thanh toán"
            binding.btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))

            binding.cardPaymentNotice.visibility = View.GONE
            updateCalculationTitle(selectedMonth == currentMonth)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating zero cost UI", e)
        }
    }

    private fun updatePaymentStatusCard(isPaid: Boolean, isCurrentMonth: Boolean) {
        try {
            val linearLayout = binding.cardPaymentStatus.getChildAt(0) as LinearLayout

            if (isPaid) {
                linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_green)
                binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_check_circle)
                binding.tvPaymentStatus.text = "Đã thanh toán tháng ${getDisplayMonth()}"
                binding.tvNote.text = "Cảm ơn bạn đã thanh toán đúng hạn!"
            } else {
                if (isCurrentMonth) {
                    linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_orange)
                    binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_pending)
                    binding.tvPaymentStatus.text = "Tạm tính tháng ${getDisplayMonth()}"
                    binding.tvNote.text = "Đây là số liệu tạm tính. Thanh toán vào ngày 01 tháng sau."
                } else {
                    linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_red)
                    binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_warning)
                    binding.tvPaymentStatus.text = "Chưa thanh toán tháng ${getDisplayMonth()}"
                    binding.tvNote.text = "Vui lòng thanh toán để tránh bị cắt dịch vụ."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating payment status card", e)
        }
    }

    private fun updateCalculationTitle(isCurrentMonth: Boolean) {
        try {
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val title = if (isCurrentMonth) {
                "Tạm tính đến ngày: $today"
            } else {
                "Chi tiết tháng ${getDisplayMonth()}"
            }
            binding.tvCalculationTitle.text = title
        } catch (e: Exception) {
            Log.e(TAG, "Error updating calculation title", e)
        }
    }

    private fun updatePaymentButton(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
        try {
            when {
                isPaid -> {
                    binding.btnPayNow.isEnabled = false
                    binding.btnPayNow.text = "✅ Đã thanh toán"
                    binding.btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))
                }
                isCurrentMonth -> {
                    binding.btnPayNow.isEnabled = false
                    binding.btnPayNow.text = "⏳ Chưa đến hạn thanh toán"
                    binding.btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))
                }
                isPreviousMonth -> {
                    binding.btnPayNow.isEnabled = true
                    binding.btnPayNow.text = "💳 Xác nhận thanh toán"
                    binding.btnPayNow.background = ContextCompat.getDrawable(requireContext(), R.drawable.button_gradient_background)
                }
                else -> {
                    binding.btnPayNow.isEnabled = false
                    binding.btnPayNow.text = "❌ Quá hạn thanh toán"
                    binding.btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating payment button", e)
        }
    }

    private fun updatePaymentNotice(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
        try {
            when {
                isPaid -> {
                    binding.cardPaymentNotice.visibility = View.GONE
                }
                isCurrentMonth -> {
                    binding.cardPaymentNotice.visibility = View.VISIBLE
                    binding.tvPaymentNoticeTitle.text = "Thông báo"
                    binding.tvPaymentNoticeContent.text = "Đây là số liệu tạm tính. Thanh toán sẽ được mở vào ngày 01 tháng sau."
                }
                isPreviousMonth -> {
                    binding.cardPaymentNotice.visibility = View.VISIBLE
                    binding.tvPaymentNoticeTitle.text = "Cần thanh toán"
                    binding.tvPaymentNoticeContent.text = "Vui lòng thanh toán để tránh bị ngắt dịch vụ điện nước."
                }
                else -> {
                    binding.cardPaymentNotice.visibility = View.VISIBLE
                    binding.tvPaymentNoticeTitle.text = "Quá hạn"
                    binding.tvPaymentNoticeContent.text = "Hóa đơn này đã quá hạn thanh toán. Vui lòng liên hệ quản lý."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating payment notice", e)
        }
    }

    private fun getDisplayMonth(): String {
        return try {
            if (selectedMonth.isBlank()) return "N/A"
            val parts = selectedMonth.split("-")
            if (parts.size < 2) return "N/A"

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, parts[0].toInt())
            calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
            SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting display month", e)
            "N/A"
        }
    }

    private fun openPaymentLink() {
        // Kiểm tra room number
        if (userRoomNumber == null) {
            Toast.makeText(requireContext(), "Chưa xác định được phòng của bạn", Toast.LENGTH_SHORT).show()
            return
        }

        // Kiểm tra amount
        if (totalCost <= 0) {
            Toast.makeText(requireContext(), "Số tiền thanh toán không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val orderCode = (System.currentTimeMillis() / 1000).toInt()
        val amount = totalCost

        // Description ngắn gọn - tối đa 25 ký tự
        val monthShort = selectedMonth.substring(5, 7)
        val description = "Thanh toan P$userRoomNumber T$monthShort"

        val cancelUrl = "myapp://payment-cancel"
        val returnUrl = "myapp://payment-success"

        // Log để debug
        Log.d("PAYOS_DEBUG", "=== PAYMENT REQUEST DEBUG ===")
        Log.d("PAYOS_DEBUG", "Description: '$description' (${description.length} chars)")
        Log.d("PAYOS_DEBUG", "OrderCode: $orderCode")
        Log.d("PAYOS_DEBUG", "Amount: $amount")

        val dataToSign = "amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl"
        val signature = hmacSha256(dataToSign, com.app.buildingmanagement.BuildConfig.SIGNATURE)

        val json = JSONObject().apply {
            put("orderCode", orderCode)
            put("amount", amount)
            put("description", description)
            put("cancelUrl", cancelUrl)
            put("returnUrl", returnUrl)
            put("signature", signature)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api-merchant.payos.vn/v2/payment-requests")
            .post(requestBody)
            .addHeader("x-client-id", com.app.buildingmanagement.BuildConfig.CLIENT_ID)
            .addHeader("x-api-key", com.app.buildingmanagement.BuildConfig.API_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("PAYOS_DEBUG", "Failed to create payment link: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Lỗi API: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                try {
                    val jsonResponse = JSONObject(body)

                    // Kiểm tra lỗi từ PayOS
                    if (jsonResponse.has("code") && jsonResponse.getString("code") != "00") {
                        val errorDesc = jsonResponse.optString("desc", "Lỗi không xác định")
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Lỗi PayOS: $errorDesc", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    val checkoutUrl = jsonResponse
                        .optJSONObject("data")
                        ?.optString("checkoutUrl", "")
                        ?: ""

                    if (checkoutUrl.isNotEmpty()) {
                        activity?.runOnUiThread {
                            val intent = Intent(requireContext(), WebPayActivity::class.java)
                            intent.putExtra("url", checkoutUrl)
                            intent.putExtra("orderCode", orderCode)
                            intent.putExtra("amount", amount)
                            intent.putExtra("month", selectedMonth)
                            intent.putExtra("roomNumber", userRoomNumber)
                            startActivityForResult(intent, PAYMENT_REQUEST_CODE)
                        }
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Không thể lấy link thanh toán", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Lỗi xử lý response: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PAYMENT_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "Payment successful")
                    cachedMonthData.clear() // Clear cache
                    refreshPaymentStatus()
                    showSuccessAnimation()
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "Payment cancelled")
                    Toast.makeText(requireContext(), "Thanh toán đã bị hủy", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.d(TAG, "Payment result unknown: $resultCode")
                    Toast.makeText(requireContext(), "Kết quả thanh toán không xác định", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSuccessAnimation() {
        binding.cardPaymentStatus.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .withEndAction {
                binding.cardPaymentStatus.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    private fun hmacSha256(data: String, key: String): String {
        val hmac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmac.init(secretKeySpec)
        val hash = hmac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun showErrorState(message: String) {
        try {
            if (!isFragmentActive || _binding == null) return

            binding.apply {
                // Usage display
                tvElectricDetail.text = "Lỗi tải dữ liệu"
                tvElectricAmount.text = "---"
                tvWaterDetail.text = "Lỗi tải dữ liệu"
                tvWaterAmount.text = "---"
                tvTotalAmount.text = "---"

                // Calculation title
                tvCalculationTitle.text = "Lỗi: $message"

                // Payment status card
                val linearLayout = cardPaymentStatus.getChildAt(0) as LinearLayout
                linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_red)

                ivPaymentStatusIcon.setImageResource(R.drawable.ic_warning)
                tvPaymentStatus.text = "Lỗi hệ thống"
                tvNote.text = message

                btnPayNow.isEnabled = false
                btnPayNow.text = "❌ Không khả dụng"
                btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))

                cardPaymentNotice.visibility = View.VISIBLE
                tvPaymentNoticeTitle.text = "Lỗi hệ thống"
                tvPaymentNoticeContent.text = message

                // Enable spinner for retry
                spnMonthPicker.isEnabled = true
            }

            Log.e(TAG, "❌ Error state shown: $message")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing error state", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        _binding = null
    }
}