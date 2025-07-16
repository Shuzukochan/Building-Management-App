package com.app.buildingmanagement.models

import java.util.Date

/**
 * User data model
 * 
 * Represents a user in the building management system.
 * Can be tenant, landlord, admin, or maintenance staff.
 */
data class User(
    val id: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val fullName: String = "",
    val role: String = "tenant", // tenant, landlord, admin, maintenance, staff
    val status: String = "active", // active, inactive, suspended
    val profileImage: String? = null,
    val address: Address? = null,
    val emergencyContact: EmergencyContact? = null,
    val personalInfo: PersonalInfo = PersonalInfo(),
    val preferences: UserPreferences = UserPreferences(),
    val tenantInfo: TenantInfo? = null,
    val staffInfo: StaffInfo? = null,
    val createdDate: Date = Date(),
    val lastLoginDate: Date? = null,
    val isEmailVerified: Boolean = false,
    val isPhoneVerified: Boolean = false,
    val documents: List<UserDocument> = emptyList(),
    val paymentMethods: List<PaymentMethodInfo> = emptyList()
) {
    /**
     * Check if user is a tenant
     */
    fun isTenant(): Boolean {
        return role == "tenant"
    }
    
    /**
     * Check if user is staff (admin, maintenance, etc.)
     */
    fun isStaff(): Boolean {
        return role in listOf("admin", "maintenance", "staff", "landlord")
    }
    
    /**
     * Check if user is admin
     */
    fun isAdmin(): Boolean {
        return role == "admin"
    }
    
    /**
     * Check if user account is active
     */
    fun isActive(): Boolean {
        return status == "active"
    }
    
    /**
     * Check if user profile is complete
     */
    fun isProfileComplete(): Boolean {
        return fullName.isNotEmpty() && 
               phoneNumber.isNotEmpty() && 
               address != null &&
               emergencyContact != null
    }
    
    /**
     * Check if user is verified
     */
    fun isVerified(): Boolean {
        return isEmailVerified && isPhoneVerified
    }
    
    /**
     * Get user display name
     */
    fun getDisplayName(): String {
        return fullName.ifEmpty { email.substringBefore("@") }
    }
    
    /**
     * Get role display name
     */
    fun getRoleDisplayName(): String {
        return when (role) {
            "tenant" -> "Người thuê"
            "landlord" -> "Chủ nhà"
            "admin" -> "Quản trị viên"
            "maintenance" -> "Bảo trì"
            "staff" -> "Nhân viên"
            else -> role.capitalize()
        }
    }
    
    /**
     * Get user initials for avatar
     */
    fun getInitials(): String {
        return fullName.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
    }
}

/**
 * User address information
 */
data class Address(
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "Vietnam",
    val isDefault: Boolean = true
) {
    /**
     * Get formatted address
     */
    fun getFormattedAddress(): String {
        return listOf(street, city, state, postalCode, country)
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }
}

/**
 * Emergency contact information
 */
data class EmergencyContact(
    val name: String = "",
    val relationship: String = "",
    val phoneNumber: String = "",
    val email: String? = null,
    val address: String? = null
)

/**
 * Personal information
 */
data class PersonalInfo(
    val dateOfBirth: Date? = null,
    val idNumber: String = "", // National ID or passport
    val idType: String = "national_id", // national_id, passport, driver_license
    val occupation: String = "",
    val employer: String = "",
    val monthlyIncome: Double = 0.0,
    val maritalStatus: String = "", // single, married, divorced, widowed
    val numberOfDependents: Int = 0,
    val notes: String = ""
) {
    /**
     * Calculate age from date of birth
     */
    fun getAge(): Int? {
        return dateOfBirth?.let { dob ->
            val now = Date()
            val diffInMillis = now.time - dob.time
            val ageInDays = diffInMillis / (24 * 60 * 60 * 1000)
            (ageInDays / 365).toInt()
        }
    }
    
    /**
     * Get ID type display name
     */
    fun getIdTypeDisplayName(): String {
        return when (idType) {
            "national_id" -> "CMND/CCCD"
            "passport" -> "Hộ chiếu"
            "driver_license" -> "Bằng lái xe"
            else -> idType
        }
    }
}

