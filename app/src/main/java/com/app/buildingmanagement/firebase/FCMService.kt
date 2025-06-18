package com.app.buildingmanagement.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.app.buildingmanagement.MainActivity
import com.app.buildingmanagement.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FCMService : FirebaseMessagingService() {

    /**
     * Called when message is received.
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // KIỂM TRA SETTING NOTIFICATION CỦA USER TRƯỚC
        if (!isNotificationEnabled()) {
            Log.d(TAG, "Notifications disabled by user, skipping notification display")
            return
        }

        // Ưu tiên xử lý notification payload trước
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: ""
            val body = notification.body ?: ""

            Log.d(TAG, "Received notification: '$title' - '$body'")

            // CHỈ HIỂN THỊ NẾU CÓ NỘI DUNG TỪ SERVER
            if (title.isNotEmpty() || body.isNotEmpty()) {
                sendNotification(title, body)
            } else {
                Log.d(TAG, "Empty notification content, not displaying")
            }
            return // Return để không xử lý data payload nữa
        }

        // Chỉ xử lý data payload nếu không có notification payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataPayload(remoteMessage.data)
        }
    }

    /**
     * Kiểm tra xem user có bật notification hay không
     */
    private fun isNotificationEnabled(): Boolean {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val userPreference = sharedPref.getBoolean("notifications_enabled", true) // Default là true

        Log.d(TAG, "Notification user preference: $userPreference")
        return userPreference
    }

    /**
     * Xử lý data payload
     */
    private fun handleDataPayload(data: Map<String, String>) {
        val title = data["title"] ?: ""
        val body = data["body"] ?: ""

        Log.d(TAG, "Data payload - title: '$title', body: '$body'")

        // Chỉ hiển thị nếu có nội dung
        if (title.isNotEmpty() || body.isNotEmpty()) {
            sendNotification(title, body)
        } else {
            Log.d(TAG, "No meaningful content in data payload")
        }
    }

    /**
     * Called if the FCM registration token is updated
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Token refreshed: $token")

        // Lưu token mới vào SharedPreferences
        val sharedPref = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putString("fcm_token", token).apply()

        // Nếu user đã đăng nhập, cập nhật token trong database
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.phoneNumber != null) {
            updateTokenInDatabase(token, currentUser.phoneNumber!!)
        }
    }

    private fun updateTokenInDatabase(token: String, phone: String) {
        val database = FirebaseDatabase.getInstance()
        val roomsRef = database.getReference("rooms")

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (roomSnapshot in snapshot.children) {
                    val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                    if (phoneInRoom == phone) {
                        val roomNumber = roomSnapshot.key
                        if (roomNumber != null) {
                            roomsRef.child(roomNumber).child("FCM").child("token").setValue(token)
                            Log.d(TAG, "Token updated for room: $roomNumber")
                        }
                        break
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error updating token: ${error.message}")
            }
        })
    }

    /**
     * Create and show notification - CHỈ HIỂN THỊ KHI USER CHO PHÉP
     */
    private fun sendNotification(title: String, messageBody: String) {
        // Double check notification setting
        if (!isNotificationEnabled()) {
            Log.d(TAG, "Notification disabled by user during sendNotification, not showing")
            return
        }

        // Không hiển thị nếu không có nội dung
        if (title.isEmpty() && messageBody.isEmpty()) {
            Log.d(TAG, "Empty notification content, not showing")
            return
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fcm_default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.icon_hcmute_notification)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // THÊM DEFAULT SETTINGS

        // Chỉ set title nếu có
        if (title.isNotEmpty()) {
            notificationBuilder.setContentTitle(title)
        } else {
            notificationBuilder.setContentTitle("Test Notification") // FALLBACK TITLE
        }

        // Chỉ set body nếu có
        if (messageBody.isNotEmpty()) {
            notificationBuilder.setContentText(messageBody)
        } else {
            notificationBuilder.setContentText("Test Body") // FALLBACK BODY
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tạo notification channel với importance phù hợp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createOrUpdateNotificationChannel(notificationManager, channelId)

            // KIỂM TRA CHANNEL SAU KHI TẠO
            val channel = notificationManager.getNotificationChannel(channelId)
            if (channel == null) {
                Log.e(TAG, "Channel is null after creation!")
                return
            }

            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "Channel importance is NONE, notification might not show")
            }

            Log.d(TAG, "Channel status - importance: ${channel.importance}, blocked: ${channel.importance == NotificationManager.IMPORTANCE_NONE}")
        }

        // Sử dụng timestamp làm notification ID để tránh ghi đè
        val notificationId = System.currentTimeMillis().toInt()

        try {
            Log.d(TAG, "Attempting to show notification (ID: $notificationId): '$title' - '$messageBody'")
            notificationManager.notify(notificationId, notificationBuilder.build())
            Log.d(TAG, "Notification.notify() called successfully")

            // VERIFY NOTIFICATION HIỂN THỊ
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNotifications = notificationManager.activeNotifications
                Log.d(TAG, "Active notifications count: ${activeNotifications.size}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }

    /**
     * Tạo hoặc cập nhật notification channel dựa trên user setting
     */
    private fun createOrUpdateNotificationChannel(notificationManager: NotificationManager, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Kiểm tra setting để quyết định importance
            val userEnabled = isNotificationEnabled()
            val importance = if (userEnabled) {
                NotificationManager.IMPORTANCE_DEFAULT
            } else {
                NotificationManager.IMPORTANCE_NONE
            }

            val channel = NotificationChannel(
                channelId,
                "Building Management Notifications",
                importance
            ).apply {
                description = "Thông báo từ ban quản lý tòa nhà"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel updated - importance: $importance (user enabled: $userEnabled)")
        }
    }

    companion object {
        private const val TAG = "FCMService"
    }
}