package com.app.buildingmanagement.firebase

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Lớp trợ giúp để quản lý Firebase Cloud Messaging (FCM)
 */
class FCMHelper {
    companion object {
        private const val TAG = "FCMHelper"

        /**
         * Lấy token của thiết bị hiện tại
         */
        fun getToken(callback: (String?) -> Unit) {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "Không thể lấy token FCM", task.exception)
                        callback(null)
                        return@OnCompleteListener
                    }

                    // Lấy token mới
                    val token = task.result
                    Log.d(TAG, "FCM Token: $token")
                    callback(token)
                })
        }

        /**
         * Đăng ký để nhận thông báo theo chủ đề
         */
        fun subscribeToTopic(topic: String) {
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Đăng ký chủ đề $topic thành công")
                    } else {
                        Log.e(TAG, "Đăng ký chủ đề $topic thất bại", task.exception)
                    }
                }
        }

        /**
         * Hủy đăng ký từ một chủ đề
         */
        fun unsubscribeFromTopic(topic: String) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Hủy đăng ký chủ đề $topic thành công")
                    } else {
                        Log.e(TAG, "Hủy đăng ký chủ đề $topic thất bại", task.exception)
                    }
                }
        }

        /**
         * Subscribe lại tất cả topics cho user (khi bật notification)
         */
        fun subscribeToUserBuildingTopics(roomNumber: String?) {
            Log.d(TAG, "=== STARTING SUBSCRIPTION PROCESS ===")
            
            // Subscribe topic chung
            subscribeToTopic("all_residents")

            if (roomNumber != null) {
                // Subscribe topic cho phòng cụ thể
                subscribeToTopic("room_$roomNumber")

                // Subscribe topic cho tầng
                val floor = roomNumber.substring(0, 1)
                subscribeToTopic("floor_$floor")

                Log.d(TAG, "Subscribed to all topics for room: $roomNumber")
                Log.d(TAG, "Topics: all_residents, room_$roomNumber, floor_$floor")
            } else {
                Log.d(TAG, "Subscribed to general topics only (no room number)")
            }
            
            Log.d(TAG, "=== SUBSCRIPTION PROCESS COMPLETED ===")
        }

        /**
         * Hủy đăng ký tất cả các topic được sử dụng trong ứng dụng Building Management
         * Gọi phương thức này khi người dùng đăng xuất hoặc tắt notification
         */
        fun unsubscribeFromBuildingTopics(roomNumber: String?) {
            // Hủy đăng ký topic "all_residents"
            unsubscribeFromTopic("all_residents")

            if (roomNumber != null) {
                // Hủy đăng ký topic cho phòng cụ thể
                unsubscribeFromTopic("room_$roomNumber")

                // Hủy đăng ký topic cho tầng
                val floor = roomNumber.substring(0, 1)
                unsubscribeFromTopic("floor_$floor")

                Log.d(TAG, "Hủy đăng ký tất cả các topic: all_residents, room_$roomNumber, floor_$floor")
            } else {
                Log.d(TAG, "Hủy đăng ký topic all_residents (không có số phòng)")
            }
        }

        /**
         * Kiểm tra setting và subscribe/unsubscribe topics theo đó
         */
        fun checkAndSubscribeBasedOnSettings(context: Context, roomNumber: String?) {
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)

            if (notificationsEnabled) {
                Log.d(TAG, "Notifications enabled, subscribing to topics...")
                subscribeToUserBuildingTopics(roomNumber)
            } else {
                Log.d(TAG, "Notifications disabled, unsubscribing from topics...")
                unsubscribeFromBuildingTopics(roomNumber)
            }
        }
    }
}