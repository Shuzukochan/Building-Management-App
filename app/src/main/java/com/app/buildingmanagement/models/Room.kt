package com.app.buildingmanagement.models

import java.util.Date

/**
 * Room data model
 * 
 * Represents a room/apartment in the building management system.
 * Contains information about room details, occupancy, and status.
 */
data class Room(
    val id: String = "",
    val roomNumber: String = "",
    val floor: Int = 0,
    val type: String = "", // studio, 1br, 2br, etc.
    val area: Double = 0.0, // square meters
    val maxOccupants: Int = 1,
    val currentOccupants: Int = 0,
    val monthlyRent: Double = 0.0,
    val deposit: Double = 0.0,
    val status: String = "vacant", // vacant, occupied, maintenance, reserved
    val amenities: List<String> = emptyList(),
    val description: String = "",
    val images: List<String> = emptyList(),
    val tenantId: String? = null,
    val leaseStartDate: Date? = null,
    val leaseEndDate: Date? = null,
    val createdDate: Date = Date(),
    val updatedDate: Date = Date(),
    val utilities: RoomUtilities = RoomUtilities(),
    val maintenance: MaintenanceInfo = MaintenanceInfo()
) {
    /**
     * Check if room is available for rent
     */
    fun isAvailable(): Boolean {
        return status == "vacant" && maintenance.isOperational()
    }
    
    /**
     * Check if room is occupied
     */
    fun isOccupied(): Boolean {
        return status == "occupied" && tenantId != null
    }
    
    /**
     * Check if lease is active
     */
    fun hasActiveLease(): Boolean {
        val now = Date()
        return leaseStartDate?.let { start ->
            leaseEndDate?.let { end ->
                now.after(start) && now.before(end)
            } ?: false
        } ?: false
    }
    
    /**
     * Get monthly total cost including utilities
     */
    fun getTotalMonthlyCost(): Double {
        return monthlyRent + utilities.getTotalMonthlyCost()
    }
    
    /**
     * Get occupancy rate as percentage
     */
    fun getOccupancyRate(): Int {
        return if (maxOccupants > 0) {
            ((currentOccupants.toDouble() / maxOccupants) * 100).toInt()
        } else 0
    }
    
    /**
     * Get room status display name
     */
    fun getStatusDisplayName(): String {
        return when (status) {
            "vacant" -> "Trống"
            "occupied" -> "Đã thuê"
            "maintenance" -> "Bảo trì"
            "reserved" -> "Đã đặt cọc"
            else -> "Không xác định"
        }
    }
    
    /**
     * Get room type display name
     */
    fun getTypeDisplayName(): String {
        return when (type) {
            "studio" -> "Căn hộ Studio"
            "1br" -> "1 phòng ngủ"
            "2br" -> "2 phòng ngủ"
            "3br" -> "3 phòng ngủ"
            "penthouse" -> "Penthouse"
            else -> type.capitalize()
        }
    }
}

/**
 * Room utilities information
 */
data class RoomUtilities(
    val electricity: Boolean = true,
    val water: Boolean = true,
    val internet: Boolean = false,
    val airConditioning: Boolean = false,
    val heating: Boolean = false,
    val parking: Boolean = false,
    val electricityCost: Double = 0.0, // per unit
    val waterCost: Double = 0.0, // per unit
    val internetCost: Double = 0.0, // monthly fixed
    val parkingCost: Double = 0.0, // monthly fixed
    val otherUtilities: Map<String, Double> = emptyMap() // name to monthly cost
) {
    /**
     * Get total monthly utilities cost (fixed costs only)
     */
    fun getTotalMonthlyCost(): Double {
        var total = 0.0
        
        if (internet) total += internetCost
        if (parking) total += parkingCost
        
        // Add other fixed utilities
        total += otherUtilities.values.sum()
        
        return total
    }
    
    /**
     * Get list of included utilities
     */
    fun getIncludedUtilities(): List<String> {
        val utilities = mutableListOf<String>()
        
        if (electricity) utilities.add("Điện")
        if (water) utilities.add("Nước")
        if (internet) utilities.add("Internet")
        if (airConditioning) utilities.add("Điều hòa")
        if (heating) utilities.add("Sưởi ấm")
        if (parking) utilities.add("Chỗ đậu xe")
        
        utilities.addAll(otherUtilities.keys)
        
        return utilities
    }
}

/**
 * Room maintenance information
 */
data class MaintenanceInfo(
    val lastInspection: Date? = null,
    val nextInspection: Date? = null,
    val maintenanceHistory: List<MaintenanceRecord> = emptyList(),
    val currentIssues: List<String> = emptyList(),
    val isUnderMaintenance: Boolean = false,
    val maintenanceNotes: String = ""
) {
    /**
     * Check if room is operational (no blocking maintenance issues)
     */
    fun isOperational(): Boolean {
        return !isUnderMaintenance && currentIssues.isEmpty()
    }
    
    /**
     * Check if inspection is due
     */
    fun isInspectionDue(): Boolean {
        return nextInspection?.let { next ->
            Date().after(next)
        } ?: false
    }
    
    /**
     * Get maintenance status
     */
    fun getMaintenanceStatus(): String {
        return when {
            isUnderMaintenance -> "Đang bảo trì"
            currentIssues.isNotEmpty() -> "Có vấn đề"
            isInspectionDue() -> "Cần kiểm tra"
            else -> "Bình thường"
        }
    }
}

/**
 * Maintenance record
 */
data class MaintenanceRecord(
    val id: String = "",
    val date: Date = Date(),
    val type: String = "", // inspection, repair, upgrade
    val description: String = "",
    val cost: Double = 0.0,
    val performedBy: String = "",
    val status: String = "completed" // pending, in_progress, completed
)