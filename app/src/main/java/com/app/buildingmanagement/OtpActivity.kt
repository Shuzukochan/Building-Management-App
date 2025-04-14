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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        auth = FirebaseAuth.getInstance()

        verificationId = intent.getStringExtra("verificationId") ?: ""
        resendToken = intent.getParcelableExtra("resendToken")!!
        phoneNumber = intent.getStringExtra("phoneNumber")!!

        addTextWatchers()
        resendOTPTimer()

        binding?.btnSubmitOtp?.setOnClickListener {
            val typedOTP = getOtpFromFields()
            if (typedOTP.length == 6) {
                val credential = PhoneAuthProvider.getCredential(verificationId, typedOTP)
                showProgressBar()
                signInWithPhoneAuthCredential(credential)
            } else {
                Toast.makeText(this, "Vui lòng nhập đầy đủ mã OTP", Toast.LENGTH_SHORT).show()
            }
        }

        binding?.resendTextView?.setOnClickListener {
            resendVerificationCode()
            resendOTPTimer()
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
        binding?.resendTextView?.visibility = View.VISIBLE
        binding?.resendTextView?.isEnabled = false
        binding?.resendTextView?.setTextColor(getColor(android.R.color.darker_gray))

        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding?.resendTextView?.text = "Gửi lại mã OTP sau: ${seconds}s"
            }

            override fun onFinish() {
                binding?.resendTextView?.text = "Gửi lại mã OTP"
                binding?.resendTextView?.isEnabled = true
                binding?.resendTextView?.setTextColor(getColor(android.R.color.holo_blue_dark))
            }
        }.start()
    }



    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                hideProgressBar()
                if (task.isSuccessful) {
                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.d("OTP", "Lỗi: ${task.exception}")
                    Toast.makeText(this, "Xác thực thất bại: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("OTP", "Verification Failed: ${e.message}", e)
            hideProgressBar()
            Toast.makeText(this@OtpActivity, "Lỗi xác thực: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        override fun onCodeSent(
            newVerificationId: String,
            newResendToken: PhoneAuthProvider.ForceResendingToken
        ) {
            verificationId = newVerificationId
            resendToken = newResendToken
        }
    }

    private fun getOtpFromFields(): String {
        return binding?.otpInput1?.text.toString() +
                binding?.otpInput2?.text.toString() +
                binding?.otpInput3?.text.toString() +
                binding?.otpInput4?.text.toString() +
                binding?.otpInput5?.text.toString() +
                binding?.otpInput6?.text.toString()
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
                    if (s?.length == 1) {
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


    inner class OtpTextWatcher(private val currentField: android.widget.EditText?, private val nextField: android.widget.EditText?, private val previousField: android.widget.EditText?) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            val text = s.toString()
            if (text.length == 1 && nextField != null) {
                nextField.requestFocus()
            } else if (text.isEmpty() && previousField != null) {
                previousField.requestFocus()
            }
        }
    }

}
