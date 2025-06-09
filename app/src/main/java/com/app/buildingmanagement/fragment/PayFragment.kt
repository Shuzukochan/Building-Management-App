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

    companion object {
        private const val PAYMENT_REQUEST_CODE = 1001
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

        findUserRoom()

        binding.btnPayNow.setOnClickListener {
            if (binding.btnPayNow.isEnabled) {
                openPaymentLink()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPaymentStatus()
    }

    private fun findUserRoom() {
        val phone = auth.currentUser?.phoneNumber ?: return

        roomsRef.orderByChild("phone").equalTo(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val roomSnapshot = snapshot.children.first()
                        userRoomNumber = roomSnapshot.key

                        setupPaymentStatusListener()
                        setupMonthSpinner()
                        checkPaymentStatus()
                    } else {
                        Toast.makeText(requireContext(), "Không tìm thấy phòng của bạn", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupMonthSpinner() {
        val calendar = Calendar.getInstance()
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        currentMonth = monthKeyFormat.format(calendar.time)

        loadAvailableMonths { availableMonths ->
            if (availableMonths.isNotEmpty()) {
                setupSpinnerWithAvailableMonths(availableMonths.sorted())
            }  else {
                setupSpinnerWithCurrentMonth()
            }
        }
    }

    private fun loadAvailableMonths(callback: (List<String>) -> Unit) {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("nodes")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val availableMonths = mutableSetOf<String>()

                        for (node in snapshot.children) {
                            val history = node.child("history")
                            for (dateSnapshot in history.children) {
                                val dateKey = dateSnapshot.key ?: continue
                                if (dateKey.length >= 7) {
                                    val monthKey = dateKey.substring(0, 7)
                                    availableMonths.add(monthKey)
                                }
                            }
                        }

                        val sortedMonths = availableMonths.sorted()
                        callback(sortedMonths)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(emptyList())
                    }
                })
        } ?: callback(emptyList())
    }


    private fun setupSpinnerWithAvailableMonths(availableMonths: List<String>) {
        val monthFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
        val months = mutableListOf<String>()
        val monthKeys = mutableListOf<String>()

        for (monthKey in availableMonths) {
            val calendar = Calendar.getInstance()
            val parts = monthKey.split("-")
            if (parts.size == 2) {
                val year = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                if (year != null && month != null && month >= 1 && month <= 12) {
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month - 1)

                    monthKeys.add(monthKey)
                    months.add(monthFormat.format(calendar.time))
                }
            }
        }

        if (monthKeys.isNotEmpty()) {
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

            // Logic mới: Kiểm tra thanh toán tháng trước để quyết định hiển thị tháng nào
            determineDefaultMonth(monthKeys) { defaultMonthIndex ->
                binding.spnMonthPicker.setSelection(defaultMonthIndex)
            }
        } else {
            setupSpinnerWithCurrentMonth()
        }
    }

    private fun determineDefaultMonth(monthKeys: List<String>, callback: (Int) -> Unit) {
        val calendar = Calendar.getInstance()
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        // Tháng hiện tại
        val currentMonthKey = monthKeyFormat.format(calendar.time)

        // Tháng trước
        calendar.add(Calendar.MONTH, -1)
        val previousMonthKey = monthKeyFormat.format(calendar.time)

        // Tìm index của tháng hiện tại và tháng trước trong danh sách
        val currentMonthIndex = monthKeys.indexOf(currentMonthKey)
        val previousMonthIndex = monthKeys.indexOf(previousMonthKey)

        // Nếu không tìm thấy tháng trước trong danh sách, hiển thị tháng mới nhất
        if (previousMonthIndex == -1) {
            callback(monthKeys.size - 1)
            return
        }

        // Kiểm tra trạng thái thanh toán tháng trước
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("payments").child(previousMonthKey)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val isPreviousMonthPaid = snapshot.exists() &&
                                snapshot.child("status").getValue(String::class.java) == "PAID"

                        when {
                            // Nếu tháng trước đã thanh toán và có tháng hiện tại trong danh sách
                            isPreviousMonthPaid && currentMonthIndex != -1 -> {
                                callback(currentMonthIndex)
                            }
                            // Nếu tháng trước chưa thanh toán
                            !isPreviousMonthPaid -> {
                                callback(previousMonthIndex)
                            }
                            // Mặc định hiển thị tháng mới nhất
                            else -> {
                                callback(monthKeys.size - 1)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Nếu có lỗi, hiển thị tháng mới nhất
                        callback(monthKeys.size - 1)
                    }
                })
        } ?: callback(monthKeys.size - 1)
    }

    private fun setupSpinnerWithCurrentMonth() {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        val months = listOf(monthFormat.format(calendar.time))
        val monthKeys = listOf(monthKeyFormat.format(calendar.time))

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

        // Luôn chọn tháng hiện tại nếu chỉ có một tháng
        selectedMonth = monthKeyFormat.format(calendar.time)
        currentMonth = selectedMonth
    }

    private fun setupPaymentStatusListener() {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("payments")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        updateUIBasedOnMonth()
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun refreshPaymentStatus() {
        if (userRoomNumber != null) {
            checkPaymentStatus()
            loadUsageData()
            updateUIBasedOnMonth()
        }
    }

    private fun checkPaymentStatus() {
        userRoomNumber?.let { roomNumber ->
            val calendar = Calendar.getInstance()
            val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            calendar.add(Calendar.MONTH, -1)
            previousMonth = monthKeyFormat.format(calendar.time)

            checkMonthPaymentAmount(previousMonth) { previousMonthAmount ->
                if (previousMonthAmount == 0) {
                    createZeroPaymentRecord(previousMonth)
                } else {
                    roomsRef.child(roomNumber).child("payments").child(previousMonth)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val isPreviousMonthPaid = snapshot.exists() &&
                                        snapshot.child("status").getValue(String::class.java) == "PAID"
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
        }
    }

    private fun checkMonthPaymentAmount(month: String, callback: (Int) -> Unit) {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("nodes")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var totalAmount = 0

                        for (node in snapshot.children) {
                            val history = node.child("history")
                            val monthDates = history.children
                                .mapNotNull { it.key }
                                .filter { it.startsWith(month) }
                                .sorted()

                            if (monthDates.isNotEmpty()) {
                                val firstDay = monthDates.first()
                                val lastDay = monthDates.last()

                                val firstSnapshot = history.child(firstDay)
                                val lastSnapshot = history.child(lastDay)

                                val firstElectric = firstSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                                val lastElectric = lastSnapshot.child("electric").getValue(Long::class.java)?.toInt() ?: 0
                                val firstWater = firstSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0
                                val lastWater = lastSnapshot.child("water").getValue(Long::class.java)?.toInt() ?: 0

                                val usedElectric = maxOf(0, lastElectric - firstElectric)
                                val usedWater = maxOf(0, lastWater - firstWater)

                                val electricCost = usedElectric * 3300
                                val waterCost = usedWater * 15000
                                totalAmount = electricCost + waterCost
                            }
                        }

                        callback(totalAmount)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(0)
                    }
                })
        } ?: callback(0)
    }

    private fun createZeroPaymentRecord(month: String) {
        userRoomNumber?.let { roomNumber ->
            val phone = auth.currentUser?.phoneNumber ?: return

            roomsRef.child(roomNumber).child("payments").child(month)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            val paymentData = mapOf(
                                "status" to "PAID",
                                "orderCode" to "AUTO_ZERO_${System.currentTimeMillis()}",
                                "paymentLinkId" to "",
                                "paymentDate" to System.currentTimeMillis(),
                                "amount" to 0,
                                "paidBy" to phone,
                                "roomNumber" to roomNumber,
                                "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                                "note" to "Tự động đánh dấu đã thanh toán do số tiền = 0đ"
                            )

                            roomsRef.child(roomNumber).child("payments").child(month)
                                .setValue(paymentData)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun updateUIBasedOnMonth() {
        val isCurrentMonth = selectedMonth == currentMonth
        val isPreviousMonth = selectedMonth == previousMonth

        checkSelectedMonthPaymentStatus { isPaid ->
            updatePaymentStatusCard(isPaid, isCurrentMonth)
            updateCalculationTitle(isCurrentMonth)
            updatePaymentButton(isPaid, isCurrentMonth, isPreviousMonth)
            updatePaymentNotice(isPaid, isCurrentMonth, isPreviousMonth)
        }
    }

    private fun checkSelectedMonthPaymentStatus(callback: (Boolean) -> Unit) {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("payments").child(selectedMonth)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val exists = snapshot.exists()
                        val status = snapshot.child("status").getValue(String::class.java)
                        val isPaid = exists && status == "PAID"

                        callback(isPaid)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(false)
                    }
                })
        } ?: callback(false)
    }

    private fun updatePaymentStatusCard(isPaid: Boolean, isCurrentMonth: Boolean) {
        val linearLayout = binding.cardPaymentStatus.getChildAt(0) as LinearLayout

        if (isPaid && totalCost == 0 && !isCurrentMonth) {
            linearLayout.background = ContextCompat.getDrawable(
                requireContext(), R.drawable.gradient_green
            )
            binding.ivPaymentStatusIcon.setImageResource(R.drawable.ic_check_circle)
            binding.tvPaymentStatus.text = "Không phát sinh chi phí tháng ${getDisplayMonth()}"
            binding.tvNote.text = "Tháng này không có chi phí điện nước phát sinh."

        } else if (isPaid) {
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
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val title = if (isCurrentMonth) {
            "Tạm tính đến ngày: $today"
        } else {
            "Chi tiết tháng ${getDisplayMonth()}"
        }
        binding.tvCalculationTitle.text = title
    }

    private fun updatePaymentButton(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
        when {
            isPaid -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = if (totalCost == 0 && !isCurrentMonth) {
                    "✅ Không phát sinh chi phí"
                } else {
                    "✅ Đã thanh toán"
                }
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
                if (totalCost == 0) {
                    binding.btnPayNow.isEnabled = false
                    binding.btnPayNow.text = "✅ Không phát sinh chi phí"
                    binding.btnPayNow.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                    )

                    createZeroPaymentRecord(selectedMonth)

                } else {
                    binding.btnPayNow.isEnabled = true
                    binding.btnPayNow.text = "💳 Xác nhận thanh toán"
                    binding.btnPayNow.background = ContextCompat.getDrawable(
                        requireContext(), R.drawable.button_gradient_background
                    )
                }
            }
            else -> {
                binding.btnPayNow.isEnabled = false
                binding.btnPayNow.text = "❌ Quá hạn thanh toán"
                binding.btnPayNow.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_disabled)
                )
            }
        }
    }

    private fun updatePaymentNotice(isPaid: Boolean, isCurrentMonth: Boolean, isPreviousMonth: Boolean) {
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
        return try {
            if (selectedMonth.isBlank()) {
                val calendar = Calendar.getInstance()
                SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
            } else {
                val parts = selectedMonth.split("-")
                if (parts.size != 2) {
                    val calendar = Calendar.getInstance()
                    SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
                } else {
                    val year = parts[0].toIntOrNull()
                    val month = parts[1].toIntOrNull()

                    if (year == null || month == null || month < 1 || month > 12) {
                        val calendar = Calendar.getInstance()
                        SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
                    } else {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.YEAR, year)
                        calendar.set(Calendar.MONTH, month - 1)
                        SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
                    }
                }
            }
        } catch (e: Exception) {
            val calendar = Calendar.getInstance()
            SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
        }
    }

    private fun loadUsageData() {
        userRoomNumber?.let { roomNumber ->
            roomsRef.child(roomNumber).child("nodes")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var startElectric: Int? = null
                        var endElectric: Int? = null
                        var startWater: Int? = null
                        var endWater: Int? = null

                        for (node in snapshot.children) {
                            val history = node.child("history")
                            val monthDates = history.children
                                .mapNotNull { it.key }
                                .filter { it.startsWith(selectedMonth) }
                                .sorted()

                            if (monthDates.isNotEmpty()) {
                                val firstDay = monthDates.first()
                                val lastDay = monthDates.last()

                                val firstSnapshot = history.child(firstDay)
                                val lastSnapshot = history.child(lastDay)

                                val firstElectric = firstSnapshot.child("electric").getValue(Long::class.java)?.toInt()
                                val lastElectric = lastSnapshot.child("electric").getValue(Long::class.java)?.toInt()
                                val firstWater = firstSnapshot.child("water").getValue(Long::class.java)?.toInt()
                                val lastWater = lastSnapshot.child("water").getValue(Long::class.java)?.toInt()

                                if (firstElectric != null && lastElectric != null) {
                                    startElectric = firstElectric
                                    endElectric = lastElectric
                                }

                                if (firstWater != null && lastWater != null) {
                                    startWater = firstWater
                                    endWater = lastWater
                                }
                            }
                        }

                        val usedElectric = if (startElectric != null && endElectric != null)
                            endElectric - startElectric else 0

                        val usedWater = if (startWater != null && endWater != null)
                            endWater - startWater else 0

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
                        Toast.makeText(requireContext(), "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun openPaymentLink() {
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
        val description = "P$userRoomNumber T$monthShort/25"

        val cancelUrl = "myapp://payment-cancel"
        val returnUrl = "myapp://payment-success"

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
                    Toast.makeText(requireContext(), "Thanh toán thành công! 🎉", Toast.LENGTH_LONG).show()
                    refreshPaymentStatus()
                    showSuccessAnimation()
                }
                Activity.RESULT_CANCELED -> {
                    Toast.makeText(requireContext(), "Thanh toán đã bị hủy", Toast.LENGTH_SHORT).show()
                }
                else -> {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
