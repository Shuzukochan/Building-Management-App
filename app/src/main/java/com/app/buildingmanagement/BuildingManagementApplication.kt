package com.app.buildingmanagement

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class BuildingManagementApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        FirebaseApp.initializeApp(this)
        
        initializeFirebaseAppCheck()
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