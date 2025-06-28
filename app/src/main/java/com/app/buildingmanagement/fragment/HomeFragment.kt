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

// Function độc lập cho navigation
@Composable
fun HomeScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    var hasCheckedNotificationPermission by remember { mutableStateOf(false) }

    // Tạo permission launcher trước khi cần sử dụng
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (activity != null) {
            android.util.Log.d("HomeScreen", "Permission result: $isGranted")
            handleNotificationPermissionResult(activity, isGranted)
        }
    }

    // LaunchedEffect để kiểm tra notification permission khi screen load
    LaunchedEffect(Unit) {
        android.util.Log.d("HomeScreen", "LaunchedEffect started")
        if (!hasCheckedNotificationPermission && activity != null) {
            hasCheckedNotificationPermission = true
            android.util.Log.d("HomeScreen", "Checking notification permission")

            // Delay 1 giây để UI ổn định
            kotlinx.coroutines.delay(1000)
            checkNotificationPermission(activity, notificationPermissionLauncher)
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
            HeaderSection(roomNumber = roomNumber, titleTextSize = dimen.titleTextSize, subtitleTextSize = dimen.subtitleTextSize)
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
                cardMarginBottom = dimen.cardMarginBottom,
                readingCardPadding = dimen.readingCardPadding,
                readingValueTextSize = dimen.readingValueTextSize
            )
            Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXXL.dp))
            TipsSection(dimen.tipsCardPadding)
            Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp))
        }
    }
}

private fun checkNotificationPermission(
    activity: androidx.activity.ComponentActivity,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    android.util.Log.d("HomeScreen", "checkNotificationPermission called")
    android.util.Log.d("HomeScreen", "Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}")
    android.util.Log.d("HomeScreen", "TIRAMISU: ${Build.VERSION_CODES.TIRAMISU}")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val sharedPref = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasRequestedBefore = sharedPref.getBoolean("notification_permission_requested", false)

        val appSettings = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val userEnabledNotifications = appSettings.getBoolean("notifications_enabled", true)

        val currentPermission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )

        android.util.Log.d("HomeScreen", "hasRequestedBefore: $hasRequestedBefore")
        android.util.Log.d("HomeScreen", "userEnabledNotifications: $userEnabledNotifications")
        android.util.Log.d("HomeScreen", "currentPermission: $currentPermission")
        android.util.Log.d("HomeScreen", "PERMISSION_GRANTED: ${PackageManager.PERMISSION_GRANTED}")

        when {
            currentPermission == PackageManager.PERMISSION_GRANTED -> {
                android.util.Log.d("HomeScreen", "Permission already granted")
                if (userEnabledNotifications) {
                    val roomNumber = FirebaseDataState.roomNumber.removePrefix("Phòng ")
                    if (roomNumber != "--" && roomNumber.isNotEmpty()) {
                        FCMHelper.subscribeToUserBuildingTopics(roomNumber)
                    }
                }
            }
            hasRequestedBefore -> {
                android.util.Log.d("HomeScreen", "Already asked before, don't ask again")
            }
            else -> {
                android.util.Log.d("HomeScreen", "Will show permission dialog")
                Handler(Looper.getMainLooper()).postDelayed({
                    showNotificationPermissionDialog(activity, notificationPermissionLauncher)
                }, 800)
            }
        }
    } else {
        android.util.Log.d("HomeScreen", "Android version < TIRAMISU, no permission needed")
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
    android.util.Log.d("HomeScreen", "showNotificationPermissionDialog called")

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
                android.util.Log.d("HomeScreen", "User clicked Allow, launching permission request")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Không") { _, _ ->
                android.util.Log.d("HomeScreen", "User clicked Deny")
                handleNotificationPermissionDenied(activity)
            }
            .setCancelable(false)
            .create()

        android.util.Log.d("HomeScreen", "Showing dialog")
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#2196F3"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))

        android.util.Log.d("HomeScreen", "Dialog shown successfully")
    } catch (e: Exception) {
        android.util.Log.e("HomeScreen", "Error showing dialog: ${e.message}")
    }
}

private fun handleNotificationPermissionResult(activity: androidx.activity.ComponentActivity, isGranted: Boolean) {
    android.util.Log.d("HomeScreen", "handleNotificationPermissionResult: $isGranted")

    val appPrefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    appPrefs.edit().putBoolean("notification_permission_requested", true).apply()

    if (isGranted) {
        val appSettings = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appSettings.edit().putBoolean("notifications_enabled", true).apply()

        val roomNumber = FirebaseDataState.roomNumber.removePrefix("Phòng ")
        android.util.Log.d("HomeScreen", "Subscribing to topics for room: $roomNumber")

        if (roomNumber != "--" && roomNumber.isNotEmpty()) {
            FCMHelper.subscribeToUserBuildingTopics(roomNumber)
        }

        Toast.makeText(activity, "Đã cho phép thông báo", Toast.LENGTH_SHORT).show()
    } else {
        handleNotificationPermissionDenied(activity)
    }
}

private fun handleNotificationPermissionDenied(activity: androidx.activity.ComponentActivity) {
    android.util.Log.d("HomeScreen", "handleNotificationPermissionDenied called")

    val appPrefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    appPrefs.edit().putBoolean("notification_permission_requested", true).apply()

    val appSettings = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    appSettings.edit().putBoolean("notifications_enabled", false).apply()

    Toast.makeText(activity, "Bạn có thể bật thông báo trong Cài đặt nếu muốn nhận thông tin từ ban quản lý", Toast.LENGTH_LONG).show()
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
