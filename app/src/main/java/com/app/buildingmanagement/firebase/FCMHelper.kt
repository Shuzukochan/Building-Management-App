package com.app.buildingmanagement.firebase

import com.google.firebase.messaging.FirebaseMessaging
import com.app.buildingmanagement.data.FirebaseDataState

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
            // Subscribe to building ID topic (không có prefix)
            val buildingId = FirebaseDataState.buildingId
            if (!buildingId.isNullOrEmpty()) {
                subscribeToTopic(buildingId)
            }

            if (roomNumber != null) {
                subscribeToTopic("room_$roomNumber")
                val floor = roomNumber.substring(0, 1)
                subscribeToTopic("floor_$floor")
            }
        }

        fun unsubscribeFromBuildingTopics(roomNumber: String?) {
            // Unsubscribe from building ID topic (không có prefix)
            val buildingId = FirebaseDataState.buildingId
            if (!buildingId.isNullOrEmpty()) {
                unsubscribeFromTopic(buildingId)
            }

            if (roomNumber != null) {
                unsubscribeFromTopic("room_$roomNumber")
                val floor = roomNumber.substring(0, 1)
                unsubscribeFromTopic("floor_$floor")
            }
        }
    }
}