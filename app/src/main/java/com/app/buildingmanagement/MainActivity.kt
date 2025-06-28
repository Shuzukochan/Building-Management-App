package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.app.buildingmanagement.firebase.FCMHelper
import com.app.buildingmanagement.navigation.AppBottomNavigation
import com.app.buildingmanagement.navigation.AppNavigationHost
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {

    private var auth: FirebaseAuth? = null

    private val fcmPrefs = "fcm_prefs"
    private val fcmTokenKey = "fcm_token"
    private val notificationTypeMaintenance = "maintenance_request"
    private val notificationTypePayment = "payment_reminder"
    private val notificationTypeAnnouncement = "announcement"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth

        if (!checkAuthenticationState()) {
            return
        }

        setupUI()
        initializeFirebaseData()
        handleNotificationIntent()
    }

    private fun checkAuthenticationState(): Boolean {
        val currentUser = auth?.currentUser
        
        if (currentUser == null) {
            redirectToSignIn()
            return false
        }

        return true
    }

    private fun setupUI() {
        enableEdgeToEdge()
        setContent {
            BuildingManagementTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { AppBottomNavigation(navController = navController) }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigationHost(navController = navController)
                    }
                }
            }
        }
    }

    private fun redirectToSignIn() {
        try {
            val intent = Intent(this, SignInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            finish()
        }
    }

    private fun handleNotificationIntent() {
        try {
            val extras = intent.extras ?: return

            val notificationData = extras.getBundle("notification_data") ?: return

            val type = notificationData.getString("type")

            when (type) {
                notificationTypeMaintenance -> {
                    navigateToMaintenanceScreen()
                }
                notificationTypePayment -> {
                    navigateToPaymentScreen()
                }
                notificationTypeAnnouncement -> {
                    navigateToAnnouncementScreen()
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun navigateToMaintenanceScreen() {
        try {
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun navigateToPaymentScreen() {
        try {
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun navigateToAnnouncementScreen() {
        try {
        } catch (e: Exception) {
            // Handle error silently
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
            redirectToSignIn()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Cleanup Firebase data state
            com.app.buildingmanagement.data.FirebaseDataState.cleanup()

            auth = null
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    fun onUserLogout() {
        try {
            val currentUser = auth?.currentUser
            currentUser?.phoneNumber?.let {
                cleanupFCMOnLogout()
            }

            clearAllMainActivityPreferences()
            auth?.signOut()
            redirectToSignIn()

        } catch (e: Exception) {
            redirectToSignIn()
        }
    }

    private fun clearAllMainActivityPreferences() {
        try {
            val fcmPrefs = getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            fcmPrefs.edit().clear().apply()

            val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
            appSettings.edit().clear().apply()

        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun cleanupFCMOnLogout() {
        try {
            // Lấy room number và loại bỏ prefix "Phòng " để đảm bảo topic name khớp
            val cleanRoomNumber = com.app.buildingmanagement.data.FirebaseDataState.roomNumber.replace("Phòng ", "")
            FCMHelper.unsubscribeFromBuildingTopics(cleanRoomNumber)

            val sharedPref = getSharedPreferences(fcmPrefs, MODE_PRIVATE)
            sharedPref.edit()
                .remove(fcmTokenKey)
                .apply()

        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun initializeFirebaseData() {
        try {
            // Initialize global Firebase data state
            com.app.buildingmanagement.data.FirebaseDataState.initialize(this)
        } catch (e: Exception) {
            // Handle error silently
        }
    }
}