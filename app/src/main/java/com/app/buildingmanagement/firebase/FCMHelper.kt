package com.app.buildingmanagement.firebase


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
         * Đăng ký chủ đề theo tòa nhà/căn hộ của người dùng
         * Thường gọi sau khi đăng nhập thành công
         */
        fun subscribeToUserTopics(userId: String, buildingId: String, apartmentId: String) {
            // Đăng ký thông báo dành riêng cho người dùng
            subscribeToTopic("user_$userId")

            // Đăng ký thông báo cho tòa nhà
            subscribeToTopic("building_$buildingId")

            // Đăng ký thông báo cho căn hộ
            subscribeToTopic("apartment_$apartmentId")
        }

        /**
         * Hủy đăng ký tất cả các chủ đề khi người dùng đăng xuất
         */
        fun unsubscribeFromAllTopics(userId: String, buildingId: String, apartmentId: String) {
            unsubscribeFromTopic("user_$userId")
            unsubscribeFromTopic("building_$buildingId")
            unsubscribeFromTopic("apartment_$apartmentId")
        }
    }
}
