package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import androidx.core.view.WindowInsetsControllerCompat
import com.app.buildingmanagement.databinding.ActivitySignInBinding
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
    private var isAutoVerified = false
    private var isVerificationInProgress = false
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var phoneNumber: String

    // Timeout handler
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        auth = Firebase.auth

        // Kiểm tra user đã đăng nhập
        if (auth.currentUser != null) {
            Log.d("SIGNIN", "User already logged in")
            goToMain()
            return
        }

        binding?.btnSendOtp?.setOnClickListener {
            sendVerificationCode()
        }
    }

    private fun sendVerificationCode() {
        // Reset tất cả flags
        resetVerificationState()

        var phone = binding?.textSignInPhone?.text.toString().trim()
        Log.d("SIGNIN", "Starting verification for phone: $phone")

        // Validation
        if (TextUtils.isEmpty(phone)) {
            binding?.tilPhone?.error = "Vui lòng nhập số điện thoại"
            return
        }

        if (!phone.matches(Regex("^\\d{10}$"))) {
            binding?.tilPhone?.error = "Vui lòng nhập số điện thoại hợp lệ (10 chữ số)"
            return
        }

        binding?.tilPhone?.error = null

        // Format số điện thoại
        phone = formatPhoneNumber(phone)
        phoneNumber = phone

        Log.d("SIGNIN", "Formatted phone number: $phoneNumber")

        // Bắt đầu verification
        startVerification()
    }

    private fun formatPhoneNumber(phone: String): String {
        return when {
            phone.startsWith("0") -> phone.replaceFirst("0", "+84")
            !phone.startsWith("+") -> "+84$phone"
            else -> phone
        }
    }

    private fun resetVerificationState() {
        isAutoVerified = false
        codeSent = false
        isVerificationInProgress = false
        clearTimeout()
        Log.d("SIGNIN", "Verification state reset")
    }

    private fun startVerification() {
        isVerificationInProgress = true
        showProgressBar()

        // Set timeout để tránh loading vô hạn
        startTimeout()

        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(90L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()

            Log.d("SIGNIN", "Starting phone verification")
            PhoneAuthProvider.verifyPhoneNumber(options)

        } catch (e: Exception) {
            Log.e("SIGNIN", "Error starting verification: ${e.message}", e)
            handleVerificationError("Lỗi khởi tạo xác thực: ${e.message}")
        }
    }

    private fun startTimeout() {
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            if (isVerificationInProgress && !isAutoVerified && !codeSent) {
                Log.e("SIGNIN", "Verification timeout reached")
                handleVerificationError("Quá thời gian chờ. Vui lòng kiểm tra kết nối mạng và thử lại.")
            }
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, 95000) // 95 seconds
    }

    private fun clearTimeout() {
        timeoutRunnable?.let {
            timeoutHandler?.removeCallbacks(it)
        }
        timeoutHandler = null
        timeoutRunnable = null
    }

    private fun handleVerificationError(message: String) {
        isVerificationInProgress = false
        hideProgressBar()
        clearTimeout()
        showToast(this, message)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d("SIGNIN", "onVerificationCompleted - Auto verification successful")

            // Đánh dấu đã auto verify
            isAutoVerified = true
            isVerificationInProgress = false
            clearTimeout()

            // Ẩn progress bar và đăng nhập
            hideProgressBar()
            signInWithPhoneAuthCredential(credential, isAutoLogin = true)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("SIGNIN", "onVerificationFailed: ${e.message}", e)

            isVerificationInProgress = false
            clearTimeout()
            hideProgressBar()

            val errorMessage = when (e) {
                is FirebaseAuthInvalidCredentialsException -> "Số điện thoại không hợp lệ"
                else -> "Lỗi xác thực: ${e.message}"
            }

            showToast(this@SignInActivity, errorMessage)
        }

        override fun onCodeSent(verifyId: String, token: PhoneAuthProvider.ForceResendingToken) {
            Log.d("SIGNIN", "onCodeSent - verificationId: $verifyId, autoVerified: $isAutoVerified")

            // Lưu thông tin verification
            verificationId = verifyId
            resendToken = token
            codeSent = true

            // Nếu chưa auto verify thì chuyển sang OTP screen
            if (!isAutoVerified) {
                isVerificationInProgress = false
                hideProgressBar()
                clearTimeout()

                // Delay nhỏ để đảm bảo onVerificationCompleted không được gọi sau
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isAutoVerified) {
                        Log.d("SIGNIN", "Moving to OTP screen")
                        goToOtpScreen()
                    } else {
                        Log.d("SIGNIN", "Auto verification completed, skipping OTP screen")
                    }
                }, 500)
            }
        }
    }

    private fun goToOtpScreen() {
        val intent = Intent(this@SignInActivity, OtpActivity::class.java).apply {
            putExtra("verificationId", verificationId)
            putExtra("resendToken", resendToken)
            putExtra("phoneNumber", phoneNumber)
        }
        startActivity(intent)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential, isAutoLogin: Boolean = false) {
        Log.d("SIGNIN", "Signing in with credential (auto: $isAutoLogin)")

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("SIGNIN", "Sign in successful")
                    val message = if (isAutoLogin) {
                        "Đăng nhập tự động thành công"
                    } else {
                        "Xác thực thành công"
                    }
                    showToast(this, message)
                    goToMain()
                } else {
                    Log.e("SIGNIN", "Sign in failed: ${task.exception?.message}")

                    // Reset state nếu thất bại
                    resetVerificationState()

                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Mã OTP không hợp lệ"
                        else -> "Lỗi đăng nhập: ${task.exception?.message}"
                    }
                    showToast(this, errorMessage)
                }
            }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onStart() {
        super.onStart()
        // Kiểm tra lại user khi activity start
        if (auth.currentUser != null) {
            Log.d("SIGNIN", "User found in onStart, going to main")
            goToMain()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearTimeout()
        binding = null
    }
}
