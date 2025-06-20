package com.app.buildingmanagement.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot

object SharedDataManager {
    private const val TAG = "SharedDataManager"
    private const val CACHE_DURATION = 30000 // 30 giây

    private var cachedRoomSnapshot: DataSnapshot? = null
    private var cachedUserRoomNumber: String? = null
    private var cachedUserPhone: String? = null
    private var cachedUserUID: String? = null // THÊM UID để validation
    private var lastUpdateTime: Long = 0

    // Callback để notify các Fragment khi có dữ liệu mới
    private val listeners = mutableSetOf<DataUpdateListener>()

    interface DataUpdateListener {
        fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String)
        fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String) // Thêm method mới
    }

    fun getCachedRoomSnapshot(): DataSnapshot? {
        // KIỂM TRA USER HIỆN TẠI TRƯỚC KHI TRẢ CACHE
        if (!isCurrentUserValid()) {
            Log.w(TAG, "Current user changed, clearing cache")
            clearCache()
            return null
        }
        
        return if (System.currentTimeMillis() - lastUpdateTime < CACHE_DURATION) {
            Log.d(TAG, "Using cached room data")
            cachedRoomSnapshot
        } else {
            Log.d(TAG, "Cache expired, need fresh data")
            null
        }
    }

    fun getCachedRoomNumber(): String? {
        // KIỂM TRA USER HIỆN TẠI TRƯỚC KHI TRẢ CACHE
        if (!isCurrentUserValid()) {
            Log.w(TAG, "Current user changed, clearing cache")
            clearCache()
            return null
        }
        return cachedUserRoomNumber
    }


    // THÊM METHOD ĐỂ KIỂM TRA USER HIỆN TẠI
    private fun isCurrentUserValid(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        
        if (currentUser == null) {
            Log.d(TAG, "No current user, cache invalid")
            return false
        }
        
        if (cachedUserUID == null || cachedUserPhone == null) {
            Log.d(TAG, "No cached user data")
            return false
        }
        
        val isUIDMatch = currentUser.uid == cachedUserUID
        val isPhoneMatch = currentUser.phoneNumber == cachedUserPhone
        
        if (!isUIDMatch || !isPhoneMatch) {
            Log.w(TAG, "User mismatch - UID: $isUIDMatch, Phone: $isPhoneMatch")
            Log.w(TAG, "Current: UID=${currentUser.uid}, Phone=${currentUser.phoneNumber}")
            Log.w(TAG, "Cached: UID=$cachedUserUID, Phone=$cachedUserPhone")
            return false
        }
        
        return true
    }

    fun setCachedData(roomSnapshot: DataSnapshot, roomNumber: String, userPhone: String) {
        // VALIDATE CURRENT USER TRƯỚC KHI SET CACHE
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e(TAG, "Cannot cache data - no current user")
            return
        }
        
        if (currentUser.phoneNumber != userPhone) {
            Log.e(TAG, "Cannot cache data - phone mismatch")
            Log.e(TAG, "Current phone: ${currentUser.phoneNumber}, Provided phone: $userPhone")
            return
        }

        val isFirstTimeCache = cachedRoomSnapshot == null
        val wasUserDifferent = cachedUserUID != null && cachedUserUID != currentUser.uid

        // CLEAR CACHE NẾU USER KHÁC
        if (wasUserDifferent) {
            Log.w(TAG, "Different user detected, clearing old cache")
            clearCache()
        }

        cachedRoomSnapshot = roomSnapshot
        cachedUserRoomNumber = roomNumber
        cachedUserPhone = userPhone
        cachedUserUID = currentUser.uid // LƯU UID ĐỂ VALIDATION
        lastUpdateTime = System.currentTimeMillis()

        Log.d(TAG, "Cache updated for room: $roomNumber, user: ${currentUser.uid}")

        // Notify all listeners
        listeners.forEach { listener ->
            try {
                if (isFirstTimeCache || wasUserDifferent) {
                    // Đây là lần đầu có cache hoặc user khác, gọi onCacheReady
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

        // Nếu đã có cache VÀ USER HỢP LỆ, gọi ngay onCacheReady
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
        cachedUserUID = null // CLEAR UID
        lastUpdateTime = 0
        // KHÔNG CLEAR LISTENERS - chúng vẫn cần để nhận data mới
        Log.d(TAG, "Cache cleared")
    }

    private fun isCacheValid(): Boolean {
        return isCurrentUserValid() && 
               System.currentTimeMillis() - lastUpdateTime < CACHE_DURATION &&
               cachedRoomSnapshot != null &&
               cachedUserRoomNumber != null
    }
    // THÊM METHOD ĐỂ CHECK USER CHANGE
    fun checkAndClearIfUserChanged() {
        if (!isCurrentUserValid()) {
            Log.w(TAG, "User changed detected, clearing cache")
            clearCache()
        }
    }
}