/**
 * User preferences
 */
data class UserPreferences(
    val language: String = "vi", // vi, en
    val timezone: String = "Asia/Ho_Chi_Minh",
    val currency: String = "VND",
    val theme: String = "system", // light, dark, system
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val privacySettings: PrivacySettings = PrivacySettings()
)

/**
 * Notification settings
 */
data class NotificationSettings(
    val emailNotifications: Boolean = true,
    val pushNotifications: Boolean = true,
    val smsNotifications: Boolean = false,
    val paymentReminders: Boolean = true,
    val maintenanceUpdates: Boolean = true,
    val announcements: Boolean = true,
    val marketingEmails: Boolean = false
)

/**
 * Privacy settings
 */
data class PrivacySettings(
    val profileVisibility: String = "tenants_only", // public, tenants_only, private
    val showContactInfo: Boolean = false,
    val showAddress: Boolean = false,
    val allowDirectMessages: Boolean = true
)

/**
 * Tenant-specific information
 */
data class TenantInfo(
    val currentRoomId: String? = null,
    val leaseStartDate: Date? = null,
    val leaseEndDate: Date? = null,
    val monthlyRent: Double = 0.0,
    val deposit: Double = 0.0,
    val moveInDate: Date? = null,
    val moveOutDate: Date? = null,
    val references: List<Reference> = emptyList(),
    val creditScore: Int? = null,
    val rentalHistory: List<RentalHistory> = emptyList()
) {
    /**
     * Check if tenant has active lease
     */
    fun hasActiveLease(): Boolean {
        val now = Date()
        return leaseStartDate?.let { start ->
            leaseEndDate?.let { end ->
                now.after(start) && now.before(end)
            } ?: false
        } ?: false
    }
}

/**
 * Staff-specific information
 */
data class StaffInfo(
    val employeeId: String = "",
    val department: String = "",
    val position: String = "",
    val hireDate: Date? = null,
    val salary: Double = 0.0,
    val permissions: List<String> = emptyList(),
    val workSchedule: WorkSchedule? = null,
    val supervisor: String? = null
)

/**
 * Reference information
 */
data class Reference(
    val name: String = "",
    val relationship: String = "",
    val phoneNumber: String = "",
    val email: String? = null,
    val verified: Boolean = false
)

/**
 * Rental history
 */
data class RentalHistory(
    val address: String = "",
    val landlordName: String = "",
    val landlordContact: String = "",
    val startDate: Date = Date(),
    val endDate: Date = Date(),
    val monthlyRent: Double = 0.0,
    val reasonForLeaving: String = ""
)

/**
 * Work schedule
 */
data class WorkSchedule(
    val mondayHours: String = "",
    val tuesdayHours: String = "",
    val wednesdayHours: String = "",
    val thursdayHours: String = "",
    val fridayHours: String = "",
    val saturdayHours: String = "",
    val sundayHours: String = ""
)

/**
 * User document
 */
data class UserDocument(
    val id: String = "",
    val type: String = "", // id_card, passport, contract, income_proof
    val name: String = "",
    val url: String = "",
    val uploadDate: Date = Date(),
    val verified: Boolean = false,
    val verifiedBy: String? = null,
    val verifiedDate: Date? = null
)

/**
 * Payment method information
 */
data class PaymentMethodInfo(
    val id: String = "",
    val type: String = "", // bank_account, credit_card, e_wallet
    val name: String = "",
    val details: Map<String, String> = emptyMap(), // encrypted sensitive data
    val isDefault: Boolean = false,
    val isActive: Boolean = true
)

/**
 * User role constants
 */
object UserRole {
    const val TENANT = "tenant"
    const val LANDLORD = "landlord"
    const val ADMIN = "admin"
    const val MAINTENANCE = "maintenance"
    const val STAFF = "staff"
}

/**
 * User status constants
 */
object UserStatus {
    const val ACTIVE = "active"
    const val INACTIVE = "inactive"
    const val SUSPENDED = "suspended"
}