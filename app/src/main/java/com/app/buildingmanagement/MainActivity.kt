package com.app.buildingmanagement

import android.content.Intent
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
    private var binding: ActivityMainBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var navController: NavController

    companion object {
        private const val TAG = "MainActivity"
    }

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

        // Chỉ khởi tạo FCM token, không gửi lên server ngay
        initializeFCMToken()

        // Xử lý notification intent
        handleNotificationIntent()
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

    private fun initializeFCMToken() {
        // Chỉ tạo FCM token và lưu vào SharedPreferences
        FCMHelper.getToken { token ->
            if (token != null) {
                Log.d(TAG, "FCM Token generated: $token")
                // Lưu token để HomeFragment sử dụng
                saveTokenToPrefs(token)
            } else {
                Log.w(TAG, "Không thể lấy FCM token")
            }
        }
    }

    private fun saveTokenToPrefs(token: String) {
        val sharedPref = getSharedPreferences("fcm_prefs", MODE_PRIVATE)
        sharedPref.edit().putString("fcm_token", token).apply()
        Log.d(TAG, "FCM token saved to SharedPreferences")
    }

    private fun handleNotificationIntent() {
        intent.extras?.let { extras ->
            // Xử lý dữ liệu từ notification khi app được mở từ notification
            val notificationData = extras.getBundle("notification_data")
            notificationData?.let { data ->
                val type = data.getString("type")
                Log.d(TAG, "Mở app từ notification với type: $type")

                when (type) {
                    "maintenance_request" -> {
                        // Chuyển đến tab hoặc fragment yêu cầu bảo trì
                        navigateToMaintenanceScreen()
                    }
                    "payment_reminder" -> {
                        // Chuyển đến tab hoặc fragment thanh toán
                        navigateToPaymentScreen()
                    }
                    "announcement" -> {
                        // Chuyển đến tab thông báo
                        navigateToAnnouncementScreen()
                    }
                }
            }
        }
    }

    private fun navigateToMaintenanceScreen() {
        // TODO: Navigate đến maintenance fragment/screen
        // Ví dụ: navController.navigate(R.id.maintenanceFragment)
        Log.d(TAG, "Navigate to maintenance screen")
    }

    private fun navigateToPaymentScreen() {
        // TODO: Navigate đến payment fragment/screen
        // Ví dụ: navController.navigate(R.id.paymentFragment)
        Log.d(TAG, "Navigate to payment screen")
    }

    private fun navigateToAnnouncementScreen() {
        // TODO: Navigate đến announcement fragment/screen
        // Ví dụ: navController.navigate(R.id.announcementFragment)
        Log.d(TAG, "Navigate to announcement screen")
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
        // Hủy đăng ký FCM topics khi destroy activity (tùy chọn)
        // Thường không cần thiết vì user vẫn muốn nhận notification khi app không mở
        binding = null
    }

    // Method để gọi khi user logout
    fun onUserLogout() {
        auth.currentUser?.let { user ->
            val phone = user.phoneNumber
            if (phone != null) {
                // Hủy đăng ký FCM topics và xóa token khỏi database
                cleanupFCMOnLogout(phone)
            }
        }

        auth.signOut()
        redirectToSignIn()
    }

    private fun cleanupFCMOnLogout(phone: String) {
        // TODO: Implement cleanup FCM data when user logout
        Log.d(TAG, "Cleaning up FCM data for phone: $phone")
    }
}
