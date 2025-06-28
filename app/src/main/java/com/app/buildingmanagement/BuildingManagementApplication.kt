package com.app.buildingmanagement

import android.app.Application
import com.app.buildingmanagement.data.FirebaseDataState
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth

class BuildingManagementApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        FirebaseApp.initializeApp(this)
        
        initializeFirebaseAppCheck()
        
        // Preload FirebaseDataState nếu user đã đăng nhập
        initializeDataStateIfAuthenticated()
    }
    
    private fun initializeDataStateIfAuthenticated() {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                // Delay một chút để Firebase hoàn tất setup
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    FirebaseDataState.initialize(this@BuildingManagementApplication)
                }, 500)
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun initializeFirebaseAppCheck() {
        try {
            if (BuildConfig.DEBUG) {
                if (BuildConfig.FIREBASE_APPCHECK_DEBUG_TOKEN.isNotEmpty()) {
                    System.setProperty("firebase.appcheck.debug.token", BuildConfig.FIREBASE_APPCHECK_DEBUG_TOKEN)
                }
                
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }
} 