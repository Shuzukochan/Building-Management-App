package com.app.buildingmanagement.fragment.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivateConnectivity
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.app.buildingmanagement.MainActivity
import com.app.buildingmanagement.R
import com.app.buildingmanagement.SignInActivity
import com.app.buildingmanagement.data.FirebaseDataState
import com.app.buildingmanagement.firebase.FCMHelper
import com.app.buildingmanagement.fragment.ui.home.HomeConstants
import com.app.buildingmanagement.model.SimplePayment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.compose.material3.Surface
import java.text.NumberFormat


// ============================================================================
// MAIN SETTINGS COMPOSABLE
// ============================================================================

@Composable
fun ComposeSettings(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val phone = user?.phoneNumber?.replace("+84", "0") ?: "Không có số điện thoại"
    
    // Get responsive dimensions with compact spacing
    val dimensions = com.app.buildingmanagement.fragment.ui.home.responsiveDimension()
    val compactSpacing = (dimensions.cardMarginBottom.value * 0.5f).dp
    
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
                sharedPref.edit().putBoolean("notifications_enabled", false).apply()
            }
        }
    }
    
    // Permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isNotificationEnabled = true
        } else {
            isNotificationEnabled = false
        }
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FB))
            .padding(dimensions.mainPadding),
        verticalArrangement = Arrangement.spacedBy(compactSpacing)
    ) {
        item {
            // Header - smaller font size
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tài khoản và cài đặt",
                    fontSize = dimensions.titleTextSize,
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
                phoneNumber = phone,
                dimensions = dimensions
            )
        }
        
        item {
            // Notification Section - smaller header
            NotificationSection(
                isEnabled = isNotificationEnabled,
                onToggle = {
                    handleNotificationToggle(
                        context = context,
                        enabled = !isNotificationEnabled,
                        permissionLauncher = notificationPermissionLauncher,
                        updateState = { isNotificationEnabled = it }
                    )
                },
                dimensions = dimensions
            )
        }
        
        item {
            // Payment Section - smaller header
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
                ),
                dimensions = dimensions
            )
        }
        
        item {
            // Support Section - smaller header
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
                        onClick = { openDialer(context, "0398103352") }
                    ),
                    SettingsActionItem(
                        icon = Icons.Default.Info,
                        iconBackgroundColor = Color(0xFFF3E5F5),
                        iconTint = Color(0xFF7B1FA2),
                        title = "Về ứng dụng",
                        onClick = { showAboutSheet = true }
                    )
                ),
                dimensions = dimensions
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        item {
            // Logout Button
            LogoutButton(
                onLogoutClick = {
                    showLogoutConfirmation(context, auth, onNavigateBack)
                },
                dimensions = dimensions
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
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
    phoneNumber: String,
    dimensions: com.app.buildingmanagement.fragment.ui.home.ResponsiveDimension
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = HomeConstants.CARD_ELEVATION_DEFAULT.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.cardPadding, vertical = (dimensions.cardPadding.value * 1.1f).dp),
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
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(HomeConstants.SPACING_LARGE.dp))
            
            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: User Name
                Text(
                    text = userName,
                    fontSize = (dimensions.usageValueTextSize.value * 0.95f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Line 2: Room & Phone
                Text(
                    text = "$roomNumber • $phoneNumber",
                    fontSize = (dimensions.subtitleTextSize.value * 1.05f).sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}

@Composable
private fun NotificationSection(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    dimensions: com.app.buildingmanagement.fragment.ui.home.ResponsiveDimension
) {
    Column {
        Text(
            text = "Thông báo",
            fontSize = 14.sp, // Smaller font size
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(
                start = HomeConstants.SPACING_SMALL.dp,
                bottom = (dimensions.headerMarginBottom.value * 0.4f).dp
            )
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = HomeConstants.CARD_ELEVATION_DEFAULT.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .heightIn(min = 64.dp)
                    .padding(dimensions.cardPadding),
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
                
                Spacer(modifier = Modifier.width(HomeConstants.SPACING_LARGE.dp))
                
                Text(
                    text = "Thông báo đẩy",
                    fontSize = (dimensions.usageValueTextSize.value * 0.9f).sp,
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
    items: List<SettingsActionItem>,
    dimensions: com.app.buildingmanagement.fragment.ui.home.ResponsiveDimension
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp, // Smaller font size
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(
                start = HomeConstants.SPACING_SMALL.dp,
                bottom = (dimensions.headerMarginBottom.value * 0.4f).dp
            )
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = HomeConstants.CARD_ELEVATION_DEFAULT.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    ActionItemRow(item = item, dimensions = dimensions)
                    
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = (40 + HomeConstants.SPACING_LARGE + HomeConstants.SPACING_LARGE).dp
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
    item: SettingsActionItem,
    dimensions: com.app.buildingmanagement.fragment.ui.home.ResponsiveDimension
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .heightIn(min = 64.dp)
            .padding(dimensions.cardPadding),
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
        
        Spacer(modifier = Modifier.width(HomeConstants.SPACING_LARGE.dp))
        
        Text(
            text = item.title,
            fontSize = (dimensions.usageValueTextSize.value * 0.9f).sp,
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
    onLogoutClick: () -> Unit,
    dimensions: com.app.buildingmanagement.fragment.ui.home.ResponsiveDimension
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = HomeConstants.CARD_ELEVATION_DEFAULT.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLogoutClick() }
                .heightIn(min = 64.dp)
                .padding(dimensions.cardPadding),
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
            
            Spacer(modifier = Modifier.width(HomeConstants.SPACING_LARGE.dp))
            
            Text(
                text = "Đăng xuất",
                fontSize = (dimensions.usageValueTextSize.value * 0.9f).sp,
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

// All functions moved to separate files:
// - UI Components: Available as composables in this file
// - Bottom Sheets: Available in SettingsBottomSheets.kt  
// - Utility Functions: Available in SettingsUtils.kt