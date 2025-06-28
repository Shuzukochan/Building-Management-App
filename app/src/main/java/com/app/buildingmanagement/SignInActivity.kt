package com.app.buildingmanagement

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class SignInActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var verificationId: String
    private var codeSent = false
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var phoneNumber: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = Firebase.auth

        // Check if user is already signed in
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        setContent {
            BuildingManagementTheme {
                SignInScreen(
                    onSendOtp = { phone -> sendVerificationCode(phone) }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SignInScreen(onSendOtp: (String) -> Unit) {
        var phoneText by remember { mutableStateOf("") }
        var phoneError by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .systemBarsPadding()
        ) {
            // Logo Image - giống XML gốc (350dp x 280dp, marginTop 30dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sin_in_logo),
                    contentDescription = "Sign in logo",
                    modifier = Modifier.size(350.dp, 280.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Title "Đăng nhập" - giống XML gốc (marginTop 45dp, marginStart 20dp)
            Text(
                text = "Đăng nhập",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .padding(start = 20.dp, top = 45.dp)
            )

            // Message text - giống XML gốc (marginStart 20dp, marginTop 10dp)
            Text(
                text = "Nhập số điện thoại để nhận mã OTP",
                fontSize = 18.sp,
                color = Color.Black,
                modifier = Modifier
                    .padding(start = 20.dp, top = 10.dp, end = 20.dp)
            )

            // Input Container - giống XML gốc (marginTop 10dp, minHeight 100dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .heightIn(min = 100.dp)
            ) {
                // TextInputLayout - tái tạo thiết kế gốc với corner radius và styling
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { newValue ->
                        // Chỉ cho phép số và tối đa 10 ký tự giống XML gốc
                        if (newValue.all { it.isDigit() } && newValue.length <= 10) {
                            phoneText = newValue
                            phoneError = null
                        }
                    },
                    label = { Text("Số điện thoại") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = phoneError != null,
                    supportingText = phoneError?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp), // minHeight từ XML
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp), // corner radius giống XML
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6200EE), // boxStrokeColor
                        unfocusedBorderColor = Color(0xFFBDBDBD),
                        focusedLabelColor = Color(0xFF6200EE),
                        unfocusedLabelColor = Color(0xFF757575), // hintTextColor
                        cursorColor = Color(0xFF6200EE),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )
            }

            // Send OTP Button - giống MaterialButton trong XML (marginTop 0dp)
            Button(
                onClick = {
                    val validation = validatePhone(phoneText)
                    if (validation == null) {
                        isLoading = true
                        onSendOtp(phoneText)
                    } else {
                        phoneError = validation
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp) // button_height từ dimens
                    .padding(horizontal = 20.dp), // start_end_margin
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFBDBDBD)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Gửi mã OTP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    private fun validatePhone(phone: String): String? {
        return when {
            phone.trim().isEmpty() -> "Vui lòng nhập số điện thoại"
            !phone.trim().matches(Regex("^\\d{10}$")) -> "Vui lòng nhập số điện thoại hợp lệ (10 chữ số)"
            else -> null
        }
    }

    private fun sendVerificationCode(phone: String) {
        var formattedPhone = phone.trim()

        formattedPhone = if (formattedPhone.startsWith("0")) {
            formattedPhone.replaceFirst("0", "+84")
        } else if (!formattedPhone.startsWith("+")) {
            "+84$formattedPhone"
        } else {
            formattedPhone
        }

        phoneNumber = formattedPhone

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
                if (task.isSuccessful) {
                    showToast("Xác thực thành công")
                    goToMain()
                } else {
                    val message = if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        "Mã OTP không hợp lệ"
                    } else {
                        "Lỗi: ${task.exception?.message}"
                    }
                    showToast(message)
                }
            }
    }

    private fun goToMain() {
        com.app.buildingmanagement.data.FirebaseDataState.cleanup()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            showToast("Lỗi xác thực: ${e.message}")
        }

        override fun onCodeSent(verifyId: String, token: PhoneAuthProvider.ForceResendingToken) {
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