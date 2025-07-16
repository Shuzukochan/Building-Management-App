package com.app.buildingmanagement.utils

/**
 * Constants used throughout the application
 * 
 * Centralized location for all application constants including
 * configuration values, API endpoints, and default settings.
 */
object Constants {
    
    // App Configuration
    const val APP_NAME = "Building Management"
    const val APP_VERSION = "1.0.0"
    const val DATABASE_VERSION = 1
    
    // Firebase Configuration
    const val FIREBASE_DATABASE_URL = "https://building-management-default-rtdb.firebaseio.com/"
    const val FIREBASE_STORAGE_BUCKET = "building-management.appspot.com"
    
    // Database Paths (duplicated from FirebaseConfig for convenience)
    object DatabasePaths {
        const val USERS = "users"
        const val ROOMS = "rooms"
        const val PAYMENTS = "payments"
        const val STATISTICS = "statistics"
        const val NOTIFICATIONS = "notifications"
        const val MAINTENANCE = "maintenance"
        const val SETTINGS = "settings"
    }
    
    // User Roles
    object UserRoles {
        const val ADMIN = "admin"
        const val LANDLORD = "landlord"
        const val TENANT = "tenant"
        const val MAINTENANCE = "maintenance"
        const val STAFF = "staff"
    }
    
    // User Status
    object UserStatus {
        const val ACTIVE = "active"
        const val INACTIVE = "inactive"
        const val SUSPENDED = "suspended"
        const val PENDING = "pending"
    }
    
    // Room Status
    object RoomStatus {
        const val VACANT = "vacant"
        const val OCCUPIED = "occupied"
        const val MAINTENANCE = "maintenance"
        const val RESERVED = "reserved"
        const val OUT_OF_ORDER = "out_of_order"
    }
    
    // Room Types
    object RoomTypes {
        const val STUDIO = "studio"
        const val ONE_BEDROOM = "1br"
        const val TWO_BEDROOM = "2br"
        const val THREE_BEDROOM = "3br"
        const val PENTHOUSE = "penthouse"
    }
    
    // Payment Status
    object PaymentStatus {
        const val PENDING = "pending"
        const val PAID = "paid"
        const val CANCELLED = "cancelled"
        const val OVERDUE = "overdue"
        const val REFUNDED = "refunded"
        const val PARTIAL = "partial"
    }
    
    // Payment Types
    object PaymentTypes {
        const val RENT = "rent"
        const val UTILITIES = "utilities"
        const val DEPOSIT = "deposit"
        const val MAINTENANCE = "maintenance"
        const val PENALTY = "penalty"
        const val PARKING = "parking"
        const val INTERNET = "internet"
        const val CLEANING = "cleaning"
        const val SECURITY = "security"
    }
    
    // Payment Methods
    object PaymentMethods {
        const val CASH = "cash"
        const val BANK_TRANSFER = "bank_transfer"
        const val CREDIT_CARD = "credit_card"
        const val DEBIT_CARD = "debit_card"
        const val E_WALLET = "e_wallet"
        const val CHECK = "check"
    }
    
    // Notification Types
    object NotificationTypes {
        const val PAYMENT_REMINDER = "payment_reminder"
        const val PAYMENT_RECEIVED = "payment_received"
        const val MAINTENANCE_REQUEST = "maintenance_request"
        const val MAINTENANCE_COMPLETED = "maintenance_completed"
        const val LEASE_EXPIRING = "lease_expiring"
        const val ANNOUNCEMENT = "announcement"
        const val SYSTEM_UPDATE = "system_update"
        const val EMERGENCY = "emergency"
    }
    
    // Maintenance Types
    object MaintenanceTypes {
        const val INSPECTION = "inspection"
        const val REPAIR = "repair"
        const val UPGRADE = "upgrade"
        const val CLEANING = "cleaning"
        const val PREVENTIVE = "preventive"
        const val EMERGENCY = "emergency"
    }
    
    // Maintenance Status
    object MaintenanceStatus {
        const val PENDING = "pending"
        const val IN_PROGRESS = "in_progress"
        const val COMPLETED = "completed"
        const val CANCELLED = "cancelled"
        const val ON_HOLD = "on_hold"
    }
    
    // Validation Constants
    object Validation {
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_PASSWORD_LENGTH = 128
        const val MIN_PHONE_LENGTH = 10
        const val MAX_PHONE_LENGTH = 15
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 50
        const val MIN_ROOM_NUMBER_LENGTH = 1
        const val MAX_ROOM_NUMBER_LENGTH = 10
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_NOTES_LENGTH = 1000
    }
    
