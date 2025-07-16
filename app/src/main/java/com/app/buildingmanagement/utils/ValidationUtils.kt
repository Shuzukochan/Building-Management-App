package com.app.buildingmanagement.utils

import android.util.Patterns
import java.util.regex.Pattern

/**
 * Validation utility functions for the application
 * 
 * Provides validation methods for various data types including
 * email, phone numbers, passwords, and other input fields.
 */
object ValidationUtils {
    
    /**
     * Validate email address
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && 
               Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
               Pattern.matches(Constants.Regex.EMAIL_PATTERN, email)
    }
    
    /**
     * Validate password strength
     */
    fun isValidPassword(password: String): Boolean {
        return password.length >= Constants.Validation.MIN_PASSWORD_LENGTH &&
               password.length <= Constants.Validation.MAX_PASSWORD_LENGTH
    }
    
    /**
     * Validate strong password (with complexity requirements)
     */
    fun isStrongPassword(password: String): Boolean {
        return isValidPassword(password) &&
               Pattern.matches(Constants.Regex.PASSWORD_PATTERN, password)
    }
    
    /**
     * Validate phone number (general format)
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleanPhone = phoneNumber.replace("\\s".toRegex(), "")
        return cleanPhone.length >= Constants.Validation.MIN_PHONE_LENGTH &&
               cleanPhone.length <= Constants.Validation.MAX_PHONE_LENGTH &&
               Pattern.matches(Constants.Regex.PHONE_PATTERN, cleanPhone)
    }
    
    /**
     * Validate Vietnamese phone number
     */
    fun isValidVietnamesePhoneNumber(phoneNumber: String): Boolean {
        val cleanPhone = phoneNumber.replace("\\s".toRegex(), "")
        return Pattern.matches(Constants.Regex.VIETNAMESE_PHONE_PATTERN, cleanPhone)
    }
    
    /**
     * Validate full name
     */
    fun isValidFullName(fullName: String): Boolean {
        val trimmedName = fullName.trim()
        return trimmedName.length >= Constants.Validation.MIN_NAME_LENGTH &&
               trimmedName.length <= Constants.Validation.MAX_NAME_LENGTH &&
               trimmedName.matches(Regex("^[a-zA-ZÀ-ỹ\\s]+$")) // Vietnamese characters and spaces
    }
    
    /**
     * Validate room number
     */
    fun isValidRoomNumber(roomNumber: String): Boolean {
        return roomNumber.length >= Constants.Validation.MIN_ROOM_NUMBER_LENGTH &&
               roomNumber.length <= Constants.Validation.MAX_ROOM_NUMBER_LENGTH &&
               Pattern.matches(Constants.Regex.ROOM_NUMBER_PATTERN, roomNumber)
    }
    
    /**
     * Validate amount (payment, rent, etc.)
     */
    fun isValidAmount(amount: Double): Boolean {
        return amount > 0 && amount <= Double.MAX_VALUE
    }
    
    /**
     * Validate amount string
     */
    fun isValidAmountString(amountString: String): Boolean {
        return try {
            val amount = amountString.replace(",", "").toDouble()
            isValidAmount(amount)
        } catch (e: NumberFormatException) {
            false
        }
    }
    
    /**
     * Validate percentage (0-100)
     */
    fun isValidPercentage(percentage: Double): Boolean {
        return percentage >= 0.0 && percentage <= 100.0
    }
    
    /**
     * Validate description text
     */
    fun isValidDescription(description: String): Boolean {
        return description.length <= Constants.Validation.MAX_DESCRIPTION_LENGTH
    }
    
    /**
     * Validate notes text
     */
    fun isValidNotes(notes: String): Boolean {
        return notes.length <= Constants.Validation.MAX_NOTES_LENGTH
    }
    
    /**
     * Validate ID number (Vietnamese)
     */
    fun isValidVietnameseIdNumber(idNumber: String): Boolean {
        val cleanId = idNumber.replace("\\s".toRegex(), "")
        return when (cleanId.length) {
            9 -> cleanId.matches(Regex("^[0-9]{9}$")) // Old ID format
            12 -> cleanId.matches(Regex("^[0-9]{12}$")) // New ID format
            else -> false
        }
    }
    
    /**
     * Validate passport number
     */
    fun isValidPassportNumber(passportNumber: String): Boolean {
        val cleanPassport = passportNumber.replace("\\s".toRegex(), "")
        return cleanPassport.length >= 6 && 
               cleanPassport.length <= 12 &&
               cleanPassport.matches(Regex("^[A-Z0-9]+$"))
    }
    
    /**
     * Validate area (square meters)
     */
    fun isValidArea(area: Double): Boolean {
        return area > 0 && area <= 10000.0 // Max 10,000 sqm
    }
    
    /**
     * Validate floor number
     */
    fun isValidFloor(floor: Int): Boolean {
        return floor >= -5 && floor <= 100 // Basement to 100th floor
    }
    
    /**
     * Validate occupant count
     */
    fun isValidOccupantCount(count: Int): Boolean {
        return count >= 0 && count <= 20 // Max 20 occupants per room
    }
    
