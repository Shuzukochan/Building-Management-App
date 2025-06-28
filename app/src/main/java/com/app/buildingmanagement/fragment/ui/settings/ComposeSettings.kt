package com.app.buildingmanagement.fragment.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.app.buildingmanagement.data.FirebaseDataState
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ComposeSettings(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val phone = user?.phoneNumber?.replace("+84", "0") ?: "Không có số điện thoại"
    
    // Get home dimensions for header only
    val homeDimensions = com.app.buildingmanagement.fragment.ui.home.responsiveDimension()

    // States
    var isNotificationEnabled by remember { mutableStateOf(true) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showPaymentHistorySheet by remember { mutableStateOf(false) }

    // Load notification preference
    LaunchedEffect(Unit) {
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        isNotificationEnabled = sharedPref.getBoolean("notifications_enabled", true)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasSystemPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasSystemPermission && isNotificationEnabled) {
                isNotificationEnabled = false
                sharedPref.edit {
                    putBoolean("notifications_enabled", false)
                }
            }
        }
    }
    
    // Permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        isNotificationEnabled = isGranted
        sharedPref.edit {
            putBoolean("notifications_enabled", isGranted)
        }

        // IMPORTANT: Subscribe to FCM topic if permission granted
        if (isGranted) {
            val cleanRoomNumber = FirebaseDataState.roomNumber.replace("Phòng ", "")
            com.app.buildingmanagement.firebase.FCMHelper.subscribeToUserBuildingTopics(cleanRoomNumber)
            android.widget.Toast.makeText(context, "Đã bật thông báo", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FB))
            .padding(homeDimensions.mainPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsConstants.CARD_MARGIN_BOTTOM.dp)
    ) {
        item {
            // Header - use home dimensions for consistency
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tài khoản và cài đặt",
                    fontSize = homeDimensions.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
            }
        }
        
        item {
            // User Info Card
            UserInfoCard(
                userName = if (FirebaseDataState.isUserDataLoaded) FirebaseDataState.userName else "Đang tải...",
                roomNumber = if (FirebaseDataState.isDataLoaded) FirebaseDataState.roomNumber else "Đang tải...",
                phoneNumber = phone
            )
        }
        
        item {
            // Notification Section
            NotificationSection(
                isEnabled = isNotificationEnabled,
                onToggle = {
                    handleNotificationToggle(
                        context = context,
                        enabled = !isNotificationEnabled,
                        permissionLauncher = notificationPermissionLauncher,
                        updateState = { newState ->
                            isNotificationEnabled = newState
                        }
                    )
                }
            )
        }
        
        item {
            // Payment Section
            ActionSection(
                title = "Thanh toán",
                items = listOf(
                    SettingsActionItem(
                        icon = Icons.Default.History,
                        iconBackgroundColor = Color(0xFFE3F2FD),
                        iconTint = Color(0xFF1976D2),
                        title = "Lịch sử thanh toán",
                        onClick = { showPaymentHistorySheet = true }
                    )
                )
            )
        }
        
        item {
            // Support Section
            ActionSection(
                title = "Hỗ trợ",
                items = listOf(
                    SettingsActionItem(
                        icon = Icons.Default.Feedback,
                        iconBackgroundColor = Color(0xFFFFF3E0),
                        iconTint = Color(0xFFF57C00),
                        title = "Góp ý",
                        onClick = { showFeedbackSheet = true }
                    ),
                    SettingsActionItem(
                        icon = Icons.Default.Phone,
                        iconBackgroundColor = Color(0xFFE8F5E8),
                        iconTint = Color(0xFF388E3C),
                        title = "Liên hệ hỗ trợ",
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = "tel:0398103352".toUri()
                            }
                            context.startActivity(intent)
                        }
                    ),
                    SettingsActionItem(
                        icon = Icons.Default.Info,
                        iconBackgroundColor = Color(0xFFF3E5F5),
                        iconTint = Color(0xFF7B1FA2),
                        title = "Về ứng dụng",
                        onClick = { showAboutSheet = true }
                    )
                )
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(SettingsConstants.SPACING_XS.dp))
        }
        
        item {
            // Logout Button
            LogoutButton(
                onLogoutClick = {
                    showLogoutConfirmation(
                        context = context,
                        auth = auth,
                        onNavigateBack = onNavigateBack
                    )
                }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(SettingsConstants.SPACING_S.dp))
        }
    }
    
    // Bottom sheets
    if (showFeedbackSheet) {
        FeedbackBottomSheet(onDismiss = { showFeedbackSheet = false })
    }
    
    if (showAboutSheet) {
        AboutBottomSheet(onDismiss = { showAboutSheet = false })
    }
    
    if (showPaymentHistorySheet) {
        PaymentHistoryBottomSheet(onDismiss = { showPaymentHistorySheet = false })
    }
}

// ============================================================================
// UI COMPONENTS
// ============================================================================

// Data class for action items
data class SettingsActionItem(
    val icon: ImageVector,
    val iconBackgroundColor: Color,
    val iconTint: Color,
    val title: String,
    val onClick: () -> Unit
)

@Composable
private fun UserInfoCard(
    userName: String,
    roomNumber: String,
    phoneNumber: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsConstants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = SettingsConstants.CARD_ELEVATION_DEFAULT.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SettingsConstants.SPACING_L.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFFFFF3E0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFF57C00),
                    modifier = Modifier.size(SettingsConstants.ICON_SIZE_SMALL.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(SettingsConstants.SPACING_L.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: User Name
                Text(
                    text = userName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = SettingsConstants.SPACING_XS.dp)
                )
                
                // Line 2: Room & Phone
                Text(
                    text = "$roomNumber • $phoneNumber",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}

@Composable
private fun NotificationSection(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Column {
        Text(
            text = "Thông báo",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(
                start = SettingsConstants.SPACING_S.dp,
                bottom = SettingsConstants.SPACING_S.dp
            )
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SettingsConstants.CARD_CORNER_RADIUS.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = SettingsConstants.CARD_ELEVATION_DEFAULT.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .heightIn(min = 64.dp)
                    .padding(SettingsConstants.SPACING_L.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE8F5E8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF388E3C),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(SettingsConstants.SPACING_L.dp))

                Text(
                    text = "Thông báo đẩy",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFBDBDBD)
                    )
                )
            }
        }
    }
}

@Composable
private fun ActionSection(
    title: String,
    items: List<SettingsActionItem>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(
                start = SettingsConstants.SPACING_S.dp,
                bottom = SettingsConstants.SPACING_S.dp
            )
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SettingsConstants.CARD_CORNER_RADIUS.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = SettingsConstants.CARD_ELEVATION_DEFAULT.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    ActionItemRow(item = item)

                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = (40 + SettingsConstants.SPACING_L + SettingsConstants.SPACING_L).dp
                            ),
                            color = Color(0xFFF0F0F0),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionItemRow(
    item: SettingsActionItem
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .heightIn(min = 64.dp)
            .padding(SettingsConstants.SPACING_L.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(item.iconBackgroundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(SettingsConstants.SPACING_L.dp))

        Text(
            text = item.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF999999),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun LogoutButton(
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsConstants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = SettingsConstants.CARD_ELEVATION_DEFAULT.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLogoutClick() }
                .heightIn(min = 64.dp)
                .padding(SettingsConstants.SPACING_L.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFFEBEE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(SettingsConstants.SPACING_L.dp))

            Text(
                text = "Đăng xuất",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F),
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF999999),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================
