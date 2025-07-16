package com.app.buildingmanagement.controllers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.app.buildingmanagement.config.FirebaseConfig
import java.util.Date
import java.util.UUID

/**
 * Notification Controller
 * 
 * Manages notification operations including push notifications,
 * in-app notifications, and notification history.
 */
class NotificationController {
    
    private val firebaseMessaging = FirebaseMessaging.getInstance()
    private val database = FirebaseConfig.database
    
    private val _notifications = MutableStateFlow<List<NotificationData>>(emptyList())
    val notifications: StateFlow<List<NotificationData>> = _notifications.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    
    /**
     * Initialize notification controller
     */
    suspend fun initialize(userId: String) {
        try {
            // Subscribe to user-specific topics
            subscribeToUserTopics(userId)
            
            // Load notification history
            loadNotifications(userId)
            
        } catch (e: Exception) {
            _error.value = "Lỗi khởi tạo thông báo: ${e.message}"
        }
    }
    
    /**
     * Send push notification to user
     */
    suspend fun sendNotificationToUser(
        userId: String,
        title: String,
        message: String,
        type: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            val notificationData = NotificationData(
                id = generateNotificationId(),
                userId = userId,
                title = title,
                message = message,
                type = type,
                data = data,
                timestamp = Date(),
                isRead = false
            )
            
            // Save to database first
            saveNotificationToDatabase(notificationData)
            
            // Send push notification
            sendPushNotification(userId, title, message, type, data)
            
            // Update local state
            loadNotifications(userId)
            
            true
        } catch (e: Exception) {
            _error.value = "Lỗi gửi thông báo: ${e.message}"
            false
        }
    }
    
    /**
     * Send notification to multiple users
     */
    suspend fun sendBroadcastNotification(
        userIds: List<String>,
        title: String,
        message: String,
        type: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            var successCount = 0
            
            userIds.forEach { userId ->
                val success = sendNotificationToUser(userId, title, message, type, data)
                if (success) successCount++
            }
            
            successCount > 0
        } catch (e: Exception) {
            _error.value = "Lỗi gửi thông báo hàng loạt: ${e.message}"
            false
        }
    }
    
    /**
     * Send maintenance request notification
     */
    suspend fun sendMaintenanceNotification(
        userId: String,
        roomId: String,
        issueDescription: String
    ): Boolean {
        val title = "Yêu cầu bảo trì"
        val message = "Yêu cầu bảo trì cho phòng $roomId: $issueDescription"
        val data = mapOf(
            "type" to "maintenance_request",
            "roomId" to roomId,
            "description" to issueDescription
        )
        
        return sendNotificationToUser(userId, title, message, "maintenance", data)
    }
    
    /**
     * Send payment reminder notification
     */
    suspend fun sendPaymentReminderNotification(
        userId: String,
        amount: Double,
        dueDate: Date,
        paymentId: String
    ): Boolean {
        val title = "Nhắc nhở thanh toán"
        val message = "Bạn có khoản thanh toán ${formatCurrency(amount)} đến hạn"
        val data = mapOf(
            "type" to "payment_reminder",
            "paymentId" to paymentId,
            "amount" to amount.toString(),
            "dueDate" to dueDate.time.toString()
        )
        
        return sendNotificationToUser(userId, title, message, "payment", data)
    }
    
    /**
     * Send announcement notification
     */
    suspend fun sendAnnouncementNotification(
        userIds: List<String>,
        title: String,
        message: String,
        priority: String = "normal"
    ): Boolean {
        val data = mapOf(
            "type" to "announcement",
            "priority" to priority
        )
        
        return sendBroadcastNotification(userIds, title, message, "announcement", data)
    }
    
    /**
     * Load notifications for user
     */
    suspend fun loadNotifications(userId: String) {
        try {
            _isLoading.value = true
            _error.value = null
            
            val notificationsList = mutableListOf<NotificationData>()
            
            database.reference
                .child(FirebaseConfig.DatabasePaths.NOTIFICATIONS)
                .child(userId)
                .orderByChild("timestamp")
                .get()
                .addOnSuccessListener { snapshot ->
                    snapshot.children.forEach { child ->
                        child.getValue(NotificationData::class.java)?.let {
                            notificationsList.add(it)
                        }
                    }
                    
                    _notifications.value = notificationsList.sortedByDescending { it.timestamp }
                    _unreadCount.value = notificationsList.count { !it.isRead }
                }
                .addOnFailureListener { e ->
                    _error.value = "Lỗi tải thông báo: ${e.message}"
                }
            
        } catch (e: Exception) {
            _error.value = "Lỗi tải thông báo: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Mark notification as read
     */
    suspend fun markAsRead(notificationId: String, userId: String): Boolean {
        return try {
            database.reference
                .child(FirebaseConfig.DatabasePaths.NOTIFICATIONS)
                .child(userId)
                .child(notificationId)
                .child("isRead")
                .setValue(true)
                .addOnSuccessListener {
                    // Update local state
                    val updatedNotifications = _notifications.value.map { notification ->
                        if (notification.id == notificationId) {
                            notification.copy(isRead = true)
                        } else {
                            notification
                        }
                    }
                    _notifications.value = updatedNotifications
                    _unreadCount.value = updatedNotifications.count { !it.isRead }
                }
            
            true
        } catch (e: Exception) {
            _error.value = "Lỗi đánh dấu đã đọc: ${e.message}"
            false
        }
    }
    
    /**
     * Mark all notifications as read
     */
    suspend fun markAllAsRead(userId: String): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>()
            
            _notifications.value.forEach { notification ->
                if (!notification.isRead) {
                    updates["${notification.id}/isRead"] = true
                }
            }
            
            database.reference
                .child(FirebaseConfig.DatabasePaths.NOTIFICATIONS)
                .child(userId)
                .updateChildren(updates)
                .addOnSuccessListener {
                    val updatedNotifications = _notifications.value.map { it.copy(isRead = true) }
                    _notifications.value = updatedNotifications
                    _unreadCount.value = 0
                }
            
            true
        } catch (e: Exception) {
            _error.value = "Lỗi đánh dấu tất cả đã đọc: ${e.message}"
            false
        }
    }
    
    /**
     * Delete notification
     */
    suspend fun deleteNotification(notificationId: String, userId: String): Boolean {
        return try {
            database.reference
                .child(FirebaseConfig.DatabasePaths.NOTIFICATIONS)
                .child(userId)
                .child(notificationId)
                .removeValue()
                .addOnSuccessListener {
                    val updatedNotifications = _notifications.value.filter { it.id != notificationId }
                    _notifications.value = updatedNotifications
                    _unreadCount.value = updatedNotifications.count { !it.isRead }
                }
            
            true
        } catch (e: Exception) {
            _error.value = "Lỗi xóa thông báo: ${e.message}"
            false
        }
    }
    
    /**
     * Subscribe to user-specific notification topics
     */
    private suspend fun subscribeToUserTopics(userId: String) {
        try {
            firebaseMessaging.subscribeToTopic("user_$userId")
            firebaseMessaging.subscribeToTopic("all_users")
        } catch (e: Exception) {
            // Silent failure for topic subscription
        }
    }
    
    /**
     * Send push notification via FCM
     */
    private suspend fun sendPushNotification(
        userId: String,
        title: String,
        message: String,
        type: String,
        data: Map<String, String>
    ) {
        // This would typically be done from a backend service
        // For now, we'll just save to database and rely on FCM service
    }
    
    /**
     * Save notification to database
     */
    private suspend fun saveNotificationToDatabase(notification: NotificationData) {
        database.reference
            .child(FirebaseConfig.DatabasePaths.NOTIFICATIONS)
            .child(notification.userId)
            .child(notification.id)
            .setValue(notification)
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
    
    private fun generateNotificationId(): String {
        return "NOTIF_${UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()}"
    }
    
    private fun formatCurrency(amount: Double): String {
        return "${String.format("%,.0f", amount)} VND"
    }
}

/**
 * Notification data model for internal use
 */
data class NotificationData(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "",
    val data: Map<String, String> = emptyMap(),
    val timestamp: Date = Date(),
    val isRead: Boolean = false
)