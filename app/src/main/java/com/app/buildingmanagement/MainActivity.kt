package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        binding?.bottomNavigation?.let { bottomNav ->
            // Setup với NavController
            bottomNav.setupWithNavController(navController)

            // Tùy chỉnh style cho bottom navigation
            bottomNav.apply {
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                elevation = 8f

                // Tùy chỉnh màu sắc
                itemIconTintList = ContextCompat.getColorStateList(
                    this@MainActivity,
                    R.color.bottom_nav_color_selector
                )
                itemTextColor = ContextCompat.getColorStateList(
                    this@MainActivity,
                    R.color.bottom_nav_color_selector
                )

                // Animation khi chuyển tab
                itemRippleColor = ContextCompat.getColorStateList(
                    this@MainActivity,
                    R.color.ripple_color
                )
            }

            // Xử lý reselect tab (tap vào tab đang active)
            bottomNav.setOnItemReselectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        // Có thể scroll to top hoặc refresh data
                        // Ví dụ: gửi event đến HomeFragment
                    }
                    R.id.nav_chart -> {
                        // Refresh chart data
                    }
                    R.id.nav_payment -> {
                        // Refresh payment data
                    }
                    R.id.nav_settings -> {
                        // Không cần action đặc biệt
                    }
                }
            }
        }
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

    // Method để các fragment có thể gọi khi cần
    fun showBottomNavigation() {
        binding?.bottomNavigation?.animate()?.translationY(0f)?.duration = 300
    }

    fun hideBottomNavigation() {
        binding?.bottomNavigation?.let { bottomNav ->
            bottomNav.animate().translationY(bottomNav.height.toFloat()).duration = 300
        }
    }

    // Method để set badge cho notification (nếu cần)
    fun setBadge(menuItemId: Int, count: Int) {
        binding?.bottomNavigation?.let { bottomNav ->
            val badge = bottomNav.getOrCreateBadge(menuItemId)
            if (count > 0) {
                badge.isVisible = true
                badge.number = count
                badge.backgroundColor = ContextCompat.getColor(this, R.color.error_color)
                badge.badgeTextColor = ContextCompat.getColor(this, R.color.white)
            } else {
                badge.isVisible = false
            }
        }
    }

    // Method để clear tất cả badges
    fun clearAllBadges() {
        binding?.bottomNavigation?.let { bottomNav ->
            bottomNav.removeBadge(R.id.nav_home)
            bottomNav.removeBadge(R.id.nav_chart)
            bottomNav.removeBadge(R.id.nav_payment)
            bottomNav.removeBadge(R.id.nav_settings)
        }
    }
}
