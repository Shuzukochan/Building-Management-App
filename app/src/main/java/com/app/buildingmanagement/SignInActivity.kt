package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
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
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var phoneNumber: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        auth = Firebase.auth

        if (auth.currentUser != null) {
            goToMain()
        }

        binding?.btnSendOtp?.setOnClickListener {
            sendVerificationCode()
        }
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