    // API and Network
    object Network {
        const val TIMEOUT_DURATION = 30L // seconds
        const val RETRY_COUNT = 3
        const val CACHE_SIZE = 10 * 1024 * 1024L // 10 MB
    }
    
    // UI Constants
    object UI {
        const val DEFAULT_ANIMATION_DURATION = 300L
        const val DEBOUNCE_TIME = 500L
        const val ITEMS_PER_PAGE = 20
        const val MAX_IMAGE_SIZE = 5 * 1024 * 1024L // 5 MB
        const val REFRESH_INTERVAL = 30000L // 30 seconds
    }
    
    // Date and Time Formats
    object DateFormats {
        const val DATE_FORMAT = "dd/MM/yyyy"
        const val TIME_FORMAT = "HH:mm"
        const val DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm"
        const val ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        const val MONTH_YEAR_FORMAT = "MM/yyyy"
        const val DISPLAY_DATE_FORMAT = "EEEE, dd MMMM yyyy"
    }
    
    // Currency and Formatting
    object Currency {
        const val DEFAULT_CURRENCY = "VND"
        const val CURRENCY_SYMBOL = "₫"
        const val DECIMAL_PLACES = 0
    }
    
    // File and Storage
    object Storage {
        const val PROFILE_IMAGES_PATH = "profile_images"
        const val ROOM_IMAGES_PATH = "room_images"
        const val DOCUMENTS_PATH = "documents"
        const val REPORTS_PATH = "reports"
        const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10 MB
        const val ALLOWED_IMAGE_TYPES = "image/jpeg,image/png,image/webp"
        const val ALLOWED_DOCUMENT_TYPES = "application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
    
    // Permissions
    object Permissions {
        const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        const val WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
        const val CAMERA = "android.permission.CAMERA"
        const val READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
        const val CALL_PHONE = "android.permission.CALL_PHONE"
        const val SEND_SMS = "android.permission.SEND_SMS"
    }
    
    // Intent Extras
    object Extras {
        const val USER_ID = "user_id"
        const val ROOM_ID = "room_id"
        const val PAYMENT_ID = "payment_id"
        const val NOTIFICATION_ID = "notification_id"
        const val IS_EDIT_MODE = "is_edit_mode"
        const val TITLE = "title"
        const val MESSAGE = "message"
        const val TYPE = "type"
    }
    
    // Preferences Keys
    object PreferenceKeys {
        const val USER_PREFERENCES = "user_preferences"
        const val THEME_MODE = "theme_mode"
        const val LANGUAGE = "language"
        const val NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val AUTO_BACKUP = "auto_backup"
        const val LAST_SYNC = "last_sync"
        const val FIRST_LAUNCH = "first_launch"
    }
    
    // Theme Constants
    object Theme {
        const val LIGHT = "light"
        const val DARK = "dark"
        const val SYSTEM = "system"
    }
    
    // Language Constants
    object Languages {
        const val VIETNAMESE = "vi"
        const val ENGLISH = "en"
    }
    
    // Error Codes
    object ErrorCodes {
        const val NETWORK_ERROR = 1001
        const val AUTHENTICATION_ERROR = 1002
        const val PERMISSION_DENIED = 1003
        const val DATA_NOT_FOUND = 1004
        const val VALIDATION_ERROR = 1005
        const val SERVER_ERROR = 1006
        const val UNKNOWN_ERROR = 9999
    }
    
    // Default Values
    object Defaults {
        const val DEFAULT_ROOM_AREA = 25.0 // square meters
        const val DEFAULT_MAX_OCCUPANTS = 2
        const val DEFAULT_LEASE_DURATION = 12 // months
        const val DEFAULT_LATE_FEE_PERCENTAGE = 5.0
        const val DEFAULT_DEPOSIT_MULTIPLIER = 2.0 // 2 months rent
        const val DEFAULT_INSPECTION_INTERVAL = 6 // months
    }
    
    // Regular Expressions
    object Regex {
        const val EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
        const val PHONE_PATTERN = "^[+]?[0-9]{10,15}$"
        const val VIETNAMESE_PHONE_PATTERN = "^(\\+84|0)[1-9][0-9]{8,9}$"
        const val PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d@$!%*?&]{6,}$"
        const val ROOM_NUMBER_PATTERN = "^[A-Za-z0-9]{1,10}$"
        const val CURRENCY_PATTERN = "^[0-9]{1,3}(,[0-9]{3})*$"
    }
    
    // API Response Codes
    object ResponseCodes {
        const val SUCCESS = 200
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val INTERNAL_SERVER_ERROR = 500
    }
}