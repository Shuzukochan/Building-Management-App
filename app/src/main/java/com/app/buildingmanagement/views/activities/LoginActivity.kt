package com.app.buildingmanagement.views.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.app.buildingmanagement.MainActivity
import com.app.buildingmanagement.repositories.UserRepository
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme
import com.app.buildingmanagement.utils.ValidationUtils

/**
 * Login Activity
 * 
 * Handles user authentication including email/password login,
 * phone authentication, and account registration.
 */
class LoginActivity : ComponentActivity() {
    
    private val userRepository = UserRepository()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BuildingManagementTheme {
                LoginScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LoginScreen() {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isRegisterMode by remember { mutableStateOf(false) }
        
        val context = LocalContext.current
        
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Logo/Icon
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = "Building Management",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Building Management",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = if (isRegisterMode) "Tạo tài khoản mới" else "Đăng nhập vào hệ thống",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Email Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { 
                                email = it
                                errorMessage = null
                            },
                            label = { Text("Email") },
                            placeholder = { Text("Nhập email của bạn") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = "Email")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorMessage != null && errorMessage?.contains("email", ignoreCase = true) == true,
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { 
                                password = it
                                errorMessage = null
                            },
                            label = { Text("Mật khẩu") },
                            placeholder = { Text("Nhập mật khẩu") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = "Password")
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff 
                                                     else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" 
                                                           else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None 
                                                 else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorMessage != null && errorMessage?.contains("password", ignoreCase = true) == true,
                            singleLine = true
                        )
                        
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Login/Register Button
                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    handleAuthentication(
                                        email = email,
                                        password = password,
                                        isRegister = isRegisterMode,
                                        onLoading = { isLoading = it },
                                        onError = { errorMessage = it },
                                        onSuccess = {
                                            startActivity(Intent(context, MainActivity::class.java))
                                            finish()
                                        }
                                    )
                                }
                            },
                            enabled = !isLoading && email.isNotEmpty() && password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (isRegisterMode) "Đăng ký" else "Đăng nhập")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Switch between login and register
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRegisterMode) "Đã có tài khoản?" else "Chưa có tài khoản?",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            TextButton(
                                onClick = { 
                                    isRegisterMode = !isRegisterMode
                                    errorMessage = null
                                }
                            ) {
                                Text(if (isRegisterMode) "Đăng nhập" else "Đăng ký")
                            }
                        }
                        
                        if (!isRegisterMode) {
                            TextButton(
                                onClick = {
                                    lifecycleScope.launch {
                                        handleForgotPassword(email) { error ->
                                            errorMessage = error
                                        }
                                    }
                                }
                            ) {
                                Text("Quên mật khẩu?")
                            }
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun handleAuthentication(
        email: String,
        password: String,
        isRegister: Boolean,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit,
        onSuccess: () -> Unit
    ) {
        try {
            onLoading(true)
            
            // Validate input
            if (!ValidationUtils.isValidEmail(email)) {
                onError("Email không hợp lệ")
                return
            }
            
            if (!ValidationUtils.isValidPassword(password)) {
                onError("Mật khẩu phải có ít nhất 6 ký tự")
                return
            }
            
            val result = if (isRegister) {
                // Create new account
                val newUser = com.app.buildingmanagement.models.User(
                    email = email,
                    fullName = "", // Will be updated in profile
                    role = "tenant"
                )
                userRepository.createUserWithEmailAndPassword(email, password, newUser)
            } else {
                // Sign in
                userRepository.signInWithEmailAndPassword(email, password)
            }
            
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { exception ->
                    onError(getErrorMessage(exception))
                }
            )
            
        } catch (e: Exception) {
            onError("Có lỗi xảy ra: ${e.message}")
        } finally {
            onLoading(false)
        }
    }
    
    private suspend fun handleForgotPassword(
        email: String,
        onError: (String) -> Unit
    ) {
        try {
            if (!ValidationUtils.isValidEmail(email)) {
                onError("Vui lòng nhập email hợp lệ")
                return
            }
            
            val success = userRepository.resetPassword(email)
            if (success) {
                onError("Đã gửi email đặt lại mật khẩu")
            } else {
                onError("Không thể gửi email đặt lại mật khẩu")
            }
        } catch (e: Exception) {
            onError("Có lỗi xảy ra: ${e.message}")
        }
    }
    
    private fun getErrorMessage(exception: Throwable): String {
        return when (exception.message) {
            "The email address is badly formatted." -> "Email không đúng định dạng"
            "The password is invalid or the user does not have a password." -> "Mật khẩu không chính xác"
            "There is no user record corresponding to this identifier." -> "Tài khoản không tồn tại"
            "The email address is already in use by another account." -> "Email đã được sử dụng"
            "A network error has occurred." -> "Lỗi kết nối mạng"
            else -> exception.message ?: "Có lỗi xảy ra"
        }
    }
}