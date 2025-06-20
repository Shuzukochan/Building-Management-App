package com.app.buildingmanagement.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot

object SharedDataManager {
    private const val CACHE_DURATION = 30000

    private var cachedRoomSnapshot: DataSnapshot? = null
    private var cachedUserRoomNumber: String? = null
    private var cachedUserPhone: String? = null
    private var cachedUserUID: String? = null
    private var lastUpdateTime: Long = 0

    private val listeners = mutableSetOf<DataUpdateListener>()

    interface DataUpdateListener {
        fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String)
        fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String)
    }

    fun getCachedRoomSnapshot(): DataSnapshot? {
        if (!isCurrentUserValid()) {
            clearCache()
            return null
        }
        
        return if (System.currentTimeMillis() - lastUpdateTime < CACHE_DURATION) {
            cachedRoomSnapshot
        } else {
            null
        }
    }

    fun getCachedRoomNumber(): String? {
        if (!isCurrentUserValid()) {
            clearCache()
            return null
        }
        return cachedUserRoomNumber
    }

    private fun isCurrentUserValid(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return false
        
        val cachedUID = cachedUserUID ?: return false
        val cachedPhone = cachedUserPhone ?: return false
        
        return currentUser.uid == cachedUID && currentUser.phoneNumber == cachedPhone
    }

    fun setCachedData(roomSnapshot: DataSnapshot, roomNumber: String, userPhone: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        
        if (currentUser.phoneNumber != userPhone) return

        val isFirstTimeCache = cachedRoomSnapshot == null
        val wasUserDifferent = cachedUserUID != null && cachedUserUID != currentUser.uid

        if (wasUserDifferent) {
            clearCache()
        }

        cachedRoomSnapshot = roomSnapshot
        cachedUserRoomNumber = roomNumber
        cachedUserPhone = userPhone
        cachedUserUID = currentUser.uid
        lastUpdateTime = System.currentTimeMillis()

        listeners.forEach { listener ->
            try {
                if (isFirstTimeCache || wasUserDifferent) {
                    listener.onCacheReady(roomSnapshot, roomNumber)
                } else {
                    listener.onDataUpdated(roomSnapshot, roomNumber)
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun addListener(listener: DataUpdateListener) {
        listeners.add(listener)

        val roomSnapshot = cachedRoomSnapshot
        val roomNumber = cachedUserRoomNumber
        
        if (isCacheValid() && roomSnapshot != null && roomNumber != null) {
            try {
                listener.onCacheReady(roomSnapshot, roomNumber)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun removeListener(listener: DataUpdateListener) {
        listeners.remove(listener)
    }

    fun clearCache() {
        cachedRoomSnapshot = null
        cachedUserRoomNumber = null
        cachedUserPhone = null
        cachedUserUID = null
        lastUpdateTime = 0
    }

    private fun isCacheValid(): Boolean {
        return isCurrentUserValid() && 
               System.currentTimeMillis() - lastUpdateTime < CACHE_DURATION &&
               cachedRoomSnapshot != null &&
               cachedUserRoomNumber != null
    }

    fun checkAndClearIfUserChanged() {
        if (!isCurrentUserValid()) {
            clearCache()
        }
    }
}