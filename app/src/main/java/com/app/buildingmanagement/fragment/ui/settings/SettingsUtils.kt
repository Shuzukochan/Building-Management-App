package com.app.buildingmanagement.fragment.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.app.buildingmanagement.MainActivity
import com.app.buildingmanagement.SignInActivity
import com.app.buildingmanagement.data.FirebaseDataState
import com.app.buildingmanagement.firebase.FCMHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// NOTIFICATION UTILS
// ============================================================================

fun handleNotificationToggle(
    context: Context,
    enabled: Boolean,
    permissionLauncher: ActivityResultLauncher<String>,
    updateState: (Boolean) -> Unit
) {
    if (enabled) {
        // Người dùng muốn BẬT thông báo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Đã có quyền -> bật thông báo và đăng ký topic
                    updateState(true)
                    val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    sharedPref.edit { putBoolean("notifications_enabled", true) }
                    val cleanRoomNumber = FirebaseDataState.roomNumber.replace("Phòng ", "")
                    FCMHelper.subscribeToUserBuildingTopics(cleanRoomNumber)
                    Toast.makeText(context, "Đã bật thông báo", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Chưa có quyền -> hiển thị dialog xin quyền
                    showNotificationPermissionDialog(context) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        } else {
            // Android < 13 không cần quyền POST_NOTIFICATIONS
            updateState(true)
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            sharedPref.edit { putBoolean("notifications_enabled", true) }
            val cleanRoomNumber = FirebaseDataState.roomNumber.replace("Phòng ", "")
            FCMHelper.subscribeToUserBuildingTopics(cleanRoomNumber)
            Toast.makeText(context, "Đã bật thông báo", Toast.LENGTH_SHORT).show()
        }
    } else {
        // Người dùng muốn TẮT thông báo -> hủy đăng ký topic
        updateState(false)
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPref.edit { putBoolean("notifications_enabled", false) }
        val cleanRoomNumber = FirebaseDataState.roomNumber.replace("Phòng ", "")
        FCMHelper.unsubscribeFromBuildingTopics(cleanRoomNumber)
        Toast.makeText(context, "Đã tắt thông báo", Toast.LENGTH_SHORT).show()
    }
}

fun showNotificationPermissionDialog(context: Context, onAccept: () -> Unit) {
    MaterialAlertDialogBuilder(context)
        .setTitle("Cho phép thông báo")
        .setMessage("Ứng dụng cần quyền thông báo để gửi cho bạn các thông tin quan trọng về thanh toán và dịch vụ.")
        .setPositiveButton("Cho phép") { _, _ -> onAccept() }
        .setNegativeButton("Từ chối", null)
        .show()
}

// ============================================================================
// LOGOUT UTILS
// ============================================================================

fun showLogoutConfirmation(context: Context, auth: FirebaseAuth, onNavigateBack: () -> Unit) {
    MaterialAlertDialogBuilder(context)
        .setTitle("Đăng xuất")
        .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản không?")
        .setPositiveButton("Đăng xuất") { _, _ ->
            performLogout(context, auth, onNavigateBack)
        }
        .setNegativeButton("Hủy", null)
        .show()
}

fun performLogout(context: Context, auth: FirebaseAuth, onNavigateBack: () -> Unit) {
    try {
        // Unsubscribe from notifications
        val cleanRoomNumber = FirebaseDataState.roomNumber.replace("Phòng ", "")
        FCMHelper.unsubscribeFromBuildingTopics(cleanRoomNumber)

        // Clear all preferences
        clearAllSharedPreferences(context)
        
        // Sign out from Firebase
        auth.signOut()
        
        // Navigate to sign in
        val intent = Intent(context, SignInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        
        // If context is Activity, finish it
        if (context is MainActivity) {
            context.finish()
        }
        
        onNavigateBack()
        
    } catch (_: Exception) {
        Toast.makeText(context, "Có lỗi xảy ra khi đăng xuất", Toast.LENGTH_SHORT).show()
    }
}

fun clearAllSharedPreferences(context: Context) {
    try {
        val appSettings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appSettings.edit { clear() }

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        appPrefs.edit { clear() }

        val fcmPrefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        fcmPrefs.edit { clear() }
    } catch (_: Exception) {
        // Handle error silently
    }
}

// ============================================================================
// FEEDBACK UTILS
// ============================================================================

fun submitFeedback(context: Context, feedback: String, isAnonymous: Boolean) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    val buildingId = FirebaseDataState.buildingId

    if (buildingId.isNullOrEmpty() && !isAnonymous) {
        Toast.makeText(context, "Không tìm thấy thông tin tòa nhà, không thể gửi góp ý.", Toast.LENGTH_SHORT).show()
        return
    }

    val feedbackData = if (isAnonymous) {
        hashMapOf(
            "roomNumber" to "anonymous",
            "phone" to "anonymous",
            "feedback" to feedback,
        )
    } else {
        hashMapOf(
            "roomNumber" to FirebaseDataState.roomNumber.replace("Phòng ", ""),
            "phone" to (user?.phoneNumber ?: "unknown"),
            "userName" to FirebaseDataState.userName,
            "feedback" to feedback,
        )
    }

    // Determine the database path
    val feedbackRef = if (isAnonymous) {
        val path = if (buildingId.isNullOrEmpty()) "service_feedbacks" else "buildings/$buildingId/service_feedbacks"
        FirebaseDatabase.getInstance().getReference(path)
    } else {
        FirebaseDatabase.getInstance().getReference("buildings").child(buildingId!!).child("service_feedbacks")
    }

    feedbackRef.child(timestamp).setValue(feedbackData)
        .addOnSuccessListener {
            val message = if (isAnonymous) {
                "Gửi góp ý ẩn danh thành công. Cảm ơn bạn!"
            } else {
                if (FirebaseDataState.userName != "--") {
                    "Cảm ơn ${FirebaseDataState.userName} đã góp ý!"
                } else {
                    "Gửi góp ý thành công. Cảm ơn bạn!"
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener {
            Toast.makeText(context, "Gửi góp ý thất bại, vui lòng thử lại.", Toast.LENGTH_SHORT).show()
        }
}

// ============================================================================
// FORMATTING UTILS
// ============================================================================

fun formatCurrency(amount: Long): String {
    @Suppress("DEPRECATION")
    return NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount) + " VNĐ"
}

fun formatTimestamp(timestamp: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(timestamp)
        date?.let { outputFormat.format(it) } ?: timestamp
    } catch (_: Exception) {
        timestamp
    }
}
