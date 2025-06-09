package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.app.buildingmanagement.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        // Kiểm tra authentication trước
        if (auth.currentUser == null) {
            redirectToSignIn()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        // Setup status bar
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setupNavigation()
        setupBottomNavigation()
    }

    private fun redirectToSignIn() {
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.mainFragmentContainer) as NavHostFragment
        navController = navHostFragment.navController
    }

    private fun setupBottomNavigation() {
        binding?.bottomNavigation?.setupWithNavController(navController)
    }

    override fun onStart() {
        super.onStart()
        // Kiểm tra lại authentication khi activity start
        if (auth.currentUser == null) {
            redirectToSignIn()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}
