package com.app.buildingmanagement.fragment

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.buildingmanagement.BuildConfig
import com.app.buildingmanagement.WebPayActivity
import com.app.buildingmanagement.data.FirebaseDataState
import com.app.buildingmanagement.fragment.ui.home.responsiveDimension
import com.app.buildingmanagement.fragment.ui.pay.*
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private fun hmacSha256(data: String): String {
    try {
        val hmac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(BuildConfig.SIGNATURE.toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmac.init(secretKeySpec)
        val hash = hmac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        return ""
        }
    }

    @Composable
fun PaymentScreen() {
    val context = LocalContext.current
    val database = remember { FirebaseDatabase.getInstance() }

    // --- STATE VARIABLES ---
    var totalCost by remember { mutableIntStateOf(0) }
    var selectedMonth by remember { mutableStateOf("") }
    var electricUsage by remember { mutableDoubleStateOf(0.0) }
    var waterUsage by remember { mutableDoubleStateOf(0.0) }
    var electricCost by remember { mutableIntStateOf(0) }
    var waterCost by remember { mutableIntStateOf(0) }

    var isLoading by remember { mutableStateOf(true) }
    var paymentStatus by remember { mutableStateOf(PaymentStatus.LOADING) }
    var noteText by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("Thanh toán ngay") }
    var buttonEnabled by remember { mutableStateOf(false) }
    var noticeVisible by remember { mutableStateOf(false) }
    var noticeTitle by remember { mutableStateOf("") }
    var noticeContent by remember { mutableStateOf("") }

    var monthKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var monthOptions by remember { mutableStateOf<List<String>>(emptyList()) }

    // --- LAMBDAS FOR LOGIC ---

    val getDisplayMonth: (String) -> String = { monthKey ->
        if (monthKey.isNotEmpty()) {
            val index = monthKeys.indexOf(monthKey)
            if (index != -1 && index < monthOptions.size) monthOptions[index] else monthKey
        } else "..."
    }

    val updateUiBasedOnStatus = { isPaid: Boolean, forMonth: String ->
        val currentMonth = FirebaseDataState.getCurrentMonth()
        val isCurrent = forMonth == currentMonth

        paymentStatus = when {
            totalCost == 0 && electricUsage == 0.0 && waterUsage == 0.0 -> PaymentStatus.NO_NEED
            isPaid -> PaymentStatus.PAID
            isCurrent -> PaymentStatus.PENDING
            else -> PaymentStatus.UNPAID
        }

        when (paymentStatus) {
            PaymentStatus.PAID -> {
                noteText = "Thanh toán đã hoàn tất"
                buttonEnabled = false
                buttonText = "Đã thanh toán"
                noticeVisible = false
            }
            PaymentStatus.PENDING -> {
                noteText = "Đây là tạm tính cho tháng hiện tại"
                buttonEnabled = false
                buttonText = "Chưa đến hạn"
                noticeVisible = true
                noticeTitle = "Tạm tính"
                noticeContent = "Hóa đơn tháng hiện tại chỉ mang tính ước tính và sẽ được chốt vào cuối tháng."
            }
            PaymentStatus.UNPAID -> {
                noteText = "Hóa đơn này chưa được thanh toán."
                buttonEnabled = true
                buttonText = "Thanh toán ngay"
                noticeVisible = true
                noticeTitle = "Cần thanh toán"
                noticeContent = "Vui lòng thanh toán hóa đơn tháng ${getDisplayMonth(forMonth)} để tránh gián đoạn dịch vụ."
            }
            PaymentStatus.NO_NEED -> {
                 noteText = "Tháng này chưa có dữ liệu tiêu thụ."
                 buttonEnabled = false
                 buttonText = "Không cần thanh toán"
                 noticeVisible = false
            }
            else -> {}
        }
    }

    val checkPaymentStatus = { month: String, callback: (Boolean) -> Unit ->
        val buildingId = FirebaseDataState.getCurrentBuildingId()
        val roomId = FirebaseDataState.getCurrentRoomId()

        if (buildingId == null || roomId == null || month.isEmpty()) {
            callback(false)
        } else {
            database.getReference("buildings/$buildingId/rooms/$roomId/payments/$month").get()
                .addOnSuccessListener { snapshot ->
                    callback(snapshot.exists() && snapshot.child("status").getValue(String::class.java) == "PAID")
                }.addOnFailureListener { callback(false) }
        }
    }

    val loadUsageData = { month: String ->
        isLoading = true
        FirebaseDataState.getMonthlyConsumption(month) { electric, water ->
            electricUsage = electric
            waterUsage = water
            val (electricPrice, waterPrice) = FirebaseDataState.getBuildingPrices()
            electricCost = (electricUsage * electricPrice).toInt()
            waterCost = (waterUsage * waterPrice).toInt()
            totalCost = electricCost + waterCost
            checkPaymentStatus(month) { isPaid ->
                updateUiBasedOnStatus(isPaid, month)
                isLoading = false
            }
        }
    }

    val paymentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Thanh toán thành công!", Toast.LENGTH_SHORT).show()
            FirebaseDataState.refreshPaymentStatus()
            checkPaymentStatus(selectedMonth) { isPaid ->
                updateUiBasedOnStatus(isPaid, selectedMonth)
            }
        } else {
            Toast.makeText(context, "Thanh toán đã bị hủy.", Toast.LENGTH_SHORT).show()
        }
    }

    val openPaymentLink = {
        val buildingId = FirebaseDataState.getCurrentBuildingId()
        val roomId = FirebaseDataState.getCurrentRoomId()
        if (roomId == null || buildingId == null) {
            Toast.makeText(context, "Chưa xác định được phòng của bạn", Toast.LENGTH_SHORT).show()
        } else if (totalCost <= 0) {
            Toast.makeText(context, "Số tiền thanh toán không hợp lệ", Toast.LENGTH_SHORT).show()
        } else {
        val orderCode = (System.currentTimeMillis() / 1000).toInt()
            val description = "Thanh toan P$roomId T${selectedMonth.substring(5, 7)}"
            val dataToSign = "amount=$totalCost&cancelUrl=myapp://payment-cancel&description=$description&orderCode=$orderCode&returnUrl=myapp://payment-success"

        val json = JSONObject().apply {
            put("orderCode", orderCode)
                put("amount", totalCost)
            put("description", description)
                put("cancelUrl", "myapp://payment-cancel")
                put("returnUrl", "myapp://payment-success")
                put("signature", hmacSha256(dataToSign))
            }
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://api-merchant.payos.vn/v2/payment-requests")
            .post(requestBody)
                .addHeader("x-client-id", BuildConfig.CLIENT_ID)
                .addHeader("x-api-key", BuildConfig.API_KEY)
            .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread { Toast.makeText(context, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                    (context as? Activity)?.runOnUiThread {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                            Toast.makeText(context, "Lỗi API: ${response.code}", Toast.LENGTH_SHORT).show()
                        } else {
                try {
                    val jsonResponse = JSONObject(body)
                                if (jsonResponse.optString("code") != "00") {
                                    Toast.makeText(context, "Lỗi PayOS: ${jsonResponse.optString("desc")}", Toast.LENGTH_SHORT).show()
                                } else {
                                    val checkoutUrl = jsonResponse.optJSONObject("data")?.optString("checkoutUrl")
                                    if (checkoutUrl.isNullOrEmpty()) {
                                        Toast.makeText(context, "Không thể lấy link thanh toán", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val intent = Intent(context, WebPayActivity::class.java).apply {
                                            putExtra("url", checkoutUrl)
                                            putExtra("month", selectedMonth)
                                            putExtra("buildingId", buildingId)
                                            putExtra("roomId", roomId)
                                            putExtra("roomNumber", roomId)
                                            putExtra("amount", totalCost)
                                        }
                                        paymentLauncher.launch(intent)
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi xử lý response: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
        }
    }

    // --- SIDE EFFECTS ---
    LaunchedEffect(FirebaseDataState.isPaymentDataLoaded, FirebaseDataState.suggestedPaymentMonth) {
        if (!FirebaseDataState.isPaymentDataLoaded) return@LaunchedEffect

        FirebaseDataState.getHistoryData { electricMap, _ ->
            val rawMonths = electricMap.keys.map { it.substring(0, 7) }.toSet().sorted()
            monthKeys = rawMonths
            monthOptions = rawMonths.map {
                val parts = it.split("-")
                "${parts[1]}/${parts[0]}"
            }

            val defaultMonth = FirebaseDataState.suggestedPaymentMonth.takeIf { it.isNotEmpty() && rawMonths.contains(it) } ?: rawMonths.lastOrNull() ?: ""
            
            if (selectedMonth != defaultMonth) {
                 selectedMonth = defaultMonth
            }
            
            if (defaultMonth.isNotEmpty()) {
                loadUsageData(defaultMonth)
            } else {
                isLoading = false
            }
        }
    }
    
    // --- UI ---
    val dimen = responsiveDimension()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(dimen.mainPadding)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (monthKeys.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa có dữ liệu để thanh toán.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(dimen.cardMarginBottom)) {
                item {
                    PayHeader(
                        selectedMonth = selectedMonth,
                        monthOptions = monthOptions,
                        monthKeys = monthKeys,
                        titleTextSize = dimen.titleTextSize,
                        bodyTextSize = dimen.subtitleTextSize,
                        onMonthSelected = { month ->
                            if (selectedMonth != month) {
                                selectedMonth = month
                                loadUsageData(month)
                            }
                        }
                    )
                }
                item {
                    PaymentStatusCard(
                        status = paymentStatus,
                        displayMonth = getDisplayMonth(selectedMonth),
                        note = noteText
                    )
                }
                item {
                    UsageDetailCard(
                        isCurrentMonth = selectedMonth == FirebaseDataState.getCurrentMonth(),
                        displayMonth = getDisplayMonth(selectedMonth),
                        electricUsage = electricUsage,
                        waterUsage = waterUsage,
                        electricCost = electricCost,
                        waterCost = waterCost,
                        totalCost = totalCost
                    )
                }
                item {
                    PaymentNoticeCard(
                        title = noticeTitle,
                        content = noticeContent,
                        isVisible = noticeVisible
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    PaymentButton(
                        text = buttonText,
                        enabled = buttonEnabled,
                        onClick = openPaymentLink
                    )
                }
            }
        }
    }
}