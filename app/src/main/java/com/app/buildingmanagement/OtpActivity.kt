package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.app.buildingmanagement.databinding.ActivityOtpBinding
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class OtpActivity : BaseActivity() {
    private var binding: ActivityOtpBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var verificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var phoneNumber: String

    private var countDownTimer: CountDownTimer? = null
    private var isAuthenticationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        auth = FirebaseAuth.getInstance()

        verificationId = intent.getStringExtra("verificationId") ?: ""
        @Suppress("DEPRECATION")
        resendToken = intent.getParcelableExtra("resendToken")!!
        phoneNumber = intent.getStringExtra("phoneNumber")!!

        addTextWatchers()
        resendOTPTimer()

        binding?.btnSubmitOtp?.setOnClickListener {
            if (!isAuthenticationInProgress) {
                val typedOTP = getOtpFromFields()

                if (typedOTP.length == 6) {
                    val credential = PhoneAuthProvider.getCredential(verificationId, typedOTP)
                    showProgressBar()
                    isAuthenticationInProgress = true
                    signInWithPhoneAuthCredential(credential)
                } else {
                    Toast.makeText(this, getString(R.string.otp_enter_complete), Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding?.resendTextView?.setOnClickListener {
            if (binding?.resendTextView?.isEnabled == true) {
                resendVerificationCode()
                resendOTPTimer()
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            binding?.otpInput1?.requestFocus()
        }, 100)
    }

    private fun resendVerificationCode() {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setForceResendingToken(resendToken)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun resendOTPTimer() {
        clearOtpFields()

        countDownTimer?.cancel()

        binding?.resendTextView?.visibility = View.VISIBLE
        binding?.resendTextView?.isEnabled = false
        binding?.resendTextView?.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected))

        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding?.resendTextView?.text = getString(R.string.otp_resend_timer, seconds)
            }

            override fun onFinish() {
                binding?.resendTextView?.text = getString(R.string.otp_resend_button)
                binding?.resendTextView?.isEnabled = true
                binding?.resendTextView?.setTextColor(ContextCompat.getColor(this@OtpActivity, R.color.textinput_border_focused))
            }
        }.start()
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                hideProgressBar()
                isAuthenticationInProgress = false

                if (task.isSuccessful) {
                    handleSuccessfulAuthentication()
                } else {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        handleSuccessfulAuthentication()
                        return@addOnCompleteListener
                    }

                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> {
                            getString(R.string.otp_invalid_code)
                        }
                        else -> getString(R.string.otp_auth_failed, task.exception?.message ?: "")
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    clearOtpFields()
                    binding?.otpInput1?.requestFocus()
                }
            }
            .addOnSuccessListener {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (auth.currentUser != null && !isFinishing) {
                        handleSuccessfulAuthentication()
                    }
                }, 500)
            }
    }

    private fun handleSuccessfulAuthentication() {
        if (isFinishing) return

        Toast.makeText(this, getString(R.string.otp_login_success), Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToMain()
        }, 200)
    }

    private fun navigateToMain() {
        if (isFinishing) return

        com.app.buildingmanagement.data.SharedDataManager.clearCache()
        
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            if (!isAuthenticationInProgress) {
                isAuthenticationInProgress = true
                showProgressBar()
                signInWithPhoneAuthCredential(credential)
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            hideProgressBar()
            isAuthenticationInProgress = false

            val errorMessage = when (e) {
                is FirebaseAuthInvalidCredentialsException -> getString(R.string.otp_phone_invalid)
                is FirebaseTooManyRequestsException -> getString(R.string.otp_too_many_requests)
                else -> getString(R.string.otp_verification_error, e.message ?: "")
            }

            Toast.makeText(this@OtpActivity, errorMessage, Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(
            newVerificationId: String,
            newResendToken: PhoneAuthProvider.ForceResendingToken
        ) {
            verificationId = newVerificationId
            resendToken = newResendToken
            Toast.makeText(this@OtpActivity, getString(R.string.otp_resent), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getOtpFromFields(): String {
        val otp = (binding?.otpInput1?.text.toString() +
                binding?.otpInput2?.text.toString() +
                binding?.otpInput3?.text.toString() +
                binding?.otpInput4?.text.toString() +
                binding?.otpInput5?.text.toString() +
                binding?.otpInput6?.text.toString()).replace("\\s".toRegex(), "")

        return otp
    }

    private fun clearOtpFields() {
        binding?.otpInput1?.setText("")
        binding?.otpInput2?.setText("")
        binding?.otpInput3?.setText("")
        binding?.otpInput4?.setText("")
        binding?.otpInput5?.setText("")
        binding?.otpInput6?.setText("")
    }

    private fun addTextWatchers() {
        val inputs = listOf(
            binding?.otpInput1,
            binding?.otpInput2,
            binding?.otpInput3,
            binding?.otpInput4,
            binding?.otpInput5,
            binding?.otpInput6
        )

        for (i in inputs.indices) {
            val current = inputs[i]
            val next = if (i < inputs.size - 1) inputs[i + 1] else null
            val prev = if (i > 0) inputs[i - 1] else null

            current?.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (text.length == 1) {
                        next?.requestFocus()
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            current?.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                    if (current.text.isNullOrEmpty()) {
                        prev?.apply {
                            setText("")
                            requestFocus()
                        }
                    }
                }
                false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null && !isAuthenticationInProgress) {
            navigateToMain()
        }
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser != null && !isAuthenticationInProgress) {
            navigateToMain()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        binding = null
    }
}