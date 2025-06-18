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
        fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String) // Thêm method mới
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
        val isFirstTimeCache = cachedRoomSnapshot == null

        cachedRoomSnapshot = roomSnapshot
        cachedUserRoomNumber = roomNumber
        cachedUserPhone = userPhone
        lastUpdateTime = System.currentTimeMillis()

        Log.d(TAG, "Cache updated for room: $roomNumber")

        // Notify all listeners
        listeners.forEach { listener ->
            try {
                if (isFirstTimeCache) {
                    // Đây là lần đầu có cache, gọi onCacheReady
                    listener.onCacheReady(roomSnapshot, roomNumber)
                } else {
                    // Đây là update data, gọi onDataUpdated
                    listener.onDataUpdated(roomSnapshot, roomNumber)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }

    fun addListener(listener: DataUpdateListener) {
        listeners.add(listener)
        Log.d(TAG, "Listener added, total: ${listeners.size}")

        // Nếu đã có cache, gọi ngay onCacheReady
        if (isCacheValid() && cachedRoomSnapshot != null && cachedUserRoomNumber != null) {
            try {
                listener.onCacheReady(cachedRoomSnapshot!!, cachedUserRoomNumber!!)
                Log.d(TAG, "Immediately provided cached data to new listener")
            } catch (e: Exception) {
                Log.e(TAG, "Error providing cached data to new listener", e)
            }
        }
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

    // Thêm method để force refresh cache
    fun refreshCache() {
        lastUpdateTime = 0
        Log.d(TAG, "Cache refresh requested")
    }
}