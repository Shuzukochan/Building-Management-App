package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.app.buildingmanagement.databinding.ActivityMainBinding
import com.app.buildingmanagement.firebase.FCMHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private var auth: FirebaseAuth? = null
    private var navController: NavController? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val FCM_PREFS = "fcm_prefs"
        private const val FCM_TOKEN_KEY = "fcm_token"

        // Notification types constants
        private const val NOTIFICATION_TYPE_MAINTENANCE = "maintenance_request"
        private const val NOTIFICATION_TYPE_PAYMENT = "payment_reminder"
        private const val NOTIFICATION_TYPE_ANNOUNCEMENT = "announcement"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth

        if (!checkAuthenticationState()) {
            return
        }

        setupUI()
        setupNavigation()
        setupBottomNavigation()

        // Initialize FCM
        initializeFCMToken()

        // PRELOAD USER DATA để fragments load nhanh hơn
        preloadUserData()

        // Handle notification intent if any
        handleNotificationIntent()
    }

    private fun checkAuthenticationState(): Boolean {
        Log.d(TAG, "=== CHECKING AUTHENTICATION STATE ===")
        
        val currentUser = auth?.currentUser
        
        Log.d(TAG, "Firebase Auth instance: ${auth != null}")
        Log.d(TAG, "Current user object: $currentUser")
        Log.d(TAG, "User phone: ${currentUser?.phoneNumber}")
        Log.d(TAG, "User UID: ${currentUser?.uid}")
        Log.d(TAG, "Is user anonymous: ${currentUser?.isAnonymous}")
        
        if (currentUser == null) {
            Log.w(TAG, "❌ User not authenticated - redirecting to SignIn")
            Log.d(TAG, "=== AUTHENTICATION CHECK FAILED ===")
            redirectToSignIn()
            return false
        }

        Log.d(TAG, "✅ User authenticated: ${currentUser.phoneNumber}")
        Log.d(TAG, "=== AUTHENTICATION CHECK PASSED ===")
        return true
    }

    private fun setupUI() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun redirectToSignIn() {
        try {
            val intent = Intent(this, SignInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error redirecting to SignIn", e)
            finish()
        }
    }

    private fun setupNavigation() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.mainFragmentContainer) as? NavHostFragment

            if (navHostFragment != null) {
                navController = navHostFragment.navController
                Log.d(TAG, "Navigation setup successful")
            } else {
                Log.e(TAG, "NavHostFragment not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up navigation", e)
        }
    }

    private fun setupBottomNavigation() {
        try {
            val bottomNav = binding?.bottomNavigation
            val controller = navController

            if (bottomNav != null && controller != null) {
                bottomNav.setupWithNavController(controller)
                Log.d(TAG, "Bottom navigation setup successful")
            } else {
                Log.e(TAG, "Bottom navigation setup failed - missing components")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bottom navigation", e)
        }
    }

    private fun initializeFCMToken() {
        try {
            FCMHelper.getToken { token ->
                if (token != null) {
                    Log.d(TAG, "FCM Token generated: ${token.take(20)}...")
                    saveTokenToPrefs(token)

                    // SETUP LISTENER ĐỂ ĐỢI CÓ ROOM NUMBER
                    setupNotificationSubscriptionWhenReady()

                } else {
                    Log.w(TAG, "Failed to generate FCM token")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FCM token", e)
        }
    }

    private fun setupNotificationSubscriptionWhenReady() {
        // Kiểm tra cache trước
        val cachedRoomNumber = com.app.buildingmanagement.data.SharedDataManager.getCachedRoomNumber()
        if (cachedRoomNumber != null) {
            Log.d(TAG, "Room number available from cache: $cachedRoomNumber")
            
            // DELAY NHỎ ĐỂ ĐẢM BẢO FCM TOKEN ĐÃ SẴN SÀNG
            Handler(Looper.getMainLooper()).postDelayed({
                ensureCorrectSubscriptionState()
            }, 1000)
            return
        }

        // Nếu chưa có cache, đợi HomeFragment load xong
        val currentUser = auth?.currentUser
        val phone = currentUser?.phoneNumber

        if (phone != null) {
            Log.d(TAG, "Waiting for room data to be loaded...")
            val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")

            roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (roomSnapshot in snapshot.children) {
                        val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInRoom == phone) {
                            val roomNumber = roomSnapshot.key
                            if (roomNumber != null) {
                                Log.d(TAG, "Room number found: $roomNumber")
                                
                                // DELAY NHỎ ĐỂ ĐẢM BẢO FCM TOKEN ĐÃ SẴN SÀNG
                                Handler(Looper.getMainLooper()).postDelayed({
                                    ensureCorrectSubscriptionState()
                                }, 1000)
                            }
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error loading room data for subscription setup", error.toException())
                }
            })
        }
    }

    private fun ensureCorrectSubscriptionState() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val roomNumber = getCurrentUserRoomNumber()

        if (roomNumber == null) {
            Log.w(TAG, "Room number still not available for subscription setup")
            return
        }

        // Kiểm tra xem đã có setting chưa
        if (!sharedPref.contains("notifications_enabled")) {
            // LẦN ĐẦU TIÊN - SET DEFAULT VÀ SUBSCRIBE
            Log.d(TAG, "First time app launch - enabling notifications by default and subscribing to room: $roomNumber")
            sharedPref.edit().putBoolean("notifications_enabled", true).apply()
            
            // FORCE SUBSCRIBE LẦN ĐẦU TIÊN
            FCMHelper.subscribeToUserBuildingTopics(roomNumber)
            
            // Log để debug
            Log.d(TAG, "FORCE subscription completed for first-time user")

        } else {
            // ĐÃ CÓ SETTING - APPLY THEO SETTING
            val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)

            if (notificationsEnabled) {
                Log.d(TAG, "Ensuring topics are subscribed (user preference: enabled) for room: $roomNumber")
                // ALWAYS RE-SUBSCRIBE để đảm bảo không bị miss
                FCMHelper.subscribeToUserBuildingTopics(roomNumber)
            } else {
                Log.d(TAG, "Ensuring topics are unsubscribed (user preference: disabled) for room: $roomNumber")
                FCMHelper.unsubscribeFromBuildingTopics(roomNumber)
            }
        }
        
        // THÊM DELAY NHỎ ĐỂ ĐẢM BẢO SUBSCRIPTION HOÀN THÀNH
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Subscription state setup completed")
            verifySubscriptionStatus()
        }, 2000)
    }


    /**
     * Kiểm tra notification setting và subscribe topics tương ứng
     */
    private fun checkNotificationSettingsAndSubscribe() {
        // Lấy room number từ cache hoặc user data
        val roomNumber = getCurrentUserRoomNumber()

        // Check setting và subscribe accordingly
        FCMHelper.checkAndSubscribeBasedOnSettings(this, roomNumber)
    }

    /**
     * Helper method để lấy room number
     */
    private fun getCurrentUserRoomNumber(): String? {
        return com.app.buildingmanagement.data.SharedDataManager.getCachedRoomNumber()
    }

    /**
     * THÊM METHOD VERIFY SUBSCRIPTION STATUS
     */
    private fun verifySubscriptionStatus() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        val roomNumber = getCurrentUserRoomNumber()
        
        Log.d(TAG, "=== SUBSCRIPTION STATUS VERIFICATION ===")
        Log.d(TAG, "Notifications Enabled: $notificationsEnabled")
        Log.d(TAG, "Room Number: $roomNumber")
        Log.d(TAG, "Expected Topics: all_residents${if (roomNumber != null) ", room_$roomNumber, floor_${roomNumber.substring(0, 1)}" else ""}")
        Log.d(TAG, "=== END VERIFICATION ===")
    }

    /**
     * PUBLIC METHOD ĐỂ FORCE RE-SUBSCRIBE (có thể gọi từ SettingsFragment)
     */
    fun forceResubscribeNotifications() {
        Log.d(TAG, "Force re-subscribing notifications...")
        val roomNumber = getCurrentUserRoomNumber()
        if (roomNumber != null) {
            FCMHelper.subscribeToUserBuildingTopics(roomNumber)
            verifySubscriptionStatus()
        }
    }

    private fun saveTokenToPrefs(token: String) {
        try {
            val sharedPref = getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            val success = sharedPref.edit()
                .putString(FCM_TOKEN_KEY, token)
                .commit()

            if (success) {
                Log.d(TAG, "FCM token saved to SharedPreferences")
            } else {
                Log.e(TAG, "Failed to save FCM token to SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving FCM token to preferences", e)
        }
    }

    private fun handleNotificationIntent() {
        try {
            val extras = intent.extras
            if (extras == null) {
                Log.d(TAG, "No notification extras found")
                return
            }

            val notificationData = extras.getBundle("notification_data")
            if (notificationData == null) {
                Log.d(TAG, "No notification_data bundle found")
                return
            }

            val type = notificationData.getString("type")
            Log.d(TAG, "Processing notification with type: $type")

            when (type) {
                NOTIFICATION_TYPE_MAINTENANCE -> {
                    Log.d(TAG, "Handling maintenance notification")
                    navigateToMaintenanceScreen()
                }
                NOTIFICATION_TYPE_PAYMENT -> {
                    Log.d(TAG, "Handling payment notification")
                    navigateToPaymentScreen()
                }
                NOTIFICATION_TYPE_ANNOUNCEMENT -> {
                    Log.d(TAG, "Handling announcement notification")
                    navigateToAnnouncementScreen()
                }
                else -> {
                    Log.w(TAG, "Unknown notification type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification intent", e)
        }
    }

    private fun navigateToMaintenanceScreen() {
        try {
            Log.d(TAG, "Navigate to maintenance screen - TODO: Implement")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to maintenance screen", e)
        }
    }

    private fun navigateToPaymentScreen() {
        try {
            Log.d(TAG, "Navigate to payment screen - TODO: Implement")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to payment screen", e)
        }
    }

    private fun navigateToAnnouncementScreen() {
        try {
            Log.d(TAG, "Navigate to announcement screen - TODO: Implement")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to announcement screen", e)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!checkAuthenticationState()) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        if (auth?.currentUser == null) {
            Log.w(TAG, "User signed out while app was in background")
            redirectToSignIn()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            binding = null
            navController = null
            auth = null
            Log.d(TAG, "MainActivity destroyed and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup", e)
        }
    }

    fun onUserLogout() {
        Log.d(TAG, "=== MAINACTIVITY: STARTING LOGOUT PROCESS ===")
        
        try {
            val currentUser = auth?.currentUser
            if (currentUser != null) {
                val phone = currentUser.phoneNumber
                if (phone != null) {
                    Log.d(TAG, "Cleaning up FCM data for user: ${phone.take(10)}...")
                    cleanupFCMOnLogout(phone)
                }
            }

            // Clear all SharedPreferences
            Log.d(TAG, "Clearing all SharedPreferences...")
            clearAllMainActivityPreferences()

            // Sign out from Firebase
            Log.d(TAG, "Signing out from Firebase Auth...")
            auth?.signOut()
            
            // Verify logout
            Log.d(TAG, "Current user after signOut: ${auth?.currentUser}")
            
            Log.d(TAG, "User signed out successfully")
            Log.d(TAG, "=== MAINACTIVITY: LOGOUT PROCESS COMPLETED ===")
            
            redirectToSignIn()

        } catch (e: Exception) {
            Log.e(TAG, "Error during logout process", e)
            redirectToSignIn()
        }
    }

    private fun clearAllMainActivityPreferences() {
        try {
            // Clear FCM prefs
            val fcmPrefs = getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            fcmPrefs.edit().clear().apply()
            Log.d(TAG, "Cleared FCM preferences")

            // Clear app settings
            val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
            appSettings.edit().clear().apply()
            Log.d(TAG, "Cleared app_settings preferences")

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing MainActivity preferences", e)
        }
    }

    private fun cleanupFCMOnLogout(phone: String) {
        try {
            // Unsubscribe from FCM topics
            FCMHelper.unsubscribeFromBuildingTopics(null)

            // Clear FCM token from SharedPreferences
            val sharedPref = getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            sharedPref.edit()
                .remove(FCM_TOKEN_KEY)
                .apply()

            Log.d(TAG, "FCM cleanup completed for logout")

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up FCM data", e)
        }
    }

    fun getCurrentFCMToken(): String? {
        return try {
            val sharedPref = getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            sharedPref.getString(FCM_TOKEN_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token from preferences", e)
            null
        }
    }

    fun refreshFCMToken() {
        try {
            Log.d(TAG, "Refreshing FCM token...")
            initializeFCMToken()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing FCM token", e)
        }
    }

    private fun preloadUserData() {
        Log.d(TAG, "=== PRELOADING USER DATA ===")
        
        val currentUser = auth?.currentUser
        val phone = currentUser?.phoneNumber

        if (phone != null) {
            // KIỂM TRA USER CHANGE TRƯỚC
            com.app.buildingmanagement.data.SharedDataManager.checkAndClearIfUserChanged()
            
            // Kiểm tra cache trước
            val cachedSnapshot = com.app.buildingmanagement.data.SharedDataManager.getCachedRoomSnapshot()
            val cachedRoomNumber = com.app.buildingmanagement.data.SharedDataManager.getCachedRoomNumber()

            if (cachedSnapshot != null && cachedRoomNumber != null) {
                Log.d(TAG, "User data already cached for room: $cachedRoomNumber")
                return
            }

            // Nếu chưa có cache, preload từ Firebase với timeout
            Log.d(TAG, "No cached data, preloading from Firebase...")
            val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")

            // THÊM TIMEOUT ĐỂ TRÁNH BLOCKING
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.w(TAG, "⏰ Preload timeout - continuing without cache")
            }
            timeoutHandler.postDelayed(timeoutRunnable, 10000) // 10 giây timeout

            roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    timeoutHandler.removeCallbacks(timeoutRunnable) // Cancel timeout
                    
                    // KIỂM TRA LẠI USER TRƯỚC KHI CACHE (tránh race condition)
                    val currentUserCheck = auth?.currentUser
                    if (currentUserCheck?.phoneNumber != phone) {
                        Log.w(TAG, "User changed during preload, discarding result")
                        return
                    }
                    
                    for (roomSnapshot in snapshot.children) {
                        val tenantsSnapshot = roomSnapshot.child("tenants")

                        for (tenantSnapshot in tenantsSnapshot.children) {
                            val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                            if (phoneInTenant == phone) {
                                val roomNumber = roomSnapshot.key
                                if (roomNumber != null) {
                                    Log.d(TAG, "✅ Preloaded data for room: $roomNumber")
                                    com.app.buildingmanagement.data.SharedDataManager.setCachedData(
                                        roomSnapshot, roomNumber, phone
                                    )
                                    return
                                }
                            }
                        }
                    }
                    Log.w(TAG, "❌ Could not find user room during preload")
                }

                override fun onCancelled(error: DatabaseError) {
                    timeoutHandler.removeCallbacks(timeoutRunnable) // Cancel timeout
                    Log.e(TAG, "Error preloading user data: ${error.message}")
                }
            })
        } else {
            Log.w(TAG, "Cannot preload data - user phone is null")
        }
    }
}