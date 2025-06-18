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

        // Kiểm tra setting notification của user trước
        if (!isNotificationEnabled()) {
            Log.d(TAG, "Notifications disabled by user, skipping notification display")
            return
        }

        // CHỈ XỬ LÝ notification payload - không tự tạo thông báo
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: ""
            val body = notification.body ?: ""

            // CHỈ HIỂN THỊ NẾU CÓ TITLE HOẶC BODY TỪ SERVER
            if (title.isNotEmpty() || body.isNotEmpty()) {
                Log.d(TAG, "Showing notification from server: $title - $body")
                sendNotification(title, body)
            } else {
                Log.d(TAG, "Empty notification from server, not showing")
            }
            return
        }

        // XỬ LÝ data payload CHỈ KHI KHÔNG CÓ notification payload
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
        val userPreference = sharedPref.getBoolean("notifications_enabled", true)

        Log.d(TAG, "Notification user preference: $userPreference")
        return userPreference
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
     * Xử lý data payload CHỈ KHI có title và body
     */
    private fun handleDataPayload(data: Map<String, String>) {
        val title = data["title"] ?: ""
        val body = data["body"] ?: ""

        // CHỈ HIỂN THỊ NẾU CÓ TITLE HOẶC BODY TỪ DATA
        if (title.isNotEmpty() || body.isNotEmpty()) {
            Log.d(TAG, "Showing notification from data payload: $title - $body")
            sendNotification(title, body)
        } else {
            Log.d(TAG, "No title/body in data payload, not showing notification")
        }
    }

    /**
     * Create and show notification - CHỈ VỚI CONTENT TỪ SERVER
     */
    private fun sendNotification(title: String, messageBody: String) {
        // Double check notification setting
        if (!isNotificationEnabled()) {
            Log.d(TAG, "Notification disabled by user, not showing notification")
            return
        }

        // KHÔNG HIỂN THỊ NẾU KHÔNG CÓ CONTENT
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

        // CHỈ SET TITLE NẾU CÓ
        if (title.isNotEmpty()) {
            notificationBuilder.setContentTitle(title)
        }

        // CHỈ SET BODY NẾU CÓ
        if (messageBody.isNotEmpty()) {
            notificationBuilder.setContentText(messageBody)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)

            val importance = if (notificationsEnabled) {
                NotificationManager.IMPORTANCE_DEFAULT
            } else {
                NotificationManager.IMPORTANCE_NONE
            }

            val channel = NotificationChannel(
                channelId,
                "Building Management Notifications",
                importance
            )
            channel.description = "Thông báo từ ban quản lý tòa nhà"
            notificationManager.createNotificationChannel(channel)
        }

        Log.d(TAG, "Showing notification: '$title' - '$messageBody'")
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    companion object {
        private const val TAG = "FCMService"
    }
}