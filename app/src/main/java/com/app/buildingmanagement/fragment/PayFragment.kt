package com.app.buildingmanagement.fragment
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
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

    private var totalCost: Int = 0  // giữ lại để dùng khi tạo QR
    private var accountName = "NGUYEN BA UY"

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

        loadUsageData()  // hiển thị thông tin ngay khi vào fragment

        binding.btnPayNow.setOnClickListener {
            openPaymentLink()
        }
    }

    private fun loadUsageData() {
        val phone = auth.currentUser?.phoneNumber ?: return

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var startElectric: Int? = null
                var endElectric: Int? = null
                var startWater: Int? = null
                var endWater: Int? = null

                for (roomSnapshot in snapshot.children) {
                    val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                    if (phoneInRoom == phone) {
                        val nodes = roomSnapshot.child("nodes")
                        for (node in nodes.children) {
                            val history = node.child("history")
                            val monthDates = history.children
                                .mapNotNull { it.key }
                                .filter { it.startsWith(currentMonth) }
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
                        break
                    }
                }

                val usedElectric = if (startElectric != null && endElectric != null)
                    endElectric - startElectric else 0

                val usedWater = if (startWater != null && endWater != null)
                    endWater - startWater else 0

                val electricCost = usedElectric * 3300
                val waterCost = usedWater * 15000
                totalCost = electricCost + waterCost

                binding.tvElectricDetail.text =
                    "Tiêu thụ điện: $usedElectric × 3300đ = $electricCost đ"

                binding.tvWaterDetail.text =
                    "Tiêu thụ nước: $usedWater × 15000đ = $waterCost đ"

                binding.tvTotalAmount.text = "Tổng cộng: $totalCost đ"
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun openPaymentLink() {
        val orderCode = System.currentTimeMillis().toInt() // Hoặc mã đơn hàng cố định
        val amount = totalCost
        val description = "Thanh toan thang ${SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())}"
        val cancelUrl = "myapp://payment-cancel"
        val returnUrl = "myapp://payment-success"

        // ✅ Tính signature
        val dataToSign = "amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl"
        val signature = hmacSha256(dataToSign, com.app.buildingmanagement.BuildConfig.SIGNATURE)

        // ✅ JSON body
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

        // ✅ OkHttp request
        val request = Request.Builder()
            .url("https://api-merchant.payos.vn/v2/payment-requests")
            .post(requestBody)
            .addHeader("x-client-id", com.app.buildingmanagement.BuildConfig.CLIENT_ID)     // <- THAY bằng giá trị thực
            .addHeader("x-api-key", com.app.buildingmanagement.BuildConfig.API_KEY)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PAYOS_DEBUG", "Failed to create payment link: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Log.e("PAYOS_DEBUG", "HTTP Error: ${response.code}")
                    return
                }

                val jsonResponse = JSONObject(body)
                val checkoutUrl = jsonResponse
                    .optJSONObject("data")
                    ?.optString("checkoutUrl", "")
                    ?: ""


                if (checkoutUrl.isNotEmpty()) {
                    Log.d("PAYOS_DEBUG", "Payment URL: $checkoutUrl")

                    val intent = Intent(requireContext(), WebPayActivity::class.java)
                    intent.putExtra("url", checkoutUrl)
                    startActivity(intent)

                } else {
                    Log.e("PAYOS_DEBUG", "checkoutUrl is missing in response")
                }
            }
        })
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
