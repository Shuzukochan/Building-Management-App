package com.app.buildingmanagement.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.buildingmanagement.models.Payment
import com.app.buildingmanagement.models.PaymentUrgency
import com.app.buildingmanagement.utils.DateUtils

/**
 * Payment Adapter for RecyclerView
 * 
 * Displays list of payments with details including amount, status,
 * due date, and payment type. Supports click events and actions.
 */
class PaymentAdapter(
    private val onPaymentClick: (Payment) -> Unit,
    private val onPaymentAction: (Payment, String) -> Unit = { _, _ -> }
) : ListAdapter<Payment, PaymentAdapter.PaymentViewHolder>(PaymentDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        // Simple implementation for demonstration
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return PaymentViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(android.R.id.text1)
        private val subtitleText: TextView = itemView.findViewById(android.R.id.text2)
        
        fun bind(payment: Payment) {
            titleText.text = "${payment.getTypeDisplayName()} - ${payment.getFormattedAmount()}"
            
            val subtitleBuilder = StringBuilder()
            subtitleBuilder.append("ID: ${payment.id}")
            subtitleBuilder.append(" • Trạng thái: ${payment.getStatusDisplayName()}")
            subtitleBuilder.append(" • Đến hạn: ${DateUtils.formatDate(payment.dueDate)}")
            
            if (payment.isPaid()) {
                payment.paidDate?.let { paidDate ->
                    subtitleBuilder.append(" • Đã thanh toán: ${DateUtils.formatDate(paidDate)}")
                }
            }
            
            subtitleText.text = subtitleBuilder.toString()
            
            itemView.setOnClickListener {
                onPaymentClick(payment)
            }
            
            // Set background color based on urgency
            val backgroundColor = when (payment.getUrgencyLevel()) {
                PaymentUrgency.PAID -> 0xFFE8F5E8.toInt() // Light green
                PaymentUrgency.OVERDUE -> 0xFFFFEBEE.toInt() // Light red
                PaymentUrgency.URGENT -> 0xFFFFF3E0.toInt() // Light orange
                PaymentUrgency.WARNING -> 0xFFFFFDE7.toInt() // Light yellow
                PaymentUrgency.NORMAL -> 0xFFFFFFFF.toInt() // White
            }
            
            itemView.setBackgroundColor(backgroundColor)
        }
    }
    
    class PaymentDiffCallback : DiffUtil.ItemCallback<Payment>() {
        override fun areItemsTheSame(oldItem: Payment, newItem: Payment): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Payment, newItem: Payment): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Payment Adapter for Compose
 */
class PaymentComposeAdapter {
    
    fun getPaymentItem(
        payment: Payment,
        onPaymentClick: (Payment) -> Unit,
        onPaymentAction: (Payment, String) -> Unit = { _, _ -> }
    ): PaymentItemData {
        return PaymentItemData(
            payment = payment,
            title = payment.getTypeDisplayName(),
            amount = payment.getFormattedAmount(),
            subtitle = buildPaymentSubtitle(payment),
            statusText = payment.getStatusDisplayName(),
            statusColor = getPaymentStatusColor(payment),
            urgencyLevel = payment.getUrgencyLevel(),
            dueDate = DateUtils.formatDate(payment.dueDate),
            isOverdue = payment.isOverdue(),
            isPaid = payment.isPaid(),
            canPay = payment.status == "pending",
            onPaymentClick = onPaymentClick,
            onPaymentAction = onPaymentAction
        )
    }
    
    private fun buildPaymentSubtitle(payment: Payment): String {
        val subtitleBuilder = StringBuilder()
        
        subtitleBuilder.append("Đến hạn: ${DateUtils.formatDate(payment.dueDate)}")
        
        if (payment.description?.isNotEmpty() == true) {
            subtitleBuilder.append(" • ${payment.description}")
        }
        
        if (payment.isPaid()) {
            payment.paidDate?.let { paidDate ->
                subtitleBuilder.append(" • Đã thanh toán: ${DateUtils.formatDate(paidDate)}")
            }
            payment.method?.let { method ->
                subtitleBuilder.append(" (${payment.getMethodDisplayName()})")
            }
        }
        
        return subtitleBuilder.toString()
    }
    
    private fun getPaymentStatusColor(payment: Payment): Long {
        return when (payment.getUrgencyLevel()) {
            PaymentUrgency.PAID -> 0xFF4CAF50 // Green
            PaymentUrgency.OVERDUE -> 0xFFF44336 // Red
            PaymentUrgency.URGENT -> 0xFFFF9800 // Orange
            PaymentUrgency.WARNING -> 0xFFFFEB3B // Yellow
            PaymentUrgency.NORMAL -> 0xFF9E9E9E // Gray
        }
    }
}

/**
 * Payment item data for Compose
 */
