package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
        resendToken = intent.getParcelableExtra("resendToken")!!
        phoneNumber = intent.getStringExtra("phoneNumber")!!

        Log.d("OTP", "Received verificationId: $verificationId")
        Log.d("OTP", "Received phoneNumber: $phoneNumber")

        addTextWatchers()
        resendOTPTimer()

        binding?.btnSubmitOtp?.setOnClickListener {
            if (!isAuthenticationInProgress) {
                val typedOTP = getOtpFromFields()
                Log.d("OTP", "User entered OTP: '$typedOTP' (length: ${typedOTP.length})")

                if (typedOTP.length == 6) {
                    val credential = PhoneAuthProvider.getCredential(verificationId, typedOTP)
                    showProgressBar()
                    isAuthenticationInProgress = true
                    signInWithPhoneAuthCredential(credential)
                } else {
                    Toast.makeText(this, "Vui lòng nhập đầy đủ mã OTP", Toast.LENGTH_SHORT).show()
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
        Log.d("OTP", "Resending verification code to: $phoneNumber")

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
                binding?.resendTextView?.text = "Gửi lại mã OTP sau: ${seconds}s"
            }

            override fun onFinish() {
                binding?.resendTextView?.text = "Gửi lại mã OTP"
                binding?.resendTextView?.isEnabled = true
                binding?.resendTextView?.setTextColor(ContextCompat.getColor(this@OtpActivity, R.color.textinput_border_focused))
            }
        }.start()
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        Log.d("OTP", "Attempting to sign in with credential")

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                Log.d("OTP", "Authentication task completed. Success: ${task.isSuccessful}")

                hideProgressBar()
                isAuthenticationInProgress = false

                if (task.isSuccessful) {
                    Log.d("OTP", "Sign in successful")
                    handleSuccessfulAuthentication()
                } else {
                    Log.e("OTP", "Sign in failed", task.exception)

                    // Kiểm tra xem user có được authenticate không bất chấp lỗi
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        Log.d("OTP", "User is authenticated despite task failure")
                        handleSuccessfulAuthentication()
                        return@addOnCompleteListener
                    }

                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> {
                            "Mã OTP không chính xác. Vui lòng kiểm tra lại."
                        }
                        else -> "Xác thực thất bại: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    clearOtpFields()
                    binding?.otpInput1?.requestFocus()
                }
            }
            .addOnSuccessListener {
                Log.d("OTP", "Authentication success listener triggered")
                // Đảm bảo navigate ngay cả khi success listener được gọi
                Handler(Looper.getMainLooper()).postDelayed({
                    if (auth.currentUser != null && !isFinishing) {
                        handleSuccessfulAuthentication()
                    }
                }, 500)
            }
    }

    private fun handleSuccessfulAuthentication() {
        if (isFinishing) return

        Log.d("OTP", "Handling successful authentication")
        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

        // Delay ngắn để đảm bảo Firebase state được cập nhật
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToMain()
        }, 200)
    }

    private fun navigateToMain() {
        if (isFinishing) return

        Log.d("OTP", "Navigating to main activity")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d("OTP", "Auto verification completed")
            if (!isAuthenticationInProgress) {
                isAuthenticationInProgress = true
                showProgressBar()
                signInWithPhoneAuthCredential(credential)
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("OTP", "Verification Failed: ${e.message}", e)
            hideProgressBar()
            isAuthenticationInProgress = false

            val errorMessage = when (e) {
                is FirebaseAuthInvalidCredentialsException -> "Số điện thoại không hợp lệ"
                is FirebaseTooManyRequestsException -> "Quá nhiều yêu cầu. Vui lòng thử lại sau."
                else -> "Lỗi xác thực: ${e.message}"
            }

            Toast.makeText(this@OtpActivity, errorMessage, Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(
            newVerificationId: String,
            newResendToken: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d("OTP", "New code sent, new verificationId: $newVerificationId")
            verificationId = newVerificationId
            resendToken = newResendToken
            Toast.makeText(this@OtpActivity, "Đã gửi lại mã OTP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getOtpFromFields(): String {
        val otp = (binding?.otpInput1?.text.toString() +
                binding?.otpInput2?.text.toString() +
                binding?.otpInput3?.text.toString() +
                binding?.otpInput4?.text.toString() +
                binding?.otpInput5?.text.toString() +
                binding?.otpInput6?.text.toString()).replace("\\s".toRegex(), "")

        Log.d("OTP", "Getting OTP: '$otp'")
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

            current?.setOnKeyListener { v, keyCode, event ->
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
        // Kiểm tra authentication state khi activity start
        if (auth.currentUser != null && !isAuthenticationInProgress) {
            Log.d("OTP", "User already authenticated in onStart")
            navigateToMain()
        }
    }

    override fun onResume() {
        super.onResume()
        // Kiểm tra lại authentication state
        if (auth.currentUser != null && !isAuthenticationInProgress) {
            Log.d("OTP", "User already authenticated in onResume")
            navigateToMain()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        binding = null
    }
}