package com.app.buildingmanagement.data

import android.util.Log
import com.google.firebase.database.DataSnapshot

object SharedDataManager {
    private const val TAG = "SharedDataManager"
    private const val CACHE_DURATION = 30000 // 30 giây

    private var cachedRoomSnapshot: DataSnapshot? = null
    private var cachedUserRoomNumber: String? = null
    private var cachedUserPhone: String? = null
    private var lastUpdateTime: Long = 0

    // Callback để notify các Fragment khi có dữ liệu mới
    private val listeners = mutableSetOf<DataUpdateListener>()

    interface DataUpdateListener {
        fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String)
    }

    fun getCachedRoomSnapshot(): DataSnapshot? {
        return if (System.currentTimeMillis() - lastUpdateTime < CACHE_DURATION) {
            Log.d(TAG, "Using cached room data")
            cachedRoomSnapshot
        } else {
            Log.d(TAG, "Cache expired, need fresh data")
            null
        }
    }

    fun getCachedRoomNumber(): String? = cachedUserRoomNumber

    fun getCachedUserPhone(): String? = cachedUserPhone

    fun setCachedData(roomSnapshot: DataSnapshot, roomNumber: String, userPhone: String) {
        cachedRoomSnapshot = roomSnapshot
        cachedUserRoomNumber = roomNumber
        cachedUserPhone = userPhone
        lastUpdateTime = System.currentTimeMillis()

        Log.d(TAG, "Cache updated for room: $roomNumber")

        // Notify all listeners
        listeners.forEach { listener ->
            try {
                listener.onDataUpdated(roomSnapshot, roomNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }

    fun addListener(listener: DataUpdateListener) {
        listeners.add(listener)
        Log.d(TAG, "Listener added, total: ${listeners.size}")
    }

    fun removeListener(listener: DataUpdateListener) {
        listeners.remove(listener)
        Log.d(TAG, "Listener removed, total: ${listeners.size}")
    }

    fun clearCache() {
        cachedRoomSnapshot = null
        cachedUserRoomNumber = null
        cachedUserPhone = null
        lastUpdateTime = 0
        listeners.clear()
        Log.d(TAG, "Cache cleared")
    }

    fun isCacheValid(): Boolean {
        return System.currentTimeMillis() - lastUpdateTime < CACHE_DURATION &&
                cachedRoomSnapshot != null &&
                cachedUserRoomNumber != null
    }
}