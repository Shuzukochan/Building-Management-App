package com.app.buildingmanagement.repositories

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.app.buildingmanagement.config.FirebaseConfig
import com.app.buildingmanagement.models.Room
import com.app.buildingmanagement.models.MaintenanceRecord
import java.util.Date

/**
 * Room Repository
 * 
 * Handles data access operations for Room entities.
 * Implements Repository pattern for room management operations.
 */
class RoomRepository {
    
    private val database = FirebaseConfig.database
    private val roomsRef = database.reference.child(FirebaseConfig.DatabasePaths.ROOMS)
    
    /**
     * Get all rooms
     */
    suspend fun getAllRooms(): List<Room> {
        return try {
            val snapshot = roomsRef.get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Room::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get rooms flow for real-time updates
     */
    fun getRoomsFlow(): Flow<List<Room>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rooms = snapshot.children.mapNotNull { child ->
                    child.getValue(Room::class.java)
                }
                trySend(rooms)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        roomsRef.addValueEventListener(listener)
        
        awaitClose {
            roomsRef.removeEventListener(listener)
        }
    }
    
    /**
     * Get room by ID
     */
    suspend fun getRoomById(roomId: String): Room? {
        return try {
            val snapshot = roomsRef.child(roomId).get().await()
            snapshot.getValue(Room::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get rooms by floor
     */
    suspend fun getRoomsByFloor(floor: Int): List<Room> {
        return try {
            val snapshot = roomsRef.orderByChild("floor").equalTo(floor.toDouble()).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Room::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get rooms by status
     */
    suspend fun getRoomsByStatus(status: String): List<Room> {
        return try {
            val snapshot = roomsRef.orderByChild("status").equalTo(status).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Room::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get rooms by tenant
     */
    suspend fun getRoomsByTenant(tenantId: String): List<Room> {
        return try {
            val snapshot = roomsRef.orderByChild("tenantId").equalTo(tenantId).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(Room::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get available rooms
     */
    suspend fun getAvailableRooms(): List<Room> {
        return getRoomsByStatus("vacant").filter { room ->
            room.isAvailable()
        }
    }
    
    /**
     * Create new room
     */
    suspend fun createRoom(room: Room): Boolean {
        return try {
            roomsRef.child(room.id).setValue(room).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update room
     */
    suspend fun updateRoom(room: Room): Boolean {
        return try {
            val updatedRoom = room.copy(updatedDate = Date())
            roomsRef.child(room.id).setValue(updatedRoom).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update room status
     */
    suspend fun updateRoomStatus(roomId: String, status: String): Boolean {
        return try {
            val updates = mapOf(
                "status" to status,
                "updatedDate" to Date().time
            )
            roomsRef.child(roomId).updateChildren(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Assign tenant to room
     */
    suspend fun assignTenant(
        roomId: String, 
        tenantId: String, 
        leaseStartDate: Date, 
        leaseEndDate: Date
    ): Boolean {
        return try {
            val updates = mapOf(
                "status" to "occupied",
                "tenantId" to tenantId,
                "leaseStartDate" to leaseStartDate.time,
                "leaseEndDate" to leaseEndDate.time,
                "updatedDate" to Date().time
            )
            roomsRef.child(roomId).updateChildren(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Remove tenant from room
     */
    suspend fun removeTenant(roomId: String): Boolean {
        return try {
            val updates = mapOf(
                "status" to "vacant",
                "tenantId" to null,
                "leaseStartDate" to null,
                "leaseEndDate" to null,
                "currentOccupants" to 0,
                "updatedDate" to Date().time
            )
            roomsRef.child(roomId).updateChildren(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete room
     */
    suspend fun deleteRoom(roomId: String): Boolean {
        return try {
            roomsRef.child(roomId).removeValue().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Search rooms by criteria
     */
    suspend fun searchRooms(
        query: String,
        minRent: Double? = null,
        maxRent: Double? = null,
        floor: Int? = null,
        type: String? = null,
        status: String? = null
    ): List<Room> {
        return try {
            val allRooms = getAllRooms()
            
            allRooms.filter { room ->
                val matchesQuery = query.isEmpty() || 
                    room.roomNumber.contains(query, ignoreCase = true) ||
                    room.description.contains(query, ignoreCase = true)
                
                val matchesRent = (minRent == null || room.monthlyRent >= minRent) &&
                    (maxRent == null || room.monthlyRent <= maxRent)
                
                val matchesFloor = floor == null || room.floor == floor
                val matchesType = type == null || room.type == type
                val matchesStatus = status == null || room.status == status
                
                matchesQuery && matchesRent && matchesFloor && matchesType && matchesStatus
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Add maintenance record to room
     */
    suspend fun addMaintenanceRecord(roomId: String, record: MaintenanceRecord): Boolean {
        return try {
            val maintenanceRef = roomsRef.child(roomId).child("maintenance").child("maintenanceHistory")
            maintenanceRef.push().setValue(record).await()
            
            // Update last inspection date if it's an inspection
            if (record.type == "inspection") {
                roomsRef.child(roomId).child("maintenance").child("lastInspection")
                    .setValue(record.date.time).await()
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get maintenance history for room
     */
    suspend fun getMaintenanceHistory(roomId: String): List<MaintenanceRecord> {
        return try {
            val snapshot = roomsRef.child(roomId).child("maintenance")
                .child("maintenanceHistory").get().await()
            
            snapshot.children.mapNotNull { child ->
                child.getValue(MaintenanceRecord::class.java)
            }.sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Statistics methods
    
    /**
     * Get total rooms count
     */
    suspend fun getTotalRoomsCount(): Int {
        return try {
            val snapshot = roomsRef.get().await()
            snapshot.childrenCount.toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get occupied rooms count
     */
    suspend fun getOccupiedRoomsCount(): Int {
        return getRoomsByStatus("occupied").size
    }
    
    /**
     * Get vacant rooms count
     */
    suspend fun getVacantRoomsCount(): Int {
        return getRoomsByStatus("vacant").size
    }
    
    /**
     * Get maintenance rooms count
     */
    suspend fun getMaintenanceRoomsCount(): Int {
        return getRoomsByStatus("maintenance").size
    }
    
    /**
     * Get average rent
     */
    suspend fun getAverageRent(): Double {
        return try {
            val rooms = getAllRooms()
            if (rooms.isNotEmpty()) {
                rooms.map { it.monthlyRent }.average()
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Get occupancy rate
     */
    suspend fun getOccupancyRate(): Double {
        return try {
            val total = getTotalRoomsCount()
            val occupied = getOccupiedRoomsCount()
            
            if (total > 0) {
                (occupied.toDouble() / total) * 100
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Get rooms requiring maintenance
     */
    suspend fun getRoomsRequiringMaintenance(): List<Room> {
        return try {
            getAllRooms().filter { room ->
                room.maintenance.currentIssues.isNotEmpty() ||
                room.maintenance.isInspectionDue() ||
                room.status == "maintenance"
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}