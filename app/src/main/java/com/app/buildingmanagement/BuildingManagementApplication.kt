package com.app.buildingmanagement

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class BuildingManagementApplication : Application() {

    companion object {
        private const val TAG = "BMApplication"
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application starting - initializing Firebase App Check...")
        
        // Initialize Firebase first
        FirebaseApp.initializeApp(this)
        
        // Initialize Firebase App Check
        initializeFirebaseAppCheck()
    }

    private fun initializeFirebaseAppCheck() {
        try {
            Log.d(TAG, "Initializing Firebase App Check...")
            
            // CHỈ SỬ DỤNG CHO DEBUG/DEVELOPMENT
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Setting up App Check for DEBUG build")
                
                // Set debug token nếu có
                if (BuildConfig.FIREBASE_APPCHECK_DEBUG_TOKEN.isNotEmpty()) {
                    Log.d(TAG, "Using debug token: ${BuildConfig.FIREBASE_APPCHECK_DEBUG_TOKEN.take(10)}...")
                    System.setProperty("firebase.appcheck.debug.token", BuildConfig.FIREBASE_APPCHECK_DEBUG_TOKEN)
                } else {
                    Log.w(TAG, "Debug token is empty in BuildConfig")
                }
                
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "✅ Debug App Check provider installed successfully")
            } else {
                Log.d(TAG, "Production build - App Check should use Play Integrity or other provider")
                // Trong production, sử dụng Play Integrity hoặc SafetyNet
                // FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                //     PlayIntegrityAppCheckProviderFactory.getInstance()
                // )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing App Check: ${e.message}", e)
        }
    }
} 