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
import com.app.buildingmanagement.R
import com.app.buildingmanagement.WebPayActivity
import com.app.buildingmanagement.databinding.FragmentPayBinding
import com.app.buildingmanagement.data.SharedDataManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*
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

class PayFragment : Fragment(), SharedDataManager.DataUpdateListener {

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

    // Thêm list để track các listeners
    private val activeListeners = mutableListOf<ValueEventListener>()

    companion object {
        private const val PAYMENT_REQUEST_CODE = 1001
        private const val TAG = "PayFragment"
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

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        roomsRef = database.getReference("rooms")

        // Đăng ký listener trước khi tìm room
        SharedDataManager.addListener(this)

        // Hiển thị loading state trước
        showLoadingState()

        findUserRoomWithCache()

        binding.btnPayNow.setOnClickListener {
            if (binding.btnPayNow.isEnabled) {
                openPaymentLink()
            }
        }
    }

    private fun showLoadingState() {
        Log.d(TAG, "Showing loading state for PayFragment")
        
        // CHỈ SET ALPHA, KHÔNG THAY ĐỔI TEXT VÀ ENABLED
        // Vì updateUIBasedOnMonth() sẽ handle text và enabled state
        binding.btnPayNow.alpha = 0.6f
    }

    private fun hideLoadingState() {
        Log.d(TAG, "Hiding loading state for PayFragment")
        
        // Restore alpha về normal - ĐẢNG BẢO set sau khi background được update
        binding.btnPayNow.alpha = 1.0f
    }

    override fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (_binding == null || !isAdded) return

