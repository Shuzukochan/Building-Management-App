package com.app.buildingmanagement.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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

    private val activeListeners = mutableListOf<ValueEventListener>()

    private val paymentRequestCode = 1001

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

        SharedDataManager.addListener(this)

        showLoadingState()

        findUserRoomWithCache()

        binding.btnPayNow.setOnClickListener {
            if (binding.btnPayNow.isEnabled) {
                openPaymentLink()
            }
        }
    }

    private fun showLoadingState() {
        binding.btnPayNow.alpha = 0.6f
    }

    private fun hideLoadingState() {
        binding.btnPayNow.alpha = 1.0f
    }

    override fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (_binding == null || !isAdded) return

        userRoomNumber = roomNumber
        refreshPaymentStatus()
        hideLoadingState()
    }

    override fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (_binding == null || !isAdded) return

        userRoomNumber = roomNumber

        loadAvailableMonthsFromSnapshot(roomSnapshot)
        setupPaymentStatusListener()
        checkPaymentStatus()
        loadUsageData()
    }

    override fun onResume() {
        super.onResume()
        refreshPaymentStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SharedDataManager.removeListener(this)
        removeAllListeners()
        _binding = null
    }

    private fun removeAllListeners() {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("payments").removeEventListener(paymentStatusListener)
        }
        activeListeners.clear()
    }

    private val paymentStatusListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (_binding != null && isAdded) {
                updateUIBasedOnMonth()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            // Handle error silently
        }
    }

    private fun findUserRoomWithCache() {
        val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()
        val cachedSnapshot = SharedDataManager.getCachedRoomSnapshot()

        if (cachedRoomNumber != null && cachedSnapshot != null) {
            userRoomNumber = cachedRoomNumber

            loadAvailableMonthsFromSnapshot(cachedSnapshot)
            setupPaymentStatusListener()
            checkPaymentStatus()
            loadUsageData()
            
            hideLoadingState()
            return
        }

        findUserRoomFromFirebase()
    }

    private fun findUserRoomFromFirebase() {
        val phone = auth.currentUser?.phoneNumber ?: return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null || !isAdded) return

                var roomFound = false

                for (roomSnapshot in snapshot.children) {
                    val tenantsSnapshot = roomSnapshot.child("tenants")

                    for (tenantSnapshot in tenantsSnapshot.children) {
                        val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInTenant == phone) {
                            roomFound = true
                            userRoomNumber = roomSnapshot.key

                            if (userRoomNumber != null) {
                                SharedDataManager.setCachedData(roomSnapshot, userRoomNumber!!, phone)
                            }

                            loadAvailableMonthsFromSnapshot(roomSnapshot)
                            setupPaymentStatusListener()

                            checkPaymentStatus()
                            loadUsageData()
                            
                            hideLoadingState()

                            break
                        }
                    }

                    if (roomFound) {
                        break
                    }
                }

                if (!roomFound) {
                    hideLoadingState()
                    Toast.makeText(requireContext(), "Không tìm thấy phòng của bạn", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null && isAdded) {
                    hideLoadingState()
                }
            }
        }

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

                userRoomNumber?.let { roomNumber ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(paymentSnapshot: DataSnapshot) {
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
                                } else {
                                    binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                                }
                            } else {
                                val idx = monthKeys.indexOf(previousMonthKey)
                                binding.spnMonthPicker.setSelection(idx)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (_binding != null && isAdded) {
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
                if (_binding != null && isAdded) {
                    if (monthKeys.contains(currentMonthKey)) {
                        val idx = monthKeys.indexOf(currentMonthKey)
                        binding.spnMonthPicker.setSelection(idx)
                    } else {
                        binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                    }
                }
            }
        } else {
            if (_binding != null && isAdded) {
                if (monthKeys.contains(currentMonthKey)) {
                    val idx = monthKeys.indexOf(currentMonthKey)
                    binding.spnMonthPicker.setSelection(idx)
                } else {
                    binding.spnMonthPicker.setSelection(monthKeys.size - 1)
                }
            }
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

    private fun checkPaymentStatus() {
        updateUIBasedOnMonth()
    }

    private fun updateUIBasedOnMonth() {
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
                    if (_binding == null || !isAdded) return

                    if (selectedMonth.isBlank() || !selectedMonth.matches(Regex("\\d{4}-\\d{2}"))) {
                        return
                    }

                    val prevMonth = Calendar.getInstance().apply {
                        val parts = selectedMonth.split("-")
                        if (parts.size < 2) {
                            return@apply
                        }
                        try {
                            set(Calendar.YEAR, parts[0].toInt())
                            set(Calendar.MONTH, parts[1].toInt() - 1)
                            add(Calendar.MONTH, -1)
                        } catch (e: NumberFormatException) {
                            return@apply
                        }
                    }
                    val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevMonth.time)
                    
                    val currentMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    val prevMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    
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
                    
                    val usedElectric = calculateMonthlyConsumption(currentMonthData, prevMonthData, true)
                    val usedWater = calculateMonthlyConsumption(currentMonthData, prevMonthData, false)
                    
                    val electricCost = usedElectric * 3300
                    val waterCost = usedWater * 15000
                    val total = electricCost + waterCost
                    val isCurrentMonth = selectedMonth == currentMonth

                    if (usedElectric == 0 && usedWater == 0 && total == 0) {
                        val linearLayout = binding.cardPaymentStatus.getChildAt(0) as LinearLayout
                        linearLayout.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_green)
                        binding.tvPaymentStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)
                        binding.tvPaymentStatus.text = getString(R.string.payment_no_need_month, getDisplayMonth())
                        binding.tvNote.text = getString(R.string.payment_no_cost_note)
                        binding.btnPayNow.isEnabled = false
                        binding.btnPayNow.text = getString(R.string.payment_no_need_button)
                        binding.btnPayNow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_disabled))
                        binding.cardPaymentNotice.visibility = View.GONE
                        updateCalculationTitle(isCurrentMonth)
                    } else {
                        checkSelectedMonthPaymentStatus { isPaid ->
                            if (_binding == null || !isAdded) return@checkSelectedMonthPaymentStatus

                            if (isCurrentMonth) {
                                updatePaymentStatusCard(isPaid = false, isCurrentMonth = true)
                                updateCalculationTitle(true)
                                updatePaymentButton(
                                    isPaid = false,
                                    isCurrentMonth = true,
                                    isPreviousMonth = false
                                )
                                updatePaymentNotice(
                                    isPaid = false,
                                    isCurrentMonth = true,
                                    isPreviousMonth = false
                                )
                            } else {
                                val isPreviousMonth = selectedMonth == previousMonth
                                updatePaymentStatusCard(isPaid, false)
                                updateCalculationTitle(false)
                                updatePaymentButton(isPaid, false, isPreviousMonth)
                                updatePaymentNotice(isPaid, false, isPreviousMonth)
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
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null || !isAdded) {
                        return
                    }

                    val exists = snapshot.exists()
                    val status = snapshot.child("status").getValue(String::class.java)
                    val isPaid = exists && status == "PAID"

                    callback(isPaid)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding != null && isAdded) {
                        callback(false)
                    }
                }
            }

            roomsRef.child(roomNumber).child("payments").child(selectedMonth)
                .addListenerForSingleValueEvent(listener)
        } ?: callback(false)
    }

    private fun updatePaymentStatusCard(isPaid: Boolean, isCurrentMonth: Boolean) {
        if (_binding == null || !isAdded) return

        val linearLayout = binding.cardPaymentStatus.getChildAt(0) as LinearLayout

        if (isPaid) {
            linearLayout.background = ContextCompat.getDrawable(
                requireContext(), R.drawable.gradient_green
            )
            binding.tvPaymentStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)
            binding.tvPaymentStatus.text = getString(R.string.payment_paid_month, getDisplayMonth())
            binding.tvNote.text = getString(R.string.payment_paid_note)
        } else {
            if (isCurrentMonth) {
                linearLayout.background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.gradient_orange
                )
                binding.tvPaymentStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pending, 0, 0, 0)
                binding.tvPaymentStatus.text = getString(R.string.payment_estimate_month, getDisplayMonth())
                binding.tvNote.text = getString(R.string.payment_estimate_note)
            } else {
                linearLayout.background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.gradient_red
                )
                binding.tvPaymentStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0)
                binding.tvPaymentStatus.text = getString(R.string.payment_unpaid_month, getDisplayMonth())
                binding.tvNote.text = getString(R.string.payment_unpaid_note)
            }
        }
    }

    private fun updateCalculationTitle(isCurrentMonth: Boolean) {
        if (_binding == null || !isAdded) return

        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val title = if (isCurrentMonth) {
            getString(R.string.payment_calculation_estimate, today)
        } else {
            getString(R.string.payment_calculation_detail, getDisplayMonth())
        }
        binding.tvCalculationTitle.text = title
    }

    private fun updatePaymentButton(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
        if (_binding == null || !isAdded) return

        when {
            isPaid -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = getString(R.string.payment_button_paid)
                binding.btnPayNow.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                )
            }
            isCurrentMonth -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = getString(R.string.payment_button_not_due)
                binding.btnPayNow.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                )
            }
            isPreviousMonth -> {
                binding.btnPayNow.isEnabled = true
                binding.btnPayNow.text = getString(R.string.payment_button_confirm)
                binding.btnPayNow.background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.button_gradient_background
                )
            }
            else -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = getString(R.string.payment_button_overdue)
                binding.btnPayNow.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                )
            }
        }
        
        hideLoadingState()
    }

    private fun updatePaymentNotice(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
        if (_binding == null || !isAdded) return

        when {
            isPaid -> {
                binding.cardPaymentNotice.visibility = View.GONE
            }
            isCurrentMonth -> {
                binding.cardPaymentNotice.visibility = View.VISIBLE
                binding.tvPaymentNoticeTitle.text = getString(R.string.payment_notice_title_info)
                binding.tvPaymentNoticeContent.text = getString(R.string.payment_notice_content_estimate)
            }
            isPreviousMonth -> {
                binding.cardPaymentNotice.visibility = View.VISIBLE
                binding.tvPaymentNoticeTitle.text = getString(R.string.payment_notice_title_need)
                binding.tvPaymentNoticeContent.text = getString(R.string.payment_notice_content_need)
            }
            else -> {
                binding.cardPaymentNotice.visibility = View.VISIBLE
                binding.tvPaymentNoticeTitle.text = getString(R.string.payment_notice_title_overdue)
                binding.tvPaymentNoticeContent.text = getString(R.string.payment_notice_content_overdue)
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
        if (_binding == null || !isAdded) return

        userRoomNumber?.let { roomNumber ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null || !isAdded) return

                    if (selectedMonth.isBlank() || !selectedMonth.matches(Regex("\\d{4}-\\d{2}"))) {
                        return
                    }

                    val prevMonth = Calendar.getInstance().apply {
                        val parts = selectedMonth.split("-")
                        if (parts.size < 2) {
                            return@apply
                        }
                        try {
                            set(Calendar.YEAR, parts[0].toInt())
                            set(Calendar.MONTH, parts[1].toInt() - 1)
                            add(Calendar.MONTH, -1)
                        } catch (e: NumberFormatException) {
                            return@apply
                        }
                    }
                    val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevMonth.time)
                    
                    val currentMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    val prevMonthData = mutableMapOf<String, Pair<Int?, Int?>>()
                    
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
                    
                    val usedElectric = calculateMonthlyConsumption(
                        currentMonthData, prevMonthData, true
                    )
                    
                    val usedWater = calculateMonthlyConsumption(
                        currentMonthData, prevMonthData, false
                    )

                    val electricCost = usedElectric * 3300
                    val waterCost = usedWater * 15000
                    totalCost = electricCost + waterCost

                    val electricCostFormatted = String.format(Locale.getDefault(), "%,d", electricCost)
                    val waterCostFormatted = String.format(Locale.getDefault(), "%,d", waterCost)
                    val totalCostFormatted = String.format(Locale.getDefault(), "%,d", totalCost)

                    binding.tvElectricDetail.text = getString(R.string.payment_electric_detail, usedElectric)
                    binding.tvElectricAmount.text = getString(R.string.payment_amount_format, electricCostFormatted)

                    binding.tvWaterDetail.text = getString(R.string.payment_water_detail, usedWater)
                    binding.tvWaterAmount.text = getString(R.string.payment_amount_format, waterCostFormatted)

                    binding.tvTotalAmount.text = getString(R.string.payment_amount_format, totalCostFormatted)
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
    
    private fun calculateMonthlyConsumption(
        currentMonthData: Map<String, Pair<Int?, Int?>>,
        prevMonthData: Map<String, Pair<Int?, Int?>>,
        isElectric: Boolean
    ): Int {
        val currentValues = currentMonthData.values
            .mapNotNull { if (isElectric) it.first else it.second }
            .sorted()
            
        val prevValues = prevMonthData.values
            .mapNotNull { if (isElectric) it.first else it.second }
            .sorted()
        
        if (currentValues.isEmpty()) {
            return 0
        }
        
        val currentMaxValue = currentValues.maxOrNull() ?: 0
        val prevMonthLastValue = prevValues.lastOrNull()
        
        val result = if (prevMonthLastValue != null) {
            currentMaxValue - prevMonthLastValue
        } else {
            val currentMinValue = currentValues.minOrNull() ?: 0
            currentMaxValue - currentMinValue
        }
        
        return maxOf(0, result)
    }

    private fun openPaymentLink() {
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

        val dataToSign = "amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl"
        val signature = hmacSha256(dataToSign)

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
                                @Suppress("DEPRECATION")
                                startActivityForResult(intent, paymentRequestCode)
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

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == paymentRequestCode) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    refreshPaymentStatus()
                    showSuccessAnimation()
                }
                Activity.RESULT_CANCELED -> {
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Thanh toán đã bị hủy", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Kết quả thanh toán không xác định", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showSuccessAnimation() {
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

    private fun hmacSha256(data: String): String {
        val hmac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(
            com.app.buildingmanagement.BuildConfig.SIGNATURE.toByteArray(Charsets.UTF_8),
            "HmacSHA256"
        )
        hmac.init(secretKeySpec)
        val hash = hmac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}