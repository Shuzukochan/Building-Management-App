package com.app.buildingmanagement

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.app.buildingmanagement.databinding.ActivitySignInBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class SignInActivity : BaseActivity() {
    private var binding: ActivitySignInBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var verificationId: String
    private var codeSent = false
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var phoneNumber: String

    // Notification permission
    private var hasRequestedNotificationPermission = false

    companion object {
        private const val NOTIFICATION_PERMISSION_PREF = "notification_permission_requested"
    }

    // Permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handleNotificationPermissionResult(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        auth = Firebase.auth

        // Kiểm tra quyền thông báo trước khi check authentication
        checkNotificationPermission()

        if (auth.currentUser != null) {
            goToMain()
        }

        binding?.btnSendOtp?.setOnClickListener {
            sendVerificationCode()
        }
    }

    private fun checkNotificationPermission() {
        // Chỉ cần check từ Android 13 (API 33) trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val hasRequestedBefore = sharedPref.getBoolean(NOTIFICATION_PERMISSION_PREF, false)

            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Đã có quyền
                    Log.d("SignInActivity", "Notification permission already granted")
                }
                hasRequestedBefore -> {
                    // Đã hỏi trước đó và bị từ chối, không hỏi lại nữa
                    Log.d("SignInActivity", "Notification permission was denied before, not asking again")
                }
                else -> {
                    // Chưa hỏi bao giờ, hiển thị dialog giải thích
                    showNotificationPermissionDialog()
                }
            }
        } else {
            Log.d("SignInActivity", "Android version < 13, notification permission not required")
        }
    }

    private fun showNotificationPermissionDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Cho phép thông báo")
            .setMessage("Ứng dụng cần quyền thông báo để:\n\n" +
                    "• Thông báo thanh toán hóa đơn\n" +
                    "• Cập nhật thông tin từ ban quản lý\n" +
                    "• Thông báo bảo trì hệ thống\n\n" +
                    "Bạn có muốn cho phép không?")
            .setPositiveButton("Cho phép") { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton("Không") { _, _ ->
                handleNotificationPermissionDenied()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Đảm bảo màu nút hiển thị rõ ràng
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, R.color.nav_selected))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasRequestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleNotificationPermissionResult(isGranted: Boolean) {
        // Lưu trạng thái đã hỏi quyền vào app_prefs
        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        appPrefs.edit().putBoolean(NOTIFICATION_PERMISSION_PREF, true).apply()

        if (isGranted) {
            Log.d("SignInActivity", "Notification permission granted")

            // LƯU VÀO CÙNG SHARED PREFS VỚI SETTINGS FRAGMENT
            val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
            appSettings.edit().putBoolean("notifications_enabled", true).apply()

            showToast(this, "Đã cho phép thông báo")
        } else {
            Log.d("SignInActivity", "Notification permission denied")
            handleNotificationPermissionDenied()
        }
    }

    private fun handleNotificationPermissionDenied() {
        // Lưu trạng thái đã hỏi quyền vào app_prefs
        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        appPrefs.edit().putBoolean(NOTIFICATION_PERMISSION_PREF, true).apply()

        // LƯU VÀO CÙNG SHARED PREFS VỚI SETTINGS FRAGMENT
        val appSettings = getSharedPreferences("app_settings", MODE_PRIVATE)
        appSettings.edit().putBoolean("notifications_enabled", false).apply()

        showToast(this, "Bạn có thể bật thông báo trong Cài đặt nếu muốn nhận thông tin từ ban quản lý")
    }

    private fun sendVerificationCode() {
        var phone = binding?.textSignInPhone?.text.toString().trim()

        if (TextUtils.isEmpty(phone)) {
            binding?.tilPhone?.error = "Vui lòng nhập số điện thoại"
            return
        }

        if (!phone.matches(Regex("^\\d{10}$"))) {
            binding?.tilPhone?.error = "Vui lòng nhập số điện thoại hợp lệ (10 chữ số)"
            return
        }

        binding?.tilPhone?.error = null

        // Chuyển đầu số
        phone = if (phone.startsWith("0")) {
            phone.replaceFirst("0", "+84")
        } else if (!phone.startsWith("+")) {
            "+84$phone"
        } else {
            phone
        }

        phoneNumber = phone

        showProgressBar()

        //FirebaseAuth.getInstance().firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        //auth.firebaseAuthSettings.forceRecaptchaFlowForTesting(true) //Force dùng recapcha

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(30L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                hideProgressBar()
                if (task.isSuccessful) {
                    showToast(this, "Xác thực thành công")
                    goToMain()
                } else {
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        showToast(this, "Mã OTP không hợp lệ")
                    } else {
                        showToast(this, "Lỗi: ${task.exception?.message}")
                    }
                    Log.d("SIGNIN", "signInWithPhoneAuthCredential: ${task.exception}")
                }
            }
    }

    private fun goToMain() {
        // CLEAR CACHE KHI LOGIN ĐỂ TRÁNH CONFLICT GIỮA CÁC USER
        Log.d("SignInActivity", "Clearing cache before going to MainActivity")
        com.app.buildingmanagement.data.SharedDataManager.clearCache()
        
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            hideProgressBar()
            Log.e("SIGNIN", "onVerificationFailed: ${e.message}", e)
            showToast(this@SignInActivity, "Lỗi xác thực: ${e.message}")
        }

        override fun onCodeSent(verifyId: String, token: PhoneAuthProvider.ForceResendingToken) {
            hideProgressBar()
            verificationId = verifyId
            resendToken = token
            codeSent = true

            val intent = Intent(this@SignInActivity, OtpActivity::class.java)
            intent.putExtra("verificationId", verificationId)
            intent.putExtra("resendToken", resendToken)
            intent.putExtra("phoneNumber", phoneNumber)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            goToMain()
        }
    }
}