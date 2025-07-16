package com.app.buildingmanagement.models

import java.util.Date

/**
 * Payment data model
 * 
 * Represents a payment transaction in the building management system.
 * Handles rent payments, utility bills, deposits, and other fees.
 */
data class Payment(
    val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val amount: Double = 0.0,
    val type: String = "", // rent, utilities, deposit, maintenance, penalty
    val status: String = "pending", // pending, paid, cancelled, overdue, refunded
    val dueDate: Date = Date(),
    val paidDate: Date? = null,
    val createdDate: Date = Date(),
    val method: String? = null, // cash, bank_transfer, credit_card, e_wallet
    val transactionId: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val lateFee: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val currency: String = "VND",
    val paymentDetails: PaymentDetails = PaymentDetails(),
    val refundInfo: RefundInfo? = null
) {
    /**
     * Calculate final amount after late fees, discounts, and taxes
     */
    fun getFinalAmount(): Double {
        return amount + lateFee + tax - discount
    }
    
    /**
     * Check if payment is overdue
     */
    fun isOverdue(): Boolean {
        return status == "pending" && Date().after(dueDate)
    }
    
    /**
     * Check if payment is paid
     */
    fun isPaid(): Boolean {
        return status == "paid" && paidDate != null
    }
    
    /**
     * Get days until due date (negative if overdue)
     */
    fun getDaysUntilDue(): Int {
        val now = Date()
        val diffInMillis = dueDate.time - now.time
        return (diffInMillis / (24 * 60 * 60 * 1000)).toInt()
    }
    
    /**
     * Get payment status display name
     */
    fun getStatusDisplayName(): String {
        return when (status) {
            "pending" -> if (isOverdue()) "Quá hạn" else "Chờ thanh toán"
            "paid" -> "Đã thanh toán"
            "cancelled" -> "Đã hủy"
            "overdue" -> "Quá hạn"
            "refunded" -> "Đã hoàn tiền"
            else -> "Không xác định"
        }
    }
    
    /**
     * Get payment type display name
     */
    fun getTypeDisplayName(): String {
        return when (type) {
            "rent" -> "Tiền thuê"
            "utilities" -> "Tiện ích"
            "deposit" -> "Tiền cọc"
            "maintenance" -> "Bảo trì"
            "penalty" -> "Phí phạt"
            "parking" -> "Chỗ đậu xe"
            "internet" -> "Internet"
            else -> type.capitalize()
        }
    }
    
    /**
     * Get payment method display name
     */
    fun getMethodDisplayName(): String {
        return when (method) {
            "cash" -> "Tiền mặt"
            "bank_transfer" -> "Chuyển khoản"
            "credit_card" -> "Thẻ tín dụng"
            "e_wallet" -> "Ví điện tử"
            else -> method ?: "Chưa xác định"
        }
    }
    
    /**
     * Format amount with currency
     */
    fun getFormattedAmount(): String {
        return "${String.format("%,.0f", getFinalAmount())} $currency"
    }
    
    /**
     * Get payment urgency level
     */
    fun getUrgencyLevel(): PaymentUrgency {
        return when {
            status == "paid" -> PaymentUrgency.PAID
            isOverdue() -> PaymentUrgency.OVERDUE
            getDaysUntilDue() <= 3 -> PaymentUrgency.URGENT
            getDaysUntilDue() <= 7 -> PaymentUrgency.WARNING
            else -> PaymentUrgency.NORMAL
        }
    }
}

/**
 * Payment details for different payment types
 */
data class PaymentDetails(
    val period: String? = null, // "2024-01" for monthly payments
    val meterReadings: MeterReadings? = null, // for utility payments
    val penaltyReason: String? = null, // for penalty payments
    val maintenanceType: String? = null, // for maintenance payments
    val additionalCharges: Map<String, Double> = emptyMap(),
    val breakdown: Map<String, Double> = emptyMap() // detailed cost breakdown
) {
    /**
     * Get total additional charges
     */
    fun getTotalAdditionalCharges(): Double {
        return additionalCharges.values.sum()
    }
    
    /**
     * Get formatted breakdown
     */
    fun getFormattedBreakdown(): List<Pair<String, String>> {
        return breakdown.map { (key, value) ->
            Pair(key, String.format("%,.0f VND", value))
        }
    }
}

/**
 * Meter readings for utility payments
 */
data class MeterReadings(
    val electricityPrevious: Double = 0.0,
    val electricityCurrent: Double = 0.0,
    val waterPrevious: Double = 0.0,
    val waterCurrent: Double = 0.0,
    val gasePrevious: Double? = null,
    val gasCurrent: Double? = null,
    val readingDate: Date = Date(),
    val readBy: String = ""
) {
    /**
     * Calculate electricity usage
     */
    fun getElectricityUsage(): Double {
        return electricityCurrent - electricityPrevious
    }
    
    /**
     * Calculate water usage
     */
    fun getWaterUsage(): Double {
        return waterCurrent - waterPrevious
    }
    
    /**
     * Calculate gas usage
     */
    fun getGasUsage(): Double? {
        return if (gasePrevious != null && gasCurrent != null) {
            gasCurrent - gasePrevious
        } else null
    }
}

/**
 * Refund information
 */
data class RefundInfo(
    val refundId: String = "",
    val refundAmount: Double = 0.0,
    val refundDate: Date = Date(),
    val refundMethod: String = "",
    val reason: String = "",
    val processedBy: String = "",
    val refundTransactionId: String? = null
)

/**
 * Payment urgency levels
 */
enum class PaymentUrgency {
    PAID,
    OVERDUE,
    URGENT,
    WARNING,
    NORMAL
}

/**
 * Payment status constants
 */
object PaymentStatus {
    const val PENDING = "pending"
    const val PAID = "paid"
    const val CANCELLED = "cancelled"
    const val OVERDUE = "overdue"
    const val REFUNDED = "refunded"
}

/**
 * Payment type constants
 */
object PaymentType {
    const val RENT = "rent"
    const val UTILITIES = "utilities"
    const val DEPOSIT = "deposit"
    const val MAINTENANCE = "maintenance"
    const val PENALTY = "penalty"
    const val PARKING = "parking"
    const val INTERNET = "internet"
}

/**
 * Payment method constants
 */
object PaymentMethod {
    const val CASH = "cash"
    const val BANK_TRANSFER = "bank_transfer"
    const val CREDIT_CARD = "credit_card"
    const val E_WALLET = "e_wallet"
}