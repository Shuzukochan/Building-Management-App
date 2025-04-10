// SignInActivity.kt
package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.app.buildingmanagement.databinding.ActivitySignInBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class SignInActivity : BaseActivity() {
    private var binding: ActivitySignInBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var verificationId: String
    private var codeSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        auth = Firebase.auth


        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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

        // Reset lỗi nếu có
        binding?.tilPhone?.error = null

        // Chuyển số 0 đầu thành +84
        if (phone.startsWith("0")) {
            phone = phone.replaceFirst("0", "+84")
        } else if (!phone.startsWith("+")) {
            phone = "+84$phone"
        }

        showProgressBar()

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(otpCallbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }



    private val otpCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
        }

        override fun onVerificationFailed(e: FirebaseException) {
            hideProgressBar()
            showToast(this@SignInActivity, "Lỗi xác thực: ${e.message}")
        }

        override fun onCodeSent(verifyId: String, token: PhoneAuthProvider.ForceResendingToken) {
            hideProgressBar()
            verificationId = verifyId
            codeSent = true
            val intent = Intent(this@SignInActivity, OtpActivity::class.java)
            intent.putExtra("verificationId", verificationId)
            startActivity(intent)
        }
    }

}
