package com.app.buildingmanagement.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * API Service for handling HTTP requests
 * 
 * Provides methods for making API calls to external services
 * and backend APIs for the building management system.
 */
class ApiService {
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(LoggingInterceptor())
            .addInterceptor(AuthInterceptor())
            .build()
    }
    
    companion object {
        private const val BASE_URL = "https://api.buildingmanagement.com/v1/"
        private const val PAYMENT_GATEWAY_URL = "https://payment.gateway.com/api/"
        
        // API Endpoints
        object Endpoints {
            const val USERS = "users"
            const val ROOMS = "rooms"
            const val PAYMENTS = "payments"
            const val STATISTICS = "statistics"
            const val NOTIFICATIONS = "notifications"
            const val MAINTENANCE = "maintenance"
            const val REPORTS = "reports"
            const val UPLOAD = "upload"
        }
        
        // Content Types
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
    }
    
    /**
     * Make a GET request
     */
    suspend fun get(endpoint: String, params: Map<String, String> = emptyMap()): ApiResponse {
        return withContext(Dispatchers.IO) {
            val urlBuilder = HttpUrl.parse("$BASE_URL$endpoint")?.newBuilder()
                ?: throw IllegalArgumentException("Invalid URL")
            
            params.forEach { (key, value) ->
                urlBuilder.addQueryParameter(key, value)
            }
            
            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build()
            
            executeRequest(request)
        }
    }
    
    /**
     * Make a POST request with JSON body
     */
    suspend fun post(endpoint: String, data: JSONObject): ApiResponse {
        return withContext(Dispatchers.IO) {
            val requestBody = data.toString().toRequestBody(JSON_MEDIA_TYPE)
            
            val request = Request.Builder()
                .url("$BASE_URL$endpoint")
                .post(requestBody)
                .build()
            
            executeRequest(request)
        }
    }
    
    /**
     * Make a PUT request with JSON body
     */
    suspend fun put(endpoint: String, data: JSONObject): ApiResponse {
        return withContext(Dispatchers.IO) {
            val requestBody = data.toString().toRequestBody(JSON_MEDIA_TYPE)
            
            val request = Request.Builder()
                .url("$BASE_URL$endpoint")
                .put(requestBody)
                .build()
            
            executeRequest(request)
        }
    }
    
    /**
     * Make a DELETE request
     */
    suspend fun delete(endpoint: String): ApiResponse {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL$endpoint")
                .delete()
                .build()
            
            executeRequest(request)
        }
    }
    
    /**
     * Upload file
     */
    suspend fun uploadFile(
        endpoint: String,
        filePath: String,
        fileName: String,
        mimeType: String,
        additionalData: Map<String, String> = emptyMap()
    ): ApiResponse {
        return withContext(Dispatchers.IO) {
            val file = java.io.File(filePath)
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
            
            // Add file
            val fileRequestBody = RequestBody.create(mimeType.toMediaType(), file)
            requestBodyBuilder.addFormDataPart("file", fileName, fileRequestBody)
            
            // Add additional data
            additionalData.forEach { (key, value) ->
                requestBodyBuilder.addFormDataPart(key, value)
            }
            
            val request = Request.Builder()
                .url("$BASE_URL$endpoint")
                .post(requestBodyBuilder.build())
                .build()
            
            executeRequest(request)
        }
    }
    
    /**
     * Process payment through payment gateway
     */
    suspend fun processPayment(paymentData: PaymentRequest): PaymentResponse {
        return withContext(Dispatchers.IO) {
            val jsonData = JSONObject().apply {
                put("amount", paymentData.amount)
                put("currency", paymentData.currency)
                put("payment_method", paymentData.paymentMethod)
                put("description", paymentData.description)
                put("customer_id", paymentData.customerId)
                put("order_id", paymentData.orderId)
            }
            
            val requestBody = jsonData.toString().toRequestBody(JSON_MEDIA_TYPE)
            
            val request = Request.Builder()
                .url("${PAYMENT_GATEWAY_URL}process")
                .post(requestBody)
                .build()
            
            val response = executeRequest(request)
            
            if (response.isSuccessful) {
                val responseData = response.data as JSONObject
                PaymentResponse(
                    success = true,
                    transactionId = responseData.optString("transaction_id"),
                    status = responseData.optString("status"),
                    message = responseData.optString("message")
                )
            } else {
                PaymentResponse(
                    success = false,
                    message = response.message
                )
            }
        }
    }
    
    /**
     * Get payment status
     */
    suspend fun getPaymentStatus(transactionId: String): PaymentStatusResponse {
        return withContext(Dispatchers.IO) {
            val response = get("${PAYMENT_GATEWAY_URL}status", mapOf("transaction_id" to transactionId))
            
            if (response.isSuccessful) {
                val responseData = response.data as JSONObject
                PaymentStatusResponse(
                    success = true,
                    transactionId = transactionId,
                    status = responseData.optString("status"),
                    amount = responseData.optDouble("amount"),
                    paidAt = responseData.optString("paid_at")
                )
            } else {
                PaymentStatusResponse(
                    success = false,
                    message = response.message
                )
            }
        }
    }
    
    /**
     * Send notification
     */
    suspend fun sendNotification(notificationData: NotificationRequest): ApiResponse {
        val jsonData = JSONObject().apply {
            put("title", notificationData.title)
            put("message", notificationData.message)
            put("type", notificationData.type)
            put("recipients", JSONArray(notificationData.recipients))
            put("data", JSONObject(notificationData.data))
        }
        
        return post(Endpoints.NOTIFICATIONS, jsonData)
    }
    
    /**
     * Generate report
     */
    suspend fun generateReport(reportRequest: ReportRequest): ApiResponse {
        val jsonData = JSONObject().apply {
            put("type", reportRequest.type)
            put("start_date", reportRequest.startDate)
            put("end_date", reportRequest.endDate)
            put("format", reportRequest.format)
            put("filters", JSONObject(reportRequest.filters))
        }
        
        return post(Endpoints.REPORTS, jsonData)
    }
    
    /**
     * Execute HTTP request
     */
    private suspend fun executeRequest(request: Request): ApiResponse {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            
            continuation.invokeOnCancellation {
                call.cancel()
            }
            
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
                
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string() ?: ""
                        val apiResponse = ApiResponse(
                            isSuccessful = response.isSuccessful,
                            code = response.code,
                            message = response.message,
                            data = parseResponseBody(responseBody),
                            rawBody = responseBody
                        )
                        continuation.resume(apiResponse)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
    
    /**
     * Parse response body to appropriate data type
     */
    private fun parseResponseBody(body: String): Any? {
        if (body.isEmpty()) return null
        
        return try {
            when {
                body.trim().startsWith("{") -> JSONObject(body)
                body.trim().startsWith("[") -> JSONArray(body)
                else -> body
            }
        } catch (e: Exception) {
            body
        }
    }
    
    /**
     * Logging Interceptor for debugging
     */
    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            
            // Log request
            println("API Request: ${request.method} ${request.url}")
            request.body?.let { body ->
                println("Request Body: $body")
            }
            
            val response = chain.proceed(request)
            
            // Log response
            println("API Response: ${response.code} ${response.message}")
            
            return response
        }
    }
    
    /**
     * Authentication Interceptor
     */
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            
            // Add authentication headers if needed
            val newRequest = originalRequest.newBuilder()
                .addHeader("User-Agent", "BuildingManagement-Android/1.0")
                .addHeader("Accept", "application/json")
                // Add auth token if available
                // .addHeader("Authorization", "Bearer $token")
                .build()
            
            return chain.proceed(newRequest)
        }
    }
}

/**
 * API Response data class
 */
data class ApiResponse(
    val isSuccessful: Boolean,
    val code: Int,
    val message: String,
    val data: Any? = null,
    val rawBody: String = ""
)

/**
 * Payment Request data class
 */
data class PaymentRequest(
    val amount: Double,
    val currency: String,
    val paymentMethod: String,
    val description: String,
    val customerId: String,
    val orderId: String
)

/**
 * Payment Response data class
 */
data class PaymentResponse(
    val success: Boolean,
    val transactionId: String? = null,
    val status: String? = null,
    val message: String? = null
)

/**
 * Payment Status Response data class
 */
data class PaymentStatusResponse(
    val success: Boolean,
    val transactionId: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val paidAt: String? = null,
    val message: String? = null
)

/**
 * Notification Request data class
 */
data class NotificationRequest(
    val title: String,
    val message: String,
    val type: String,
    val recipients: List<String>,
    val data: Map<String, String> = emptyMap()
)

/**
 * Report Request data class
 */
data class ReportRequest(
    val type: String,
    val startDate: String,
    val endDate: String,
    val format: String = "pdf",
    val filters: Map<String, String> = emptyMap()
)