        Log.d(TAG, "Received data update for room: $roomNumber")
        userRoomNumber = roomNumber
        // Refresh payment status khi có data mới
        refreshPaymentStatus()
        // Gọi hideLoadingState sau khi refresh
        hideLoadingState()
    }

    override fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (_binding == null || !isAdded) return

        Log.d(TAG, "Cache ready for room: $roomNumber")
        userRoomNumber = roomNumber

        // Load dữ liệu từ cache ngay lập tức
        loadAvailableMonthsFromSnapshot(roomSnapshot)
        setupPaymentStatusListener()
        checkPaymentStatus()
        loadUsageData()
        
        // Hide loading state SAU KHI updateUIBasedOnMonth được gọi
        // (sẽ được gọi trong checkPaymentStatus -> updateUIBasedOnMonth)
    }

    override fun onResume() {
        super.onResume()
        refreshPaymentStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove listener và cleanup
        SharedDataManager.removeListener(this)
        removeAllListeners()
        _binding = null
    }

    private fun removeAllListeners() {
        // Remove tất cả active listeners
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("payments").removeEventListener(paymentStatusListener)
        }
        activeListeners.clear()
    }

    // Tạo một listener riêng để có thể remove sau này
    private val paymentStatusListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            // Kiểm tra fragment vẫn còn active
            if (_binding != null && isAdded) {
                Log.d(TAG, "Payment data changed for room $userRoomNumber")
                updateUIBasedOnMonth()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            if (_binding != null && isAdded) {
                Log.e(TAG, "Payment listener error: ${error.message}")
            }
        }
    }

    private fun findUserRoomWithCache() {
        // Kiểm tra cache trước
        val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()
        val cachedSnapshot = SharedDataManager.getCachedRoomSnapshot()

        if (cachedRoomNumber != null && cachedSnapshot != null) {
            Log.d(TAG, "Using cached room number: $cachedRoomNumber")
            userRoomNumber = cachedRoomNumber

            // Tiếp tục với logic hiện tại từ cached data
            loadAvailableMonthsFromSnapshot(cachedSnapshot)
            setupPaymentStatusListener()
            checkPaymentStatus()
            loadUsageData()
            
            // QUAN TRỌNG: Hide loading state sau khi load xong từ cache
            hideLoadingState()
            return
        }

        // Nếu không có cache, thực hiện như cũ
        Log.d(TAG, "No cache available, loading from Firebase")
        findUserRoomFromFirebase()
    }

    private fun findUserRoomFromFirebase() {
        val phone = auth.currentUser?.phoneNumber ?: return

        Log.d(TAG, "Finding room for phone: $phone")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Kiểm tra fragment vẫn còn active
                if (_binding == null || !isAdded) return

                var roomFound = false

                // Duyệt qua tất cả các phòng
                for (roomSnapshot in snapshot.children) {
                    val tenantsSnapshot = roomSnapshot.child("tenants")

                    // Duyệt qua tất cả các thành viên trong phòng
                    for (tenantSnapshot in tenantsSnapshot.children) {
                        val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInTenant == phone) {
                            roomFound = true
                            userRoomNumber = roomSnapshot.key

                            Log.d(TAG, "Found user in room: $userRoomNumber, tenant: ${tenantSnapshot.key}")

                            // Cập nhật cache
                            if (userRoomNumber != null) {
                                SharedDataManager.setCachedData(roomSnapshot, userRoomNumber!!, phone)
                            }

                            // Gọi spinner sau khi đã xác định phòng
                            loadAvailableMonthsFromSnapshot(roomSnapshot)
                            setupPaymentStatusListener()

                            // Load initial data
                            checkPaymentStatus()
                            loadUsageData()
                            
                            // QUAN TRỌNG: Hide loading state sau khi load xong từ Firebase
                            hideLoadingState()

                            break // Thoát khỏi vòng lặp tenants
                        }
                    }

                    if (roomFound) {
                        break // Thoát khỏi vòng lặp rooms
                    }
                }

                if (!roomFound) {
                    Log.e(TAG, "User not found in any room")
                    hideLoadingState()
                    Toast.makeText(requireContext(), "Không tìm thấy phòng của bạn", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null && isAdded) {
                    Log.e(TAG, "Error finding user room: ${error.message}")
                    hideLoadingState()
                }
            }
        }

        // Thay đổi query - không còn sử dụng orderByChild("phone")
        roomsRef.addListenerForSingleValueEvent(listener)
    }

    private fun loadAvailableMonthsFromSnapshot(roomSnapshot: DataSnapshot) {
        val historySnapshot = roomSnapshot.child("history")

        val rawMonths = mutableSetOf<String>()
        for (dateSnapshot in historySnapshot.children) {
            val dateKey = dateSnapshot.key ?: continue
            if (dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val monthKey = dateKey.substring(0, 7)
                rawMonths.add(monthKey)
            }
        }

        monthKeys = rawMonths.sorted()
        val displayMonths = monthKeys.map {
            val parts = it.split("-")
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, parts[0].toInt())
            cal.set(Calendar.MONTH, parts[1].toInt() - 1)
            SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(cal.time)
        }

        if (monthKeys.isEmpty()) return

        // Kiểm tra fragment vẫn còn active trước khi update UI
        if (_binding == null || !isAdded) return

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayMonths)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spnMonthPicker.adapter = adapter
        binding.spnMonthPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMonth = monthKeys[position]
                loadUsageData()
                updateUIBasedOnMonth()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // LOGIC CHỌN THÁNG MẶC ĐỊNH ĐÃ ĐƯỢC CẢI THIỆN
        selectDefaultMonthFromSnapshot(historySnapshot)
    }

    private fun selectDefaultMonthFromSnapshot(historySnapshot: DataSnapshot) {
        val calendar = Calendar.getInstance()
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val currentMonthKey = monthKeyFormat.format(calendar.time)
        val prevCalendar = Calendar.getInstance()
        prevCalendar.add(Calendar.MONTH, -1)
        val previousMonthKey = monthKeyFormat.format(prevCalendar.time)

        if (monthKeys.contains(previousMonthKey)) {
            // Kiểm tra dữ liệu tháng trước từ cached snapshot
            val prevMonthDates = historySnapshot.children
                .mapNotNull { it.key }
                .filter { it.startsWith(previousMonthKey) }
                .sorted()

            if (prevMonthDates.size >= 2) {
                val firstDay = prevMonthDates.first()
                val lastDay = prevMonthDates.last()
                val firstSnapshot = historySnapshot.child(firstDay)
                val lastSnapshot = historySnapshot.child(lastDay)

                val firstElectric = firstSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                val lastElectric = lastSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                val firstWater = firstSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0
                val lastWater = lastSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0

                val prevElectric = lastElectric - firstElectric
                val prevWater = lastWater - firstWater
                val prevTotalCost = prevElectric * 3300 + prevWater * 15000

                // Kiểm tra trạng thái thanh toán tháng trước
                userRoomNumber?.let { roomNumber ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(paymentSnapshot: DataSnapshot) {
                            // Kiểm tra fragment vẫn còn active
                            if (_binding == null || !isAdded) return

                            val isPreviousMonthPaid = paymentSnapshot.exists() &&
                                    paymentSnapshot.child("status").getValue(String::class.java) == "PAID"

                            val shouldSwitchToCurrent = prevMonthDates.size < 2 ||
                                    (prevElectric == 0 && prevWater == 0) ||
                                    prevTotalCost == 0 ||
                                    isPreviousMonthPaid

                            if (shouldSwitchToCurrent) {
                                if (monthKeys.contains(currentMonthKey)) {
                                    val idx = monthKeys.indexOf(currentMonthKey)
                                    binding.spnMonthPicker.setSelection(idx)
                                    Log.d(TAG, "Chuyển sang tháng hiện tại vì tháng trước đã thanh toán hoặc không có dữ liệu")
                                } else {
                                    binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                                    Log.d(TAG, "Không có tháng hiện tại, chọn tháng cuối cùng")
                                }
                            } else {
                                val idx = monthKeys.indexOf(previousMonthKey)
                                binding.spnMonthPicker.setSelection(idx)
                                Log.d(TAG, "Hiển thị tháng trước vì chưa thanh toán và có phát sinh chi phí")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (_binding != null && isAdded) {
                                Log.e(TAG, "Lỗi kiểm tra trạng thái thanh toán: ${error.message}")
                                if (monthKeys.contains(currentMonthKey)) {
                                    val idx = monthKeys.indexOf(currentMonthKey)
                                    binding.spnMonthPicker.setSelection(idx)
                                }
                            }
                        }
                    }

                    roomsRef.child(roomNumber).child("payments").child(previousMonthKey)
                        .addListenerForSingleValueEvent(listener)
                }
            } else {
                // Không đủ dữ liệu tháng trước, chuyển sang tháng hiện tại
                if (_binding != null && isAdded) {
                    if (monthKeys.contains(currentMonthKey)) {
                        val idx = monthKeys.indexOf(currentMonthKey)
                        binding.spnMonthPicker.setSelection(idx)
                        Log.d(TAG, "Chuyển sang tháng hiện tại vì tháng trước không đủ dữ liệu")
                    } else {
                        binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                    }
                }
            }
        } else {
            // Không có tháng trước, chọn tháng hiện tại hoặc tháng mới nhất
            if (_binding != null && isAdded) {
                if (monthKeys.contains(currentMonthKey)) {
                    val idx = monthKeys.indexOf(currentMonthKey)
                    binding.spnMonthPicker.setSelection(idx)
                    Log.d(TAG, "Hiển thị tháng hiện tại mặc định")
                } else {
                    binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                    Log.d(TAG, "Hiển thị tháng mới nhất vì không có tháng hiện tại")
                }
            }
        }
    }

    private fun loadAvailableMonths() {
        userRoomNumber?.let { roomNumber ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Kiểm tra fragment vẫn còn active
                    if (_binding == null || !isAdded) return

                    val rawMonths = mutableSetOf<String>()
                    for (dateSnapshot in snapshot.children) {
                        val dateKey = dateSnapshot.key ?: continue
                        if (dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            val monthKey = dateKey.substring(0, 7)
                            rawMonths.add(monthKey)
                        }
                    }
                    monthKeys = rawMonths.sorted()
                    val displayMonths = monthKeys.map {
                        val parts = it.split("-")
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.YEAR, parts[0].toInt())
                        cal.set(Calendar.MONTH, parts[1].toInt() - 1)
                        SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(cal.time)
                    }

                    if (monthKeys.isEmpty()) return

                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayMonths)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spnMonthPicker.adapter = adapter
                    binding.spnMonthPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            selectedMonth = monthKeys[position]
                            loadUsageData()
                            updateUIBasedOnMonth()
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }

                    // LOGIC CHỌN THÁNG MẶC ĐỊNH ĐÃ ĐƯỢC CẢI THIỆN
                    val calendar = Calendar.getInstance()
                    val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    val currentMonthKey = monthKeyFormat.format(calendar.time)
                    val prevCalendar = Calendar.getInstance()
                    prevCalendar.add(Calendar.MONTH, -1)
                    val previousMonthKey = monthKeyFormat.format(prevCalendar.time)

                    if (monthKeys.contains(previousMonthKey)) {
                        // Kiểm tra dữ liệu tháng trước
                        val prevMonthDates = snapshot.children
                            .mapNotNull { it.key }
                            .filter { it.startsWith(previousMonthKey) }
                            .sorted()

                        if (prevMonthDates.size >= 2) {
                            val firstDay = prevMonthDates.first()
                            val lastDay = prevMonthDates.last()
                            val firstSnapshot = snapshot.child(firstDay)
                            val lastSnapshot = snapshot.child(lastDay)

                            val firstElectric = firstSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                            val lastElectric = lastSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                            val firstWater = firstSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0
                            val lastWater = lastSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0

                            val prevElectric = lastElectric - firstElectric
                            val prevWater = lastWater - firstWater
                            val prevTotalCost = prevElectric * 3300 + prevWater * 15000

                            // Kiểm tra trạng thái thanh toán tháng trước
                            val paymentListener = object : ValueEventListener {
                                override fun onDataChange(paymentSnapshot: DataSnapshot) {
                                    // Kiểm tra fragment vẫn còn active
                                    if (_binding == null || !isAdded) return

                                    val isPreviousMonthPaid = paymentSnapshot.exists() &&
                                            paymentSnapshot.child("status").getValue(String::class.java) == "PAID"

                                    val shouldSwitchToCurrent = prevMonthDates.size < 2 ||
                                            (prevElectric == 0 && prevWater == 0) ||
                                            prevTotalCost == 0 ||
                                            isPreviousMonthPaid

                                    if (shouldSwitchToCurrent) {
                                        if (monthKeys.contains(currentMonthKey)) {
                                            val idx = monthKeys.indexOf(currentMonthKey)
                                            binding.spnMonthPicker.setSelection(idx)
                                            Log.d(TAG, "Chuyển sang tháng hiện tại vì tháng trước đã thanh toán hoặc không có dữ liệu")
                                        } else {
                                            binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                                            Log.d(TAG, "Không có tháng hiện tại, chọn tháng cuối cùng")
                                        }
                                    } else {
                                        val idx = monthKeys.indexOf(previousMonthKey)
                                        binding.spnMonthPicker.setSelection(idx)
                                        Log.d(TAG, "Hiển thị tháng trước vì chưa thanh toán và có phát sinh chi phí")
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    if (_binding != null && isAdded) {
                                        Log.e(TAG, "Lỗi kiểm tra trạng thái thanh toán: ${error.message}")
                                        if (monthKeys.contains(currentMonthKey)) {
                                            val idx = monthKeys.indexOf(currentMonthKey)
                                            binding.spnMonthPicker.setSelection(idx)
                                        }
                                    }
                                }
                            }

                            roomsRef.child(roomNumber).child("payments").child(previousMonthKey)
                                .addListenerForSingleValueEvent(paymentListener)
                        } else {
                            // Không đủ dữ liệu tháng trước, chuyển sang tháng hiện tại
                            if (monthKeys.contains(currentMonthKey)) {
                                val idx = monthKeys.indexOf(currentMonthKey)
                                binding.spnMonthPicker.setSelection(idx)
                                Log.d(TAG, "Chuyển sang tháng hiện tại vì tháng trước không đủ dữ liệu")
                            } else {
                                binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                            }
                        }
                    } else {
                        // Không có tháng trước, chọn tháng hiện tại hoặc tháng mới nhất
                        if (monthKeys.contains(currentMonthKey)) {
                            val idx = monthKeys.indexOf(currentMonthKey)
                            binding.spnMonthPicker.setSelection(idx)
                            Log.d(TAG, "Hiển thị tháng hiện tại mặc định")
                        } else {
                            binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                            Log.d(TAG, "Hiển thị tháng mới nhất vì không có tháng hiện tại")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding != null && isAdded) {
                        Log.e(TAG, "Lỗi khi tải danh sách tháng từ history: ${error.message}")
                    }
                }
            }

            roomsRef.child(roomNumber).child("history")
                .addListenerForSingleValueEvent(listener)
        }
    }

    private fun determineDefaultMonth(monthKeys: List<String>) {
        userRoomNumber?.let { roomNumber ->
            val calendar = Calendar.getInstance()
            val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            currentMonth = monthKeyFormat.format(calendar.time)
            calendar.add(Calendar.MONTH, -1)
            previousMonth = monthKeyFormat.format(calendar.time)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Kiểm tra fragment vẫn còn active
                    if (_binding == null || !isAdded) return

                    val isPreviousMonthPaid = snapshot.exists() &&
                            snapshot.child("status").getValue(String::class.java) == "PAID"

                    val defaultMonth = if (!isPreviousMonthPaid) currentMonth else previousMonth
                    val index = monthKeys.indexOf(defaultMonth)

                    if (index in monthKeys.indices) {
                        binding.spnMonthPicker.setSelection(index)
                    } else {
                        binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            val historyRef = roomsRef.child(roomNumber).child("history").child(previousMonth)
            historyRef.addListenerForSingleValueEvent(listener)
        }
    }

    private fun setupPaymentStatusListener() {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("payments")
                .addValueEventListener(paymentStatusListener)
        }
    }

    private fun refreshPaymentStatus() {
        if (userRoomNumber != null && _binding != null && isAdded) {
            checkPaymentStatus()
            loadUsageData()
            updateUIBasedOnMonth()
        }
    }

    private fun setupMonthSpinner() {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        currentMonth = monthKeyFormat.format(calendar.time)
        calendar.add(Calendar.MONTH, -1)
        previousMonth = monthKeyFormat.format(calendar.time)

        val months = mutableListOf<String>()
        val monthKeys = mutableListOf<String>()

        calendar.add(Calendar.MONTH, -4)

        for (i in 0..5) {
            monthKeys.add(monthKeyFormat.format(calendar.time))
            months.add(monthFormat.format(calendar.time))
            calendar.add(Calendar.MONTH, 1)
        }

        if (_binding != null && isAdded) {
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, months)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spnMonthPicker.adapter = adapter

            binding.spnMonthPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedMonth = monthKeys[position]
                    loadUsageData()
                    updateUIBasedOnMonth()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun checkPaymentStatus() {
        updateUIBasedOnMonth()
    }

    private fun updateUIBasedOnMonth() {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        if (selectedMonth.isNotBlank()) {
            val parts = selectedMonth.split("-")
            if (parts.size >= 2) {
                currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val cal = Calendar.getInstance()
                cal.time = Date()
                cal.add(Calendar.MONTH, -1)
                previousMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
            }
        }

        userRoomNumber?.let { roomNumber ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Kiểm tra fragment vẫn còn active
                    if (_binding == null || !isAdded) return

                    // Validation: Kiểm tra selectedMonth có hợp lệ không
                    if (selectedMonth.isBlank() || !selectedMonth.matches(Regex("\\d{4}-\\d{2}"))) {
                        Log.e(TAG, "Invalid selectedMonth: '$selectedMonth'")
                        return
                    }

                    // Logic mới: sử dụng calculateMonthlyConsumption
                    val prevMonth = Calendar.getInstance().apply {
                        val parts = selectedMonth.split("-")
                        if (parts.size < 2) {
                            Log.e(TAG, "Invalid selectedMonth format: '$selectedMonth'")
                            return@apply
                        }
                        try {
                            set(Calendar.YEAR, parts[0].toInt())
                            set(Calendar.MONTH, parts[1].toInt() - 1)
                            add(Calendar.MONTH, -1)
                        } catch (e: NumberFormatException) {
                            Log.e(TAG, "Error parsing selectedMonth: '$selectedMonth'", e)
                            return@apply
                        }
                    }
                    val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevMonth.time)
                    
                    // Lấy dữ liệu tháng hiện tại và tháng trước
                    val currentMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    val prevMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    
                    // Thu thập dữ liệu từ snapshot
                    for (dateSnapshot in snapshot.children) {
                        val dateKey = dateSnapshot.key ?: continue
                        val electric = dateSnapshot.child("electric").getValue(Long::class.java)?.toInt()
                        val water = dateSnapshot.child("water").getValue(Long::class.java)?.toInt()
                        
                        when {
                            dateKey.startsWith(selectedMonth) -> {
                                currentMonthData[dateKey] = Pair(electric, water)
                            }
                            dateKey.startsWith(prevMonthKey) -> {
                                prevMonthData[dateKey] = Pair(electric, water)
                            }
                        }
                    }
                    
                    Log.d(TAG, "Current month data: $currentMonthData")
                    Log.d(TAG, "Previous month data: $prevMonthData")
                    
                    // Tính consumption bằng method mới
                    val usedElectric = calculateMonthlyConsumption(currentMonthData, prevMonthData, true)
                    val usedWater = calculateMonthlyConsumption(currentMonthData, prevMonthData, false)
                    
                    val electricCost = usedElectric * 3300
                    val waterCost = usedWater * 15000
                    val total = electricCost + waterCost
                    val isCurrentMonth = selectedMonth == currentMonth

                    if (usedElectric == 0 && usedWater == 0 && total == 0) {
                        val linearLayout = binding.cardPaymentStatus.getChildAt(0) as LinearLayout
                        linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_green)
                        binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        binding.tvPaymentStatus.text = "Không cần thanh toán tháng ${getDisplayMonth()}"
                        binding.tvNote.text = "Tháng này không phát sinh chi phí."
                        binding.btnPayNow.isEnabled = false
                        binding.btnPayNow.text = "✅ Không cần thanh toán"
                        binding.btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))
                        binding.cardPaymentNotice.visibility = View.GONE
                        updateCalculationTitle(isCurrentMonth)
                    } else {
                        checkSelectedMonthPaymentStatus { isPaid ->
                            // Kiểm tra fragment vẫn còn active trong callback
                            if (_binding == null || !isAdded) return@checkSelectedMonthPaymentStatus

                            if (isCurrentMonth) {
                                updatePaymentStatusCard(false, true)
                                updateCalculationTitle(true)
                                updatePaymentButton(false, true, false)
                                updatePaymentNotice(false, true, false)
                            } else {
                                val isPreviousMonth = selectedMonth == previousMonth
                                updatePaymentStatusCard(isPaid, isCurrentMonth)
                                updateCalculationTitle(isCurrentMonth)
                                updatePaymentButton(isPaid, isCurrentMonth, isPreviousMonth)
                                updatePaymentNotice(isPaid, isCurrentMonth, isPreviousMonth)
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }

            roomsRef.child(roomNumber).child("history")
                .addListenerForSingleValueEvent(listener)
        }
    }

    private fun checkSelectedMonthPaymentStatus(callback: (Boolean) -> Unit) {
        userRoomNumber?.let { roomNumber ->
            Log.d(TAG, "=== DEBUG PAYMENT STATUS ===")
            Log.d(TAG, "Room Number: $roomNumber")
            Log.d(TAG, "Selected Month: $selectedMonth")
            Log.d(TAG, "Current Month: $currentMonth")
            Log.d(TAG, "Previous Month: $previousMonth")

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Kiểm tra fragment vẫn còn active
                    if (_binding == null || !isAdded) {
                        return
                    }

                    val exists = snapshot.exists()
                    val status = snapshot.child("status").getValue(String::class.java)
                    val isPaid = exists && status == "PAID"

                    Log.d(TAG, "Payment exists: $exists")
                    Log.d(TAG, "Payment status: $status")
                    Log.d(TAG, "Is Paid: $isPaid")
                    Log.d(TAG, "Database path: rooms/$roomNumber/payments/$selectedMonth")

                    callback(isPaid)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding != null && isAdded) {
                        Log.e(TAG, "Database error: ${error.message}")
                        callback(false)
                    }
                }
            }

            roomsRef.child(roomNumber).child("payments").child(selectedMonth)
                .addListenerForSingleValueEvent(listener)
        } ?: callback(false)
    }

    private fun updatePaymentStatusCard(isPaid: Boolean, isCurrentMonth: Boolean) {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        val linearLayout = binding.cardPaymentStatus.getChildAt(0) as LinearLayout

        if (isPaid) {
            linearLayout.background = ContextCompat.getDrawable(
                requireContext(), R.drawable.gradient_green
            )
            binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_check_circle)
            binding.tvPaymentStatus.text = "Đã thanh toán tháng ${getDisplayMonth()}"
            binding.tvNote.text = "Cảm ơn bạn đã thanh toán đúng hạn!"
        } else {
            if (isCurrentMonth) {
                linearLayout.background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.gradient_orange
                )
                binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_pending)
                binding.tvPaymentStatus.text = "Tạm tính tháng ${getDisplayMonth()}"
                binding.tvNote.text = "Đây là số liệu tạm tính. Thanh toán vào ngày 01 tháng sau."
            } else {
                linearLayout.background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.gradient_red
                )
                binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_warning)
                binding.tvPaymentStatus.text = "Chưa thanh toán tháng ${getDisplayMonth()}"
                binding.tvNote.text = "Vui lòng thanh toán để tránh bị cắt dịch vụ."
            }
        }
    }

    private fun updateCalculationTitle(isCurrentMonth: Boolean) {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val title = if (isCurrentMonth) {
            "Tạm tính đến ngày: $today"
        } else {
            "Chi tiết tháng ${getDisplayMonth()}"
        }
        binding.tvCalculationTitle.text = title
    }

    private fun updatePaymentButton(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        when {
            isPaid -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = "✅ Đã thanh toán"
                binding.btnPayNow.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                )
            }
            isCurrentMonth -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = "⏳ Chưa đến hạn thanh toán"
                binding.btnPayNow.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                )
            }
            isPreviousMonth -> {
                binding.btnPayNow.isEnabled = true
                binding.btnPayNow.text = "💳 Xác nhận thanh toán"
                binding.btnPayNow.background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.button_gradient_background
                )
            }
            else -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = "❌ Quá hạn thanh toán"
                binding.btnPayNow.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                )
            }
        }
        
        // QUAN TRỌNG: Đảm bảo alpha = 1.0f sau khi update button state
        hideLoadingState()
    }

    private fun updatePaymentNotice(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        when {
            isPaid -> {
                binding.cardPaymentNotice.visibility = View.GONE
            }
            isCurrentMonth -> {
                binding.cardPaymentNotice.visibility = View.VISIBLE
                binding.tvPaymentNoticeTitle.text = "Thông báo"
                binding.tvPaymentNoticeContent.text =
                    "Đây là số liệu tạm tính. Thanh toán sẽ được mở vào ngày 01 tháng sau."
            }
            isPreviousMonth -> {
                binding.cardPaymentNotice.visibility = View.VISIBLE
                binding.tvPaymentNoticeTitle.text = "Cần thanh toán"
                binding.tvPaymentNoticeContent.text =
                    "Vui lòng thanh toán để tránh bị ngắt dịch vụ điện nước."
            }
            else -> {
                binding.cardPaymentNotice.visibility = View.VISIBLE
                binding.tvPaymentNoticeTitle.text = "Quá hạn"
                binding.tvPaymentNoticeContent.text =
                    "Hóa đơn này đã quá hạn thanh toán. Vui lòng liên hệ quản lý."
            }
        }
    }

    private fun getDisplayMonth(): String {
        if (selectedMonth.isBlank()) return "N/A"
        val parts = selectedMonth.split("-")
        if (parts.size < 2) return "N/A"
        return try {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, parts[0].toInt())
            calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
            SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun loadUsageData() {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        userRoomNumber?.let { roomNumber ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Kiểm tra fragment vẫn còn active
                    if (_binding == null || !isAdded) return

                    Log.d(TAG, "=== CALCULATING PAYMENT CONSUMPTION ===")
                    Log.d(TAG, "Selected Month: $selectedMonth")

                    // Validation: Kiểm tra selectedMonth có hợp lệ không
                    if (selectedMonth.isBlank() || !selectedMonth.matches(Regex("\\d{4}-\\d{2}"))) {
                        Log.e(TAG, "Invalid selectedMonth in loadUsageData: '$selectedMonth'")
                        return
                    }

                    // Logic mới: giống ChartFragment và HomeFragment
                    val prevMonth = Calendar.getInstance().apply {
                        val parts = selectedMonth.split("-")
                        if (parts.size < 2) {
                            Log.e(TAG, "Invalid selectedMonth format in loadUsageData: '$selectedMonth'")
                            return@apply
                        }
                        try {
                            set(Calendar.YEAR, parts[0].toInt())
                            set(Calendar.MONTH, parts[1].toInt() - 1)
                            add(Calendar.MONTH, -1)
                        } catch (e: NumberFormatException) {
                            Log.e(TAG, "Error parsing selectedMonth in loadUsageData: '$selectedMonth'", e)
                            return@apply
                        }
                    }
                    val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevMonth.time)
                    
                    // Lấy dữ liệu tháng hiện tại và tháng trước
                    val currentMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    val prevMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    
                    // Thu thập dữ liệu từ snapshot
                    for (dateSnapshot in snapshot.children) {
                        val dateKey = dateSnapshot.key ?: continue
                        val electric = dateSnapshot.child("electric").getValue(Long::class.java)?.toInt()
                        val water = dateSnapshot.child("water").getValue(Long::class.java)?.toInt()
                        
                        when {
                            dateKey.startsWith(selectedMonth) -> {
                                currentMonthData[dateKey] = Pair(electric, water)
                            }
                            dateKey.startsWith(prevMonthKey) -> {
                                prevMonthData[dateKey] = Pair(electric, water)
                            }
                        }
                    }
                    
                    Log.d(TAG, "Current month data: $currentMonthData")
                    Log.d(TAG, "Previous month data: $prevMonthData")
                    
                    // Tính consumption cho điện
                    val usedElectric = calculateMonthlyConsumption(
                        currentMonthData, prevMonthData, true
                    )
                    
                    // Tính consumption cho nước  
                    val usedWater = calculateMonthlyConsumption(
                        currentMonthData, prevMonthData, false
                    )

                    Log.d(TAG, "Final consumption - Electric: $usedElectric, Water: $usedWater")

                    val electricCost = usedElectric * 3300
                    val waterCost = usedWater * 15000
                    totalCost = electricCost + waterCost

                    val electricCostFormatted = String.format("%,d", electricCost)
                    val waterCostFormatted = String.format("%,d", waterCost)
                    val totalCostFormatted = String.format("%,d", totalCost)

                    binding.tvElectricDetail.text = "Tiêu thụ điện: $usedElectric × 3.300đ"
                    binding.tvElectricAmount.text = "${electricCostFormatted}đ"

                    binding.tvWaterDetail.text = "Tiêu thụ nước: $usedWater × 15.000đ"
                    binding.tvWaterAmount.text = "${waterCostFormatted}đ"

                    binding.tvTotalAmount.text = "${totalCostFormatted}đ"
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            roomsRef.child(roomNumber).child("history")
                .addListenerForSingleValueEvent(listener)
        }
    }
    
    /**
     * Tính consumption tháng theo logic giống ChartFragment và HomeFragment
     */
    private fun calculateMonthlyConsumption(
        currentMonthData: Map<String, Pair<Int?, Int?>>,
        prevMonthData: Map<String, Pair<Int?, Int?>>,
        isElectric: Boolean
    ): Int {
        val type = if (isElectric) "ELECTRIC" else "WATER"
        Log.d(TAG, "=== Calculating $type consumption ===")
        
        // Extract values cho loại cần tính
        val currentValues = currentMonthData.values
            .mapNotNull { if (isElectric) it.first else it.second }
            .sorted()
            
        val prevValues = prevMonthData.values
            .mapNotNull { if (isElectric) it.first else it.second }
            .sorted()
        
        Log.d(TAG, "$type - Current month values: $currentValues")
        Log.d(TAG, "$type - Previous month values: $prevValues")
        
        if (currentValues.isEmpty()) {
            Log.d(TAG, "$type - No current data, returning 0")
            return 0
        }
        
        val currentMaxValue = currentValues.maxOrNull() ?: 0
        val prevMonthLastValue = prevValues.lastOrNull()
        
        val result = if (prevMonthLastValue != null) {
            // Trường hợp bình thường: có dữ liệu tháng trước
            Log.d(TAG, "$type - Normal case: $currentMaxValue - $prevMonthLastValue = ${currentMaxValue - prevMonthLastValue}")
            currentMaxValue - prevMonthLastValue
        } else {
            // Trường hợp đặc biệt: không có dữ liệu tháng trước
            val currentMinValue = currentValues.minOrNull() ?: 0
            Log.d(TAG, "$type - Special case: $currentMaxValue - $currentMinValue = ${currentMaxValue - currentMinValue}")
            currentMaxValue - currentMinValue
        }
        
        Log.d(TAG, "$type - Final result: $result")
        return maxOf(0, result) // Đảm bảo không âm
    }

    private fun openPaymentLink() {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        if (userRoomNumber == null) {
            Toast.makeText(requireContext(), "Chưa xác định được phòng của bạn", Toast.LENGTH_SHORT).show()
            return
        }

        if (totalCost <= 0) {
            Toast.makeText(requireContext(), "Số tiền thanh toán không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val orderCode = (System.currentTimeMillis() / 1000).toInt()
        val amount = totalCost

        val monthShort = selectedMonth.substring(5, 7)
        val description = "Thanh toan P$userRoomNumber T$monthShort"

        val cancelUrl = "myapp://payment-cancel"
        val returnUrl = "myapp://payment-success"

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
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                Log.e("PAYOS_DEBUG", "Failed to create payment link: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    activity?.runOnUiThread {
                        if (_binding != null && isAdded) {
                            Toast.makeText(requireContext(), "Lỗi API: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }

                try {
                    val jsonResponse = JSONObject(body)

                    if (jsonResponse.has("code") && jsonResponse.getString("code") != "00") {
                        val errorDesc = jsonResponse.optString("desc", "Lỗi không xác định")
                        activity?.runOnUiThread {
                            if (_binding != null && isAdded) {
                                Toast.makeText(requireContext(), "Lỗi PayOS: $errorDesc", Toast.LENGTH_LONG).show()
                            }
                        }
                        return
                    }

                    val checkoutUrl = jsonResponse
                        .optJSONObject("data")
                        ?.optString("checkoutUrl", "")
                        ?: ""

                    if (checkoutUrl.isNotEmpty()) {
                        activity?.runOnUiThread {
                            if (_binding != null && isAdded) {
                                val intent = Intent(requireContext(), WebPayActivity::class.java)
                                intent.putExtra("url", checkoutUrl)
                                intent.putExtra("orderCode", orderCode)
                                intent.putExtra("amount", amount)
                                intent.putExtra("month", selectedMonth)
                                intent.putExtra("roomNumber", userRoomNumber)
                                startActivityForResult(intent, PAYMENT_REQUEST_CODE)
                            }
                        }
                    } else {
                        activity?.runOnUiThread {
                            if (_binding != null && isAdded) {
                                Toast.makeText(requireContext(), "Không thể lấy link thanh toán", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        if (_binding != null && isAdded) {
                            Toast.makeText(requireContext(), "Lỗi xử lý response: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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
                    refreshPaymentStatus()
                    showSuccessAnimation()
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "Payment cancelled")
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Thanh toán đã bị hủy", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    Log.d(TAG, "Payment result unknown: $resultCode")
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Kết quả thanh toán không xác định", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showSuccessAnimation() {
        // Kiểm tra fragment vẫn còn active
        if (_binding == null || !isAdded) return

        binding.cardPaymentStatus.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .withEndAction {
                if (_binding != null && isAdded) {
                    binding.cardPaymentStatus.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
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
}