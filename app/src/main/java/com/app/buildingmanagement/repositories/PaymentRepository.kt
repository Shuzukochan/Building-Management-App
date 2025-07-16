package com.app.buildingmanagement.repositories

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.app.buildingmanagement.config.FirebaseConfig
import com.app.buildingmanagement.models.Payment
import com.app.buildingmanagement.models.PaymentStatus
import java.util.Date
import java.util.Calendar

/**
 * Payment Repository
 * 
 * Handles data access operations for Payment entities.
 * Implements Repository pattern for payment management operations.
 */
class PaymentRepository {
    
    private val database = FirebaseConfig.database
    private val paymentsRef = database.reference.child(FirebaseConfig.DatabasePaths.PAYMENTS)
    
    /**
     * Get all payments
     */
    suspend fun getAllPayments(): List<Payment> {
        return try {
            val snapshot = paymentsRef.get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Payment::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get payments flow for real-time updates
     */
    fun getPaymentsFlow(): Flow<List<Payment>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val payments = snapshot.children.mapNotNull { child ->
                    child.getValue(Payment::class.java)
                }
                trySend(payments)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        paymentsRef.addValueEventListener(listener)
        
        awaitClose {
            paymentsRef.removeEventListener(listener)
        }
    }
    
    /**
     * Get payment by ID
     */
    suspend fun getPaymentById(paymentId: String): Payment? {
        return try {
            val snapshot = paymentsRef.child(paymentId).get().await()
            snapshot.getValue(Payment::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get payments by user
     */
    suspend fun getPaymentsByUser(userId: String): List<Payment> {
        return try {
            val snapshot = paymentsRef.orderByChild("userId").equalTo(userId).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Payment::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get payments by room
     */
    suspend fun getPaymentsByRoom(roomId: String): List<Payment> {
        return try {
            val snapshot = paymentsRef.orderByChild("roomId").equalTo(roomId).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Payment::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get payments by status
     */
    suspend fun getPaymentsByStatus(status: String): List<Payment> {
        return try {
            val snapshot = paymentsRef.orderByChild("status").equalTo(status).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Payment::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get pending payments by user
     */
    suspend fun getPendingPaymentsByUser(userId: String): List<Payment> {
        return getPaymentsByUser(userId).filter { it.status == PaymentStatus.PENDING }
    }
    
    /**
     * Get overdue payments by user
     */
    suspend fun getOverduePaymentsByUser(userId: String): List<Payment> {
        return getPaymentsByUser(userId).filter { it.isOverdue() }
    }
    
    /**
     * Get payment history for user
     */
    suspend fun getPaymentHistory(userId: String, limit: Int = 10): List<Payment> {
        return getPaymentsByUser(userId)
            .filter { it.status == PaymentStatus.PAID }
            .sortedByDescending { it.paidDate }
            .take(limit)
    }
    
    /**
     * Create new payment
     */
    suspend fun createPayment(payment: Payment): Boolean {
        return try {
            paymentsRef.child(payment.id).setValue(payment).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update payment
     */
    suspend fun updatePayment(payment: Payment): Boolean {
        return try {
            paymentsRef.child(payment.id).setValue(payment).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update payment status
     */
    suspend fun updatePaymentStatus(
        paymentId: String, 
        status: String,
        paidDate: Date? = null,
        method: String? = null,
        transactionId: String? = null
    ): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status
            )
            
            if (status == PaymentStatus.PAID) {
                paidDate?.let { updates["paidDate"] = it.time }
                method?.let { updates["method"] = it }
                transactionId?.let { updates["transactionId"] = it }
            }
            
            paymentsRef.child(paymentId).updateChildren(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete payment
     */
    suspend fun deletePayment(paymentId: String): Boolean {
        return try {
            paymentsRef.child(paymentId).removeValue().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Search payments
     */
    suspend fun searchPayments(
        query: String,
        status: String? = null,
        type: String? = null,
        userId: String? = null,
        startDate: Date? = null,
        endDate: Date? = null
    ): List<Payment> {
        return try {
            val allPayments = if (userId != null) {
                getPaymentsByUser(userId)
            } else {
                getAllPayments()
            }
            
            allPayments.filter { payment ->
                val matchesQuery = query.isEmpty() ||
                    payment.id.contains(query, ignoreCase = true) ||
                    payment.description?.contains(query, ignoreCase = true) == true ||
                    payment.notes?.contains(query, ignoreCase = true) == true
                
                val matchesStatus = status == null || payment.status == status
                val matchesType = type == null || payment.type == type
                
                val matchesDateRange = (startDate == null || !payment.createdDate.before(startDate)) &&
                    (endDate == null || !payment.createdDate.after(endDate))
                
                matchesQuery && matchesStatus && matchesType && matchesDateRange
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get payments for date range
     */
    suspend fun getPaymentsForDateRange(startDate: Date, endDate: Date): List<Payment> {
        return try {
            getAllPayments().filter { payment ->
                !payment.createdDate.before(startDate) && !payment.createdDate.after(endDate)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get monthly revenue
     */
    suspend fun getMonthlyRevenue(month: Int, year: Int = Calendar.getInstance().get(Calendar.YEAR)): Double {
        return try {
            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, 1, 0, 0, 0)
            val startDate = calendar.time
            
            calendar.add(Calendar.MONTH, 1)
            val endDate = calendar.time
            
            getPaymentsForDateRange(startDate, endDate)
                .filter { it.status == PaymentStatus.PAID }
                .sumOf { it.getFinalAmount() }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Get daily revenue for month
     */
    suspend fun getDailyRevenueForMonth(month: Int, year: Int): List<Pair<Int, Double>> {
        return try {
            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, 1)
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            val dailyRevenue = mutableListOf<Pair<Int, Double>>()
            
            for (day in 1..daysInMonth) {
                calendar.set(year, month - 1, day, 0, 0, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val endDate = calendar.time
                
                val dayRevenue = getPaymentsForDateRange(startDate, endDate)
                    .filter { it.status == PaymentStatus.PAID }
                    .sumOf { it.getFinalAmount() }
                
                dailyRevenue.add(Pair(day, dayRevenue))
                
                calendar.add(Calendar.DAY_OF_MONTH, -1) // Reset for next iteration
            }
            
            dailyRevenue
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Calculate total outstanding amount
     */
    suspend fun getTotalOutstandingAmount(): Double {
        return try {
            getPaymentsByStatus(PaymentStatus.PENDING)
                .sumOf { it.getFinalAmount() }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Calculate total overdue amount
     */
    suspend fun getTotalOverdueAmount(): Double {
        return try {
            getAllPayments()
                .filter { it.isOverdue() }
                .sumOf { it.getFinalAmount() }
        } catch (e: Exception) {
            0.0
        }
    }
    
    // Statistics methods
    
    /**
     * Get total payments count
     */
    suspend fun getTotalPaymentsCount(): Int {
        return try {
            val snapshot = paymentsRef.get().await()
            snapshot.childrenCount.toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get paid payments count
     */
    suspend fun getPaidPaymentsCount(): Int {
        return getPaymentsByStatus(PaymentStatus.PAID).size
    }
    
    /**
     * Get pending payments count
     */
    suspend fun getPendingPaymentsCount(): Int {
        return getPaymentsByStatus(PaymentStatus.PENDING).size
    }
    
    /**
     * Get overdue payments count
     */
    suspend fun getOverduePaymentsCount(): Int {
        return getAllPayments().count { it.isOverdue() }
    }
    
    /**
     * Get payment statistics by type
     */
    suspend fun getPaymentStatsByType(): Map<String, Double> {
        return try {
            val paidPayments = getPaymentsByStatus(PaymentStatus.PAID)
            paidPayments.groupBy { it.type }
                .mapValues { (_, payments) ->
                    payments.sumOf { it.getFinalAmount() }
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Get average payment amount
     */
    suspend fun getAveragePaymentAmount(): Double {
        return try {
            val paidPayments = getPaymentsByStatus(PaymentStatus.PAID)
            if (paidPayments.isNotEmpty()) {
                paidPayments.map { it.getFinalAmount() }.average()
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Get payment collection rate
     */
    suspend fun getPaymentCollectionRate(): Double {
        return try {
            val allPayments = getAllPayments()
            val paidPayments = allPayments.filter { it.status == PaymentStatus.PAID }
            
            if (allPayments.isNotEmpty()) {
                (paidPayments.size.toDouble() / allPayments.size) * 100
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Get payments requiring attention (overdue or due soon)
     */
    suspend fun getPaymentsRequiringAttention(): List<Payment> {
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 3) // Due in next 3 days
            val threeDaysFromNow = calendar.time
            
            getAllPayments().filter { payment ->
                payment.status == PaymentStatus.PENDING &&
                (payment.isOverdue() || payment.dueDate.before(threeDaysFromNow))
            }.sortedBy { it.dueDate }
        } catch (e: Exception) {
            emptyList()
        }
    }
}