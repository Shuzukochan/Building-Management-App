package com.app.buildingmanagement.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.app.buildingmanagement.BuildConfig

/**
 * Firebase Configuration Manager
 * 
 * Centralized configuration for Firebase services used throughout the app.
 * Handles initialization of Firebase Auth, Realtime Database, and App Check.
 */
object FirebaseConfig {
    
    private var _auth: FirebaseAuth? = null
    private var _database: FirebaseDatabase? = null
    private var _appCheck: FirebaseAppCheck? = null
    
    /**
     * Get Firebase Auth instance
     */
    val auth: FirebaseAuth
        get() = _auth ?: FirebaseAuth.getInstance().also { _auth = it }
    
    /**
     * Get Firebase Database instance
     */
    val database: FirebaseDatabase
        get() = _database ?: FirebaseDatabase.getInstance().also { 
            _database = it
            it.setPersistenceEnabled(true)
        }
    
    /**
     * Get Firebase App Check instance
     */
    val appCheck: FirebaseAppCheck
        get() = _appCheck ?: FirebaseAppCheck.getInstance().also { _appCheck = it }
    
    /**
     * Initialize Firebase services
     * 
     * @param context Application context
     */
    fun initialize(context: Context) {
        try {
            // Initialize Firebase App if not already done
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            
            // Initialize Firebase App Check for security
            initializeAppCheck()
            
            // Initialize other Firebase services
            initializeAuth()
            initializeDatabase()
            
        } catch (e: Exception) {
            // Log error but don't crash the app
            e.printStackTrace()
        }
    }
    
    /**
     * Initialize Firebase App Check
     */
    private fun initializeAppCheck() {
        try {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            
            // Use debug provider in debug builds
            if (BuildConfig.DEBUG && BuildConfig.FIREBASE_APPCHECK_DEBUG_TOKEN.isNotEmpty()) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            }
            
            _appCheck = firebaseAppCheck
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Initialize Firebase Authentication
     */
    private fun initializeAuth() {
        try {
            _auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Initialize Firebase Realtime Database
     */
    private fun initializeDatabase() {
        try {
            val database = FirebaseDatabase.getInstance()
            database.setPersistenceEnabled(true)
            _database = database
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Sign out current user
     */
    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Cleanup Firebase resources
     */
    fun cleanup() {
        try {
            _auth = null
            _database = null
            _appCheck = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Database reference paths
     */
    object DatabasePaths {
        const val USERS = "users"
        const val ROOMS = "rooms"
        const val PAYMENTS = "payments"
        const val STATISTICS = "statistics"
        const val NOTIFICATIONS = "notifications"
    }
    
    /**
     * Authentication providers
     */
    object AuthProviders {
        const val EMAIL_PASSWORD = "password"
        const val GOOGLE = "google.com"
        const val PHONE = "phone"
    }
}