package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import com.app.buildingmanagement.databinding.ActivityOtpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider

class OtpActivity : BaseActivity() {
    private var binding: ActivityOtpBinding? = null
    private lateinit var verificationId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        verificationId = intent.getStringExtra("verificationId") ?: ""

        setupOtpInput()

        binding?.btnSubmitOtp?.setOnClickListener {
            val otp = getOtpFromFields()

            if (otp.length == 6) {
                verifyOtp(otp)
            } else {
                Toast.makeText(this, "Vui lòng nhập đầy đủ mã OTP.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOtpInput() {
        binding?.otpInput1?.addTextChangedListener(createTextWatcher(binding?.otpInput1, binding?.otpInput2))
        binding?.otpInput2?.addTextChangedListener(createTextWatcher(binding?.otpInput2, binding?.otpInput3))
        binding?.otpInput3?.addTextChangedListener(createTextWatcher(binding?.otpInput3, binding?.otpInput4))
        binding?.otpInput4?.addTextChangedListener(createTextWatcher(binding?.otpInput4, binding?.otpInput5))
        binding?.otpInput5?.addTextChangedListener(createTextWatcher(binding?.otpInput5, binding?.otpInput6))
        binding?.otpInput6?.addTextChangedListener(createTextWatcher(binding?.otpInput6, null))
    }

    private fun createTextWatcher(currentField: android.widget.EditText?, nextField: android.widget.EditText?): android.text.TextWatcher {
        return object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s?.length == 1) {
                    nextField?.requestFocus()  // Nếu có ký tự, focus sang ô tiếp theo
                } else if (s?.length == 0) {
                    // Nếu ô trống và người dùng nhấn xóa, focus sẽ trở về ô trước
                    if (currentField != binding?.otpInput1) {
                        currentField?.clearFocus()
                        focusPreviousField(currentField)
                    }
                }
                checkOtpComplete()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
    }

    private fun focusPreviousField(currentField: android.widget.EditText?) {
        when (currentField) {
            binding?.otpInput2 -> {
                binding?.otpInput1?.requestFocus()
            }
            binding?.otpInput3 -> {
                binding?.otpInput2?.requestFocus()
            }
            binding?.otpInput4 -> {
                binding?.otpInput3?.requestFocus()
            }
            binding?.otpInput5 -> {
                binding?.otpInput4?.requestFocus()
            }
            binding?.otpInput6 -> {
                binding?.otpInput5?.requestFocus()
            }
        }
    }

    private fun checkOtpComplete() {
        val otp = getOtpFromFields()
        if (otp.length == 6) {
            verifyOtp(otp)
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

    private fun verifyOtp(otp: String) {
        showProgressBar()
        if (TextUtils.isEmpty(verificationId)) {
            Toast.makeText(this, "Lỗi mã xác thực. Vui lòng thử lại.", Toast.LENGTH_SHORT).show()
            hideProgressBar()
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId, otp)

        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                hideProgressBar()
                if (task.isSuccessful) {
                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Lỗi đăng nhập: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}