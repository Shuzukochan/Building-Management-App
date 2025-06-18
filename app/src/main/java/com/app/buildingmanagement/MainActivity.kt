package com.app.buildingmanagement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    // Null safety improvements
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

        // Initialize auth first
        auth = Firebase.auth

        // Critical: Check authentication before setting up UI
        if (!checkAuthenticationState()) {
            return // Exit early if not authenticated
        }

        // Setup UI only if authenticated
        setupUI()
        setupNavigation()
        setupBottomNavigation()

        // Initialize FCM (non-blocking)
        initializeFCMToken()

        updateNotificationChannelBasedOnSettings()

        // Handle notification intent if any
        handleNotificationIntent()
    }

    /**
     * Check if user is authenticated
     * @return true if authenticated, false otherwise
     */
    private fun checkAuthenticationState(): Boolean {
        val currentUser = auth?.currentUser
        if (currentUser == null) {
            Log.w(TAG, "User not authenticated, redirecting to SignIn")
            redirectToSignIn()
            return false
        }

        Log.d(TAG, "User authenticated: ${currentUser.phoneNumber}")
        return true
    }

    private fun setupUI() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        // Setup status bar
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
            // Subscribe to all residents topic immediately
            FCMHelper.subscribeToTopic("all_residents")
            Log.d(TAG, "Subscribed to all_residents topic")

            // Generate and save FCM token
            FCMHelper.getToken { token ->
                if (token != null) {
                    Log.d(TAG, "FCM Token generated: ${token.take(20)}...") // Log only first 20 chars for security
                    saveTokenToPrefs(token)
                } else {
                    Log.w(TAG, "Failed to generate FCM token")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FCM token", e)
        }
    }

    private fun saveTokenToPrefs(token: String) {
        try {
            val sharedPref = getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            val success = sharedPref.edit()
                .putString(FCM_TOKEN_KEY, token)
                .commit() // Use commit() instead of apply() to ensure it's saved immediately

            if (success) {
                Log.d(TAG, "FCM token saved to SharedPreferences")
            } else {
                Log.e(TAG, "Failed to save FCM token to SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving FCM token to preferences", e)
        }
    }

    private fun updateNotificationChannelBasedOnSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
            val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "fcm_default_channel"

            val importance = if (notificationsEnabled) {
                NotificationManager.IMPORTANCE_DEFAULT
            } else {
                NotificationManager.IMPORTANCE_NONE
            }

            val channel = NotificationChannel(
                channelId,
                "Building Management Notifications",
                importance
            ).apply {
                description = "Thông báo từ ban quản lý tòa nhà"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel refreshed on app start - enabled: $notificationsEnabled")
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
            // TODO: Implement navigation to maintenance screen
            // Example: navController?.navigate(R.id.maintenanceFragment)
            Log.d(TAG, "Navigate to maintenance screen - TODO: Implement")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to maintenance screen", e)
        }
    }

    private fun navigateToPaymentScreen() {
        try {
            // TODO: Implement navigation to payment screen
            // Example: navController?.navigate(R.id.paymentFragment)
            Log.d(TAG, "Navigate to payment screen - TODO: Implement")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to payment screen", e)
        }
    }

    private fun navigateToAnnouncementScreen() {
        try {
            // TODO: Implement navigation to announcement screen
            // Example: navController?.navigate(R.id.announcementFragment)
            Log.d(TAG, "Navigate to announcement screen - TODO: Implement")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to announcement screen", e)
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-check authentication when activity starts
        if (!checkAuthenticationState()) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        // Additional check on resume (in case user was signed out while app was in background)
        if (auth?.currentUser == null) {
            Log.w(TAG, "User signed out while app was in background")
            redirectToSignIn()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Clean up resources
            binding = null
            navController = null
            auth = null
            Log.d(TAG, "MainActivity destroyed and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup", e)
        }
    }

    /**
     * Method to be called when user logs out
     * Can be called from SettingsFragment or other components
     */
    fun onUserLogout() {
        try {
            val currentUser = auth?.currentUser
            if (currentUser != null) {
                val phone = currentUser.phoneNumber
                if (phone != null) {
                    Log.d(TAG, "Cleaning up FCM data for user: ${phone.take(10)}...") // Log partial phone for privacy
                    cleanupFCMOnLogout(phone)
                }
            }

            // Sign out from Firebase
            auth?.signOut()
            Log.d(TAG, "User signed out successfully")

            // Redirect to sign in
            redirectToSignIn()

        } catch (e: Exception) {
            Log.e(TAG, "Error during logout process", e)
            // Still try to redirect even if cleanup fails
            redirectToSignIn()
        }
    }

    private fun cleanupFCMOnLogout(phone: String) {
        try {
            // Unsubscribe from FCM topics
            FCMHelper.unsubscribeFromBuildingTopics(null) // Will unsubscribe from common topics

            // Clear FCM token from SharedPreferences
            val sharedPref = getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            sharedPref.edit()
                .remove(FCM_TOKEN_KEY)
                .apply()

            Log.d(TAG, "FCM cleanup completed for logout")

            // TODO: Remove FCM token from Firebase Database if needed
            // This would require knowing the room number

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up FCM data", e)
        }
    }

    /**
     * Get the current FCM token from SharedPreferences
     * @return FCM token or null if not found
     */
    fun getCurrentFCMToken(): String? {
        return try {
            val sharedPref = getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            sharedPref.getString(FCM_TOKEN_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token from preferences", e)
            null
        }
    }

    /**
     * Refresh FCM token if needed
     * Can be called from fragments
     */
    fun refreshFCMToken() {
        try {
            Log.d(TAG, "Refreshing FCM token...")
            initializeFCMToken()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing FCM token", e)
        }
    }
}