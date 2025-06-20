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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: ""
            val body = notification.body ?: ""

            if (title.isNotEmpty() || body.isNotEmpty()) {
                sendNotification(title, body)
            }
            return
        }

        if (remoteMessage.data.isNotEmpty()) {
            handleDataPayload(remoteMessage.data)
        }
    }

    /**
     * Xử lý data payload
     */
    private fun handleDataPayload(data: Map<String, String>) {
        val title = data["title"] ?: ""
        val body = data["body"] ?: ""

        if (title.isNotEmpty() || body.isNotEmpty()) {
            sendNotification(title, body)
        }
    }
    override fun onNewToken(token: String) {
        val sharedPref = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putString("fcm_token", token).apply()

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
                        }
                        break
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
            }
        })
    }
    private fun sendNotification(title: String, messageBody: String) {
        if (title.isEmpty() && messageBody.isEmpty()) {
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
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tạo notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Building Management Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}