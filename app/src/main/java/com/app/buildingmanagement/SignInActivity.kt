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
import com.google.firebase.FirebaseTooManyRequestsException
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
    private var verificationId: String? = null
    private var codeSent = false
    private var isAutoVerified = false
    private var isVerificationInProgress = false
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var phoneNumber: String = ""

    // Timeout handling
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null

    // Auth state check handler
    private var authCheckHandler: Handler? = null
    private var authCheckRunnable: Runnable? = null

    companion object {
        private const val TAG = "SignInActivity"
        private const val VERIFICATION_TIMEOUT = 60L
        private const val APP_TIMEOUT = 30000L // 30 seconds
        private const val AUTH_CHECK_DELAY = 2000L // 2 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        auth = Firebase.auth

        // Kiểm tra user đã đăng nhập
        checkAuthenticationState()

        binding?.btnSendOtp?.setOnClickListener {
            sendVerificationCode()
        }
    }

    private fun checkAuthenticationState() {
        if (auth.currentUser != null) {
            Log.d(TAG, "User already logged in")
            goToMain()
            return
        }
    }

    private fun sendVerificationCode() {
        resetVerificationState()

        var phone = binding?.textSignInPhone?.text.toString().trim()
        Log.d(TAG, "Starting verification for phone: $phone")

        if (TextUtils.isEmpty(phone)) {
            binding?.tilPhone?.error = "Vui lòng nhập số điện thoại"
            return
        }

        if (!phone.matches(Regex("^\\d{10}$"))) {
            binding?.tilPhone?.error = "Vui lòng nhập số điện thoại hợp lệ (10 chữ số)"
            return
        }

        binding?.tilPhone?.error = null

        phone = formatPhoneNumber(phone)
        phoneNumber = phone

        Log.d(TAG, "Formatted phone number: $phoneNumber")

        // Kiểm tra nếu là số test (thường bắt đầu bằng +84555 trong Firebase test)
        val isTestNumber = phone.contains("555") || phone.contains("123456789")
        Log.d(TAG, "Is test number: $isTestNumber")

        startVerification(isTestNumber)
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
        clearAuthCheck()
        Log.d(TAG, "Verification state reset")
    }

    private fun startVerification(isTestNumber: Boolean = false) {
        isVerificationInProgress = true
        showProgressBar()

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting verification at: $startTime")

        // Timeout ngắn hơn cho số test
        val timeoutMs = if (isTestNumber) 15000L else APP_TIMEOUT
        startTimeout(timeoutMs)

        // Bắt đầu auth check sau 2 giây cho số test
        if (isTestNumber) {
            startAuthCheck()
        }

        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(VERIFICATION_TIMEOUT, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()

            Log.d(TAG, "Calling PhoneAuthProvider.verifyPhoneNumber")
            PhoneAuthProvider.verifyPhoneNumber(options)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting verification: ${e.message}", e)
            handleVerificationError("Lỗi khởi tạo xác thực: ${e.message}")
        }
    }

    private fun startTimeout(timeoutMs: Long) {
        clearTimeout()
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            if (isVerificationInProgress && !isAutoVerified && !codeSent) {
                Log.e(TAG, "Verification timeout reached")

                // Kiểm tra auth state một lần nữa trước khi báo lỗi
                if (auth.currentUser != null) {
                    Log.d(TAG, "User authenticated during timeout - redirecting")
                    handleSuccessfulAuth()
                } else {
                    handleVerificationError("Quá thời gian chờ. Vui lòng thử lại.")
                }
            }
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMs)
    }

    private fun clearTimeout() {
        timeoutRunnable?.let {
            timeoutHandler?.removeCallbacks(it)
        }
        timeoutHandler = null
        timeoutRunnable = null
    }

    // Kiểm tra auth state định kỳ cho số test
    private fun startAuthCheck() {
        clearAuthCheck()
        authCheckHandler = Handler(Looper.getMainLooper())
        authCheckRunnable = object : Runnable {
            override fun run() {
                if (isVerificationInProgress && !isAutoVerified && !codeSent) {
                    Log.d(TAG, "Checking auth state...")
                    if (auth.currentUser != null) {
                        Log.d(TAG, "User authenticated via background check")
                        handleSuccessfulAuth()
                        return
                    }
                    // Kiểm tra lại sau 1 giây
                    authCheckHandler?.postDelayed(this, 1000)
                }
            }
        }
        authCheckHandler?.postDelayed(authCheckRunnable!!, AUTH_CHECK_DELAY)
    }

    private fun clearAuthCheck() {
        authCheckRunnable?.let {
            authCheckHandler?.removeCallbacks(it)
        }
        authCheckHandler = null
        authCheckRunnable = null
    }

    private fun handleVerificationError(message: String) {
        isVerificationInProgress = false
        hideProgressBar()
        clearTimeout()
        clearAuthCheck()
        showToast(this, message)
    }

    private fun handleSuccessfulAuth() {
        if (isFinishing) return

        isAutoVerified = true
        isVerificationInProgress = false
        clearTimeout()
        clearAuthCheck()
        hideProgressBar()

        showToast(this, "Đăng nhập thành công!")
        goToMain()
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "onVerificationCompleted at: $endTime")

            if (!isAutoVerified && !isFinishing) {
                isAutoVerified = true
                isVerificationInProgress = false
                clearTimeout()
                clearAuthCheck()
                hideProgressBar()
                signInWithPhoneAuthCredential(credential, isAutoLogin = true)
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            val endTime = System.currentTimeMillis()
            Log.e(TAG, "onVerificationFailed at: $endTime - ${e.message}", e)

            isVerificationInProgress = false
            clearTimeout()
            clearAuthCheck()
            hideProgressBar()

            val errorMessage = when (e) {
                is FirebaseAuthInvalidCredentialsException -> "Số điện thoại không hợp lệ"
                is FirebaseTooManyRequestsException -> "Quá nhiều yêu cầu. Vui lòng thử lại sau."
                else -> "Lỗi xác thực: ${e.localizedMessage}"
            }
            showToast(this@SignInActivity, errorMessage)
        }

        override fun onCodeSent(verifyId: String, token: PhoneAuthProvider.ForceResendingToken) {
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "onCodeSent at: $endTime - verificationId: $verifyId")

            verificationId = verifyId
            resendToken = token
            codeSent = true

            if (!isAutoVerified && !isFinishing) {
                isVerificationInProgress = false
                hideProgressBar()
                clearTimeout()
                clearAuthCheck()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isAutoVerified && !isFinishing) {
                        Log.d(TAG, "Moving to OTP screen")
                        goToOtpScreen()
                    } else {
                        Log.d(TAG, "Auto verification completed, skipping OTP screen")
                    }
                }, 500)
            }
        }
    }

    private fun goToOtpScreen() {
        val verifyId = verificationId
        val token = resendToken

        if (verifyId != null && token != null) {
            val intent = Intent(this@SignInActivity, OtpActivity::class.java).apply {
                putExtra("verificationId", verifyId)
                putExtra("resendToken", token)
                putExtra("phoneNumber", phoneNumber)
            }
            startActivity(intent)
        } else {
            Log.e(TAG, "Cannot navigate to OTP: missing verification data")
            showToast(this, "Lỗi hệ thống. Vui lòng thử lại.")
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential, isAutoLogin: Boolean = false) {
        Log.d(TAG, "Signing in with credential (auto: $isAutoLogin)")

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful || auth.currentUser != null) {
                    Log.d(TAG, "Sign in successful")
                    val message = if (isAutoLogin) {
                        "Đăng nhập tự động thành công"
                    } else {
                        "Xác thực thành công"
                    }
                    showToast(this, message)
                    goToMain()
                } else {
                    Log.e(TAG, "Sign in failed: ${task.exception?.message}")
                    resetVerificationState()

                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Mã OTP không hợp lệ"
                        else -> "Lỗi đăng nhập: ${task.exception?.localizedMessage}"
                    }
                    showToast(this, errorMessage)
                }
            }
    }

    private fun goToMain() {
        if (isFinishing) return

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onStart() {
        super.onStart()
        checkAuthenticationState()
    }

    override fun onResume() {
        super.onResume()
        checkAuthenticationState()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearTimeout()
        clearAuthCheck()
        binding = null
    }
}