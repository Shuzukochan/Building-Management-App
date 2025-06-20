package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        setupNavigation()
        setupBottomNavigation()
        preloadUserData()
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
            finish()
        }
    }

    private fun setupNavigation() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.mainFragmentContainer) as? NavHostFragment

            navHostFragment?.let {
                navController = it.navController
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun setupBottomNavigation() {
        try {
            val bottomNav = binding?.bottomNavigation
            val controller = navController

            if (bottomNav != null && controller != null) {
                bottomNav.setupWithNavController(controller)
            }
        } catch (e: Exception) {
            // Handle error silently
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
            binding = null
            navController = null
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
            val fcmPrefs = getSharedPreferences(fcmPrefs, MODE_PRIVATE)
            fcmPrefs.edit().clear().apply()

            val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
            appSettings.edit().clear().apply()

        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun cleanupFCMOnLogout() {
        try {
            FCMHelper.unsubscribeFromBuildingTopics(null)

            val sharedPref = getSharedPreferences(fcmPrefs, MODE_PRIVATE)
            sharedPref.edit()
                .remove(fcmTokenKey)
                .apply()

        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun preloadUserData() {
        val currentUser = auth?.currentUser
        val phone = currentUser?.phoneNumber ?: return

        com.app.buildingmanagement.data.SharedDataManager.checkAndClearIfUserChanged()
        
        val cachedSnapshot = com.app.buildingmanagement.data.SharedDataManager.getCachedRoomSnapshot()
        val cachedRoomNumber = com.app.buildingmanagement.data.SharedDataManager.getCachedRoomNumber()

        if (cachedSnapshot != null && cachedRoomNumber != null) {
            return
        }

        val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")

        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            // Timeout - continue without cache
        }
        timeoutHandler.postDelayed(timeoutRunnable, 10000)

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                
                val currentUserCheck = auth?.currentUser
                if (currentUserCheck?.phoneNumber != phone) {
                    return
                }
                
                for (roomSnapshot in snapshot.children) {
                    val tenantsSnapshot = roomSnapshot.child("tenants")

                    for (tenantSnapshot in tenantsSnapshot.children) {
                        val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInTenant == phone) {
                            val roomNumber = roomSnapshot.key
                            roomNumber?.let {
                                com.app.buildingmanagement.data.SharedDataManager.setCachedData(
                                    roomSnapshot, it, phone
                                )
                                return
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                // Handle error silently
            }
        })
    }
}