data class PaymentItemData(
    val payment: Payment,
    val title: String,
    val amount: String,
    val subtitle: String,
    val statusText: String,
    val statusColor: Long,
    val urgencyLevel: PaymentUrgency,
    val dueDate: String,
    val isOverdue: Boolean,
    val isPaid: Boolean,
    val canPay: Boolean,
    val onPaymentClick: (Payment) -> Unit,
    val onPaymentAction: (Payment, String) -> Unit
)

/**
 * Payment filter options
 */
data class PaymentFilter(
    val status: String? = null,
    val type: String? = null,
    val userId: String? = null,
    val roomId: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val startDate: java.util.Date? = null,
    val endDate: java.util.Date? = null,
    val method: String? = null,
    val urgencyLevels: List<PaymentUrgency> = emptyList()
) {
    fun matches(payment: Payment): Boolean {
        if (status != null && payment.status != status) return false
        if (type != null && payment.type != type) return false
        if (userId != null && payment.userId != userId) return false
        if (roomId != null && payment.roomId != roomId) return false
        if (minAmount != null && payment.getFinalAmount() < minAmount) return false
        if (maxAmount != null && payment.getFinalAmount() > maxAmount) return false
        if (startDate != null && payment.createdDate.before(startDate)) return false
        if (endDate != null && payment.createdDate.after(endDate)) return false
        if (method != null && payment.method != method) return false
        if (urgencyLevels.isNotEmpty() && payment.getUrgencyLevel() !in urgencyLevels) return false
        
        return true
    }
}

/**
 * Payment sorting options
 */
enum class PaymentSortOption(val displayName: String) {
    DUE_DATE_ASCENDING("Đến hạn: Sớm nhất"),
    DUE_DATE_DESCENDING("Đến hạn: Muộn nhất"),
    AMOUNT_LOW_TO_HIGH("Số tiền: Thấp đến cao"),
    AMOUNT_HIGH_TO_LOW("Số tiền: Cao đến thấp"),
    CREATED_DATE_NEWEST("Ngày tạo: Mới nhất"),
    CREATED_DATE_OLDEST("Ngày tạo: Cũ nhất"),
    STATUS("Trạng thái"),
    TYPE("Loại thanh toán"),
    URGENCY("Mức độ khẩn cấp")
}

/**
 * Payment sorting utility
 */
object PaymentSorter {
    fun sortPayments(payments: List<Payment>, sortOption: PaymentSortOption): List<Payment> {
        return when (sortOption) {
            PaymentSortOption.DUE_DATE_ASCENDING -> payments.sortedBy { it.dueDate }
            PaymentSortOption.DUE_DATE_DESCENDING -> payments.sortedByDescending { it.dueDate }
            PaymentSortOption.AMOUNT_LOW_TO_HIGH -> payments.sortedBy { it.getFinalAmount() }
            PaymentSortOption.AMOUNT_HIGH_TO_LOW -> payments.sortedByDescending { it.getFinalAmount() }
            PaymentSortOption.CREATED_DATE_NEWEST -> payments.sortedByDescending { it.createdDate }
            PaymentSortOption.CREATED_DATE_OLDEST -> payments.sortedBy { it.createdDate }
            PaymentSortOption.STATUS -> payments.sortedBy { it.status }
            PaymentSortOption.TYPE -> payments.sortedBy { it.type }
            PaymentSortOption.URGENCY -> payments.sortedBy { 
                when (it.getUrgencyLevel()) {
                    PaymentUrgency.OVERDUE -> 0
                    PaymentUrgency.URGENT -> 1
                    PaymentUrgency.WARNING -> 2
                    PaymentUrgency.NORMAL -> 3
                    PaymentUrgency.PAID -> 4
                }
            }
        }
    }
}

/**
 * Payment summary data for dashboard
 */
data class PaymentSummary(
    val totalPayments: Int,
    val totalAmount: Double,
    val paidPayments: Int,
    val paidAmount: Double,
    val pendingPayments: Int,
    val pendingAmount: Double,
    val overduePayments: Int,
    val overdueAmount: Double
) {
    fun getPaidPercentage(): Double {
        return if (totalPayments > 0) (paidPayments.toDouble() / totalPayments) * 100 else 0.0
    }
    
    fun getCollectionRate(): Double {
        return if (totalAmount > 0) (paidAmount / totalAmount) * 100 else 0.0
    }
}

/**
 * Payment analytics data
 */
data class PaymentAnalytics(
    val averagePaymentAmount: Double,
    val paymentsByType: Map<String, Int>,
    val paymentsByStatus: Map<String, Int>,
    val paymentsByMethod: Map<String, Int>,
    val monthlyTrend: List<Pair<String, Double>>,
    val overdueRate: Double,
    val collectionEfficiency: Double
)