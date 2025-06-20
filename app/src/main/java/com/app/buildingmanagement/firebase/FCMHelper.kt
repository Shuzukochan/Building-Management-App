package com.app.buildingmanagement.firebase

import com.google.firebase.messaging.FirebaseMessaging

class FCMHelper {
    companion object {
        private fun subscribeToTopic(topic: String) {
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener { _ ->
                    // Handle completion silently
                }
        }

        private fun unsubscribeFromTopic(topic: String) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { _ ->
                    // Handle completion silently
                }
        }

        fun subscribeToUserBuildingTopics(roomNumber: String?) {
            subscribeToTopic("all_residents")

            if (roomNumber != null) {
                subscribeToTopic("room_$roomNumber")
                val floor = roomNumber.substring(0, 1)
                subscribeToTopic("floor_$floor")
            }
        }

        fun unsubscribeFromBuildingTopics(roomNumber: String?) {
            unsubscribeFromTopic("all_residents")

            if (roomNumber != null) {
                unsubscribeFromTopic("room_$roomNumber")
                val floor = roomNumber.substring(0, 1)
                unsubscribeFromTopic("floor_$floor")
            }
        }
    }
}