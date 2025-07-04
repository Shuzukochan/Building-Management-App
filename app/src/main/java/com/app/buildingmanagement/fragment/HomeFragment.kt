package com.app.buildingmanagement.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.data.FirebaseDataState
import com.app.buildingmanagement.firebase.FCMHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.app.buildingmanagement.fragment.ui.home.HeaderSection
import com.app.buildingmanagement.fragment.ui.home.UsageCards
import com.app.buildingmanagement.fragment.ui.home.MeterReadingSection
import com.app.buildingmanagement.fragment.ui.home.TipsSection
import com.app.buildingmanagement.fragment.ui.home.LoadingSkeleton
import com.app.buildingmanagement.fragment.ui.home.responsiveDimension
import com.app.buildingmanagement.fragment.ui.home.NotificationHistoryBottomSheet
import androidx.core.graphics.toColorInt
import androidx.core.content.edit

// Function độc lập cho navigation
@Composable
fun HomeScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    var hasCheckedNotificationPermission by remember { mutableStateOf(false) }
    
    // Notification UI state
    var showNotificationSheet by remember { mutableStateOf(false) }
    var hasUnread by remember { mutableStateOf(false) }

    // Tạo permission launcher trước khi cần sử dụng
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (activity != null) {
            handleNotificationPermissionResult(activity, isGranted)
        }
    }

    // LaunchedEffect để kiểm tra notification permission & unread khi load
    LaunchedEffect(Unit) {
        if (!hasCheckedNotificationPermission && activity != null) {
            hasCheckedNotificationPermission = true
            kotlinx.coroutines.delay(500)
            checkNotificationPermission(activity, notificationPermissionLauncher)
        }

        // Kiểm tra unread ban đầu
        checkUnreadNotifications { unread ->
            hasUnread = unread
        }
    }

    val dimen = responsiveDimension()
    val roomNumber = FirebaseDataState.roomNumber
    val electricUsed = FirebaseDataState.electricUsed
    val waterUsed = FirebaseDataState.waterUsed
    val electricReading = FirebaseDataState.electricReading
    val waterReading = FirebaseDataState.waterReading
    val isLoading = FirebaseDataState.isLoading

    if (isLoading) {
        LoadingSkeleton(dimen = dimen)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(dimen.mainPadding)
        ) {
            HeaderSection(
                roomNumber = roomNumber, 
                titleTextSize = dimen.titleTextSize, 
                subtitleTextSize = dimen.subtitleTextSize,
                onNotificationClick = { showNotificationSheet = true },
                hasUnread = hasUnread
            )
            Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp))
            UsageCards(
                electricUsed = electricUsed,
                waterUsed = waterUsed,
                cardMinHeight = dimen.cardMinHeight,
                cardPadding = dimen.cardPadding,
                usageValueTextSize = dimen.usageValueTextSize,
                usageLabelTextSize = dimen.usageLabelTextSize
            )
            Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXXL.dp))
            MeterReadingSection(
                electricReading = electricReading,
                waterReading = waterReading,
                sectionTitleTextSize = dimen.sectionTitleTextSize,
                titleMarginBottom = dimen.titleMarginBottom,
                readingCardPadding = dimen.readingCardPadding,
                readingValueTextSize = dimen.readingValueTextSize
            )
            Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXXL.dp))
            TipsSection(dimen.tipsCardPadding)
            Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp))
        }
    }
    
    // Notification Bottom Sheet
    if (showNotificationSheet) {
        NotificationHistoryBottomSheet(
            onDismiss = { showNotificationSheet = false },
            onUnreadChanged = { hasUnread = it }
        )
    }
}

private fun checkNotificationPermission(
    activity: androidx.activity.ComponentActivity,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val sharedPref = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasRequestedBefore = sharedPref.getBoolean("notification_permission_requested", false)

        val appSettings = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val userEnabledNotifications = appSettings.getBoolean("notifications_enabled", true)

        val currentPermission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )

        when {
            currentPermission == PackageManager.PERMISSION_GRANTED -> {
                if (userEnabledNotifications) {
                    val roomNumber = FirebaseDataState.roomNumber.removePrefix("Phòng ")
                    if (roomNumber != "--" && roomNumber.isNotEmpty()) {
                        FCMHelper.subscribeToUserBuildingTopics(roomNumber)
                    }
                }
            }
            hasRequestedBefore -> {
            }
            else -> {
                Handler(Looper.getMainLooper()).postDelayed({
                    showNotificationPermissionDialog(activity, notificationPermissionLauncher)
                }, 800)
            }
        }
    } else {
        val appSettings = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val userEnabledNotifications = appSettings.getBoolean("notifications_enabled", true)

        if (userEnabledNotifications) {
            val roomNumber = FirebaseDataState.roomNumber.removePrefix("Phòng ")
            if (roomNumber != "--" && roomNumber.isNotEmpty()) {
                FCMHelper.subscribeToUserBuildingTopics(roomNumber)
            }
        }
    }
}

private fun showNotificationPermissionDialog(
    activity: androidx.activity.ComponentActivity,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    try {
        val message = """
            Ứng dụng cần quyền thông báo để:
            
            • Thông báo thanh toán hóa đơn
            • Cập nhật thông tin từ ban quản lý  
            • Thông báo bảo trì hệ thống
            
            Bạn có muốn cho phép không?
        """.trimIndent()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Cho phép thông báo")
            .setMessage(message)
            .setPositiveButton("Cho phép") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Không") { _, _ ->
                handleNotificationPermissionDenied(activity)
            }
            .setCancelable(false)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor("#2196F3".toColorInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor("#9E9E9E".toColorInt())

    } catch (_: Exception) {
    }
}

private fun handleNotificationPermissionResult(activity: androidx.activity.ComponentActivity, isGranted: Boolean) {
    val appPrefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    appPrefs.edit { putBoolean("notification_permission_requested", true) }

    if (isGranted) {
        val appSettings = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appSettings.edit { putBoolean("notifications_enabled", true) }

        val roomNumber = FirebaseDataState.roomNumber.removePrefix("Phòng ")
        if (roomNumber != "--" && roomNumber.isNotEmpty()) {
            FCMHelper.subscribeToUserBuildingTopics(roomNumber)
        }

        Toast.makeText(activity, "Đã cho phép thông báo", Toast.LENGTH_SHORT).show()
    } else {
        handleNotificationPermissionDenied(activity)
    }
}

private fun handleNotificationPermissionDenied(activity: androidx.activity.ComponentActivity) {

    val appPrefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    appPrefs.edit { putBoolean("notification_permission_requested", true) }

    val appSettings = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    appSettings.edit { putBoolean("notifications_enabled", false) }

    Toast.makeText(activity, "Bạn có thể bật thông báo trong Cài đặt nếu muốn nhận thông tin từ ban quản lý", Toast.LENGTH_LONG).show()
}

private fun checkUnreadNotifications(callback: (Boolean) -> Unit) {
    val buildingId = FirebaseDataState.getCurrentBuildingId()
    val roomId = FirebaseDataState.getCurrentRoomId()
    if (buildingId == null || roomId == null) {
        callback(false)
        return
    }

    val ref = com.google.firebase.database.FirebaseDatabase.getInstance()
        .getReference("buildings")
        .child(buildingId)
        .child("rooms")
        .child(roomId)
        .child("notifications")

    ref.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
            var unread = false
            for (child in snapshot.children) {
                val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false
                if (!isRead) { unread = true; break }
            }
            callback(unread)
        }

        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
            callback(false)
        }
    })
}

class HomeFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeScreen()
            }
        }
    }
}
