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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.app.buildingmanagement.navigation.AppBottomNavigation
import com.app.buildingmanagement.navigation.AppNavigationHost
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {

    private var auth: FirebaseAuth? = null
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            // Handle error silently
        }
    }

    private fun navigateToMaintenanceScreen() {
        try {
        } catch (_: Exception) {
            // Handle error silently
        }
    }

    private fun navigateToPaymentScreen() {
        try {
        } catch (_: Exception) {
            // Handle error silently
        }
    }

    private fun navigateToAnnouncementScreen() {
        try {
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            // Handle error silently
        }
    }



    private fun initializeFirebaseData() {
        try {
            // Initialize global Firebase data state
            com.app.buildingmanagement.data.FirebaseDataState.initialize(this)
        } catch (_: Exception) {
            // Handle error silently
        }
    }
}