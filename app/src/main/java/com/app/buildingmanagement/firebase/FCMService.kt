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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class FCMService : FirebaseMessagingService() {

    /**
     * Called when message is received.
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleNow(remoteMessage.data)
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "Thông báo", it.body ?: "Bạn có thông báo mới")
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
                            // Chỉ cập nhật token
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
     * Handle time allotted to BroadcastReceivers.
     */
    private fun handleNow(data: Map<String, String>) {
        // Process data payload here
        // You can extract specific data and handle it accordingly
    }

    /**
     * Persist token to third-party servers.
     */
    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement this method to send token to your app server.
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     */
    private fun sendNotification(title: String, messageBody: String) {
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
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Building Management Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "FCMService"
    }
}
