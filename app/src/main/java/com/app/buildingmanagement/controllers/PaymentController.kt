package com.app.buildingmanagement.controllers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.app.buildingmanagement.models.Payment
import com.app.buildingmanagement.repositories.PaymentRepository
import com.app.buildingmanagement.utils.ValidationUtils
import java.util.Date
import java.util.UUID

/**
 * Payment Controller
 * 
 * Manages business logic for payment operations.
 * Handles payment creation, updates, processing, and state management.
 */
class PaymentController {
    
    private val paymentRepository = PaymentRepository()
    
    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    /**
     * Load all payments for a user
     */
    suspend fun loadPayments(userId: String) {
        try {
            _isLoading.value = true
            _error.value = null
            
            val userPayments = paymentRepository.getPaymentsByUser(userId)
            _payments.value = userPayments.sortedByDescending { it.dueDate }
            
        } catch (e: Exception) {
            _error.value = "Lỗi khi tải danh sách thanh toán: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Load all payments (admin view)
     */
    suspend fun loadAllPayments() {
        try {
            _isLoading.value = true
            _error.value = null
            
            val allPayments = paymentRepository.getAllPayments()
            _payments.value = allPayments.sortedByDescending { it.dueDate }
            
        } catch (e: Exception) {
            _error.value = "Lỗi khi tải danh sách thanh toán: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Create a new payment
     */
    suspend fun createPayment(
        userId: String,
        roomId: String,
        amount: Double,
        type: String,
        dueDate: Date,
        description: String? = null
    ): Boolean {
        return try {
            _isProcessing.value = true
            _error.value = null
            
            // Validate input
            if (!ValidationUtils.isValidAmount(amount)) {
                _error.value = "Số tiền không hợp lệ"
                return false
            }
            
            if (userId.isEmpty() || roomId.isEmpty()) {
                _error.value = "Thông tin người dùng hoặc phòng không hợp lệ"
                return false
            }
            
            val payment = Payment(
                id = generatePaymentId(),
                userId = userId,
                roomId = roomId,
                amount = amount,
                type = type,
                status = "pending",
                dueDate = dueDate,
                createdDate = Date(),
                description = description
            )
            
            val success = paymentRepository.createPayment(payment)
            
            if (success) {
                // Refresh payments list
                loadPayments(userId)
            } else {
                _error.value = "Không thể tạo thanh toán"
            }
            
            success
        } catch (e: Exception) {
            _error.value = "Lỗi khi tạo thanh toán: ${e.message}"
            false
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Process payment
     */
    suspend fun processPayment(paymentId: String, method: String): Boolean {
        return try {
            _isProcessing.value = true
            _error.value = null
            
            val success = paymentRepository.updatePaymentStatus(
                paymentId, 
                "paid", 
                Date(), 
                method
            )
            
            if (success) {
                // Refresh payments list
                refreshPayments()
            } else {
                _error.value = "Không thể xử lý thanh toán"
            }
            
            success
        } catch (e: Exception) {
            _error.value = "Lỗi khi xử lý thanh toán: ${e.message}"
            false
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Cancel payment
     */
    suspend fun cancelPayment(paymentId: String): Boolean {
        return try {
            _isProcessing.value = true
            _error.value = null
            
            val success = paymentRepository.updatePaymentStatus(paymentId, "cancelled")
            
            if (success) {
                refreshPayments()
            } else {
                _error.value = "Không thể hủy thanh toán"
            }
            
            success
        } catch (e: Exception) {
            _error.value = "Lỗi khi hủy thanh toán: ${e.message}"
            false
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Update payment details
     */
    suspend fun updatePayment(payment: Payment): Boolean {
        return try {
            _isProcessing.value = true
            _error.value = null
            
            // Validate input
            if (!ValidationUtils.isValidAmount(payment.amount)) {
                _error.value = "Số tiền không hợp lệ"
                return false
            }
            
            val success = paymentRepository.updatePayment(payment)
            
            if (success) {
                refreshPayments()
            } else {
                _error.value = "Không thể cập nhật thanh toán"
            }
            
            success
        } catch (e: Exception) {
            _error.value = "Lỗi khi cập nhật thanh toán: ${e.message}"
            false
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Get pending payments for a user
     */
    suspend fun getPendingPayments(userId: String): List<Payment> {
        return try {
            paymentRepository.getPendingPaymentsByUser(userId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get overdue payments for a user
     */
    suspend fun getOverduePayments(userId: String): List<Payment> {
        return try {
            paymentRepository.getOverduePaymentsByUser(userId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get payment history for a user
     */
    suspend fun getPaymentHistory(userId: String, limit: Int = 10): List<Payment> {
        return try {
            paymentRepository.getPaymentHistory(userId, limit)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Search payments by criteria
     */
    suspend fun searchPayments(
        query: String,
        status: String? = null,
        type: String? = null
    ): List<Payment> {
        return try {
            paymentRepository.searchPayments(query, status, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get payment by ID
     */
    suspend fun getPaymentById(paymentId: String): Payment? {
        return try {
            paymentRepository.getPaymentById(paymentId)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Refresh payments list
     */
    private suspend fun refreshPayments() {
        // This would need the current user context
        // For now, we'll reload all payments
        loadAllPayments()
    }
    
    /**
     * Generate unique payment ID
     */
    private fun generatePaymentId(): String {
        return "PAY_${UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()}"
    }
    
    /**
     * Validate payment data
     */
    fun validatePaymentData(
        amount: Double,
        type: String,
        dueDate: Date
    ): Pair<Boolean, String?> {
        if (!ValidationUtils.isValidAmount(amount)) {
            return Pair(false, "Số tiền không hợp lệ")
        }
        
        if (type.isEmpty()) {
            return Pair(false, "Loại thanh toán không được để trống")
        }
        
        if (dueDate.before(Date())) {
            return Pair(false, "Ngày đến hạn không thể là quá khứ")
        }
        
        return Pair(true, null)
    }
}