    /**
     * Validate URL
     */
    fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }
    
    /**
     * Validate file size
     */
    fun isValidFileSize(sizeInBytes: Long, maxSizeInBytes: Long = Constants.Storage.MAX_FILE_SIZE): Boolean {
        return sizeInBytes > 0 && sizeInBytes <= maxSizeInBytes
    }
    
    /**
     * Validate image file type
     */
    fun isValidImageType(mimeType: String): Boolean {
        return Constants.Storage.ALLOWED_IMAGE_TYPES.split(",").contains(mimeType)
    }
    
    /**
     * Validate document file type
     */
    fun isValidDocumentType(mimeType: String): Boolean {
        return Constants.Storage.ALLOWED_DOCUMENT_TYPES.split(",").contains(mimeType)
    }
    
    /**
     * Validate lease duration (in months)
     */
    fun isValidLeaseDuration(months: Int): Boolean {
        return months >= 1 && months <= 120 // 1 month to 10 years
    }
    
    /**
     * Validate coordinates (latitude/longitude)
     */
    fun isValidLatitude(latitude: Double): Boolean {
        return latitude >= -90.0 && latitude <= 90.0
    }
    
    fun isValidLongitude(longitude: Double): Boolean {
        return longitude >= -180.0 && longitude <= 180.0
    }
    
    /**
     * Validate age
     */
    fun isValidAge(age: Int): Boolean {
        return age >= 0 && age <= 150
    }
    
    /**
     * Validate input is not empty or just whitespace
     */
    fun isNotEmpty(input: String): Boolean {
        return input.trim().isNotEmpty()
    }
    
    /**
     * Validate input length
     */
    fun isValidLength(input: String, minLength: Int, maxLength: Int): Boolean {
        return input.length >= minLength && input.length <= maxLength
    }
    
    /**
     * Validate alphanumeric input
     */
    fun isAlphanumeric(input: String): Boolean {
        return input.matches(Regex("^[a-zA-Z0-9]+$"))
    }
    
    /**
     * Validate numeric input
     */
    fun isNumeric(input: String): Boolean {
        return input.matches(Regex("^[0-9]+$"))
    }
    
    /**
     * Validate alphabetic input (including Vietnamese characters)
     */
    fun isAlphabetic(input: String): Boolean {
        return input.matches(Regex("^[a-zA-ZÀ-ỹ\\s]+$"))
    }
    
    /**
     * Get password strength score (0-4)
     * 0: Very weak, 1: Weak, 2: Fair, 3: Good, 4: Strong
     */
    fun getPasswordStrength(password: String): Int {
        var score = 0
        
        if (password.length >= 8) score++
        if (password.matches(Regex(".*[a-z].*"))) score++
        if (password.matches(Regex(".*[A-Z].*"))) score++
        if (password.matches(Regex(".*[0-9].*"))) score++
        if (password.matches(Regex(".*[!@#$%^&*()].*"))) score++
        
        return minOf(score, 4)
    }
    
    /**
     * Get password strength description
     */
    fun getPasswordStrengthDescription(strength: Int): String {
        return when (strength) {
            0 -> "Rất yếu"
            1 -> "Yếu"
            2 -> "Trung bình"
            3 -> "Tốt"
            4 -> "Mạnh"
            else -> "Không xác định"
        }
    }
    
    /**
     * Validate payment method
     */
    fun isValidPaymentMethod(method: String): Boolean {
        return method in listOf(
            Constants.PaymentMethods.CASH,
            Constants.PaymentMethods.BANK_TRANSFER,
            Constants.PaymentMethods.CREDIT_CARD,
            Constants.PaymentMethods.DEBIT_CARD,
            Constants.PaymentMethods.E_WALLET,
            Constants.PaymentMethods.CHECK
        )
    }
    
    /**
     * Validate user role
     */
    fun isValidUserRole(role: String): Boolean {
        return role in listOf(
            Constants.UserRoles.ADMIN,
            Constants.UserRoles.LANDLORD,
            Constants.UserRoles.TENANT,
            Constants.UserRoles.MAINTENANCE,
            Constants.UserRoles.STAFF
        )
    }
    
    /**
     * Validate room status
     */
    fun isValidRoomStatus(status: String): Boolean {
        return status in listOf(
            Constants.RoomStatus.VACANT,
            Constants.RoomStatus.OCCUPIED,
            Constants.RoomStatus.MAINTENANCE,
            Constants.RoomStatus.RESERVED,
            Constants.RoomStatus.OUT_OF_ORDER
        )
    }
    
    /**
     * Validate payment status
     */
    fun isValidPaymentStatus(status: String): Boolean {
        return status in listOf(
            Constants.PaymentStatus.PENDING,
            Constants.PaymentStatus.PAID,
            Constants.PaymentStatus.CANCELLED,
            Constants.PaymentStatus.OVERDUE,
            Constants.PaymentStatus.REFUNDED,
            Constants.PaymentStatus.PARTIAL
        )
    }
    
    /**
     * Sanitize input string (remove harmful characters)
     */
    fun sanitizeInput(input: String): String {
        return input.trim()
            .replace(Regex("[<>\"'%;()&+]"), "")
            .replace(Regex("\\s+"), " ")
    }
    
    /**
     * Validate and sanitize user input
     */
    fun validateAndSanitize(
        input: String,
        minLength: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
        allowEmpty: Boolean = false
    ): Pair<Boolean, String> {
        val sanitized = sanitizeInput(input)
        
        if (!allowEmpty && sanitized.isEmpty()) {
            return Pair(false, sanitized)
        }
        
        if (!isValidLength(sanitized, minLength, maxLength)) {
            return Pair(false, sanitized)
        }
        
        return Pair(true, sanitized)
    }
}