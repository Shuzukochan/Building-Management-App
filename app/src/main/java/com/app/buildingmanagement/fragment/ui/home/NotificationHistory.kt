package com.app.buildingmanagement.fragment.ui.home

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.buildingmanagement.data.FirebaseDataState
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable

// ============================================================
// Data Model
// ============================================================

data class NotificationItem(
    val id: String,
    val message: String,
    val timestamp: String,
    val sentBy: String = "admin",
    var isRead: Boolean = false
)

// ============================================================
// Public API – call from Composable to show sheet
// ============================================================

@Composable
fun NotificationHistoryBottomSheet(
    onDismiss: () -> Unit,
    onUnreadChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // States for data
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load notifications once
    LaunchedEffect(Unit) {
        // Show dialog lazily after we have this LaunchedEffect scope
        val dialog = BottomSheetDialog(context)
        dialog.behavior.isDraggable = true
        dialog.behavior.isFitToContents = true
        dialog.behavior.skipCollapsed = true

        val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NotificationHistorySheetContent(
                    notifications = notifications,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onClose = { dialog.dismiss() },
                    onUnreadChanged = onUnreadChanged
                )
            }
        }

        dialog.setContentView(composeView)
        dialog.setOnDismissListener { onDismiss() }

        // Start loading data, then show dialog when done
        loadNotifications { list, error ->
            notifications = list
            errorMessage = error
            isLoading = false
            onUnreadChanged(list.any { !it.isRead })
        }

        dialog.show()
    }
}

// ============================================================
// Sheet Content
// ============================================================

@Composable
private fun NotificationHistorySheetContent(
    notifications: List<NotificationItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onClose: () -> Unit,
    onUnreadChanged: (Boolean) -> Unit
) {
    // Chuyển sang mutableStateList để có thể cập nhật khi mark read
    val notificationList = remember { mutableStateListOf<NotificationItem>() }
    LaunchedEffect(notifications) {
        notificationList.clear()
        notificationList.addAll(notifications)
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFBBDEFB), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Thông báo",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Lịch sử thông báo",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }

                // Close button
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when {
                isLoading -> LoadingContent()
                errorMessage != null -> ErrorContent(errorMessage)
                notificationList.isEmpty() -> EmptyContent()
                else -> NotificationList(notificationList) { item ->
                    if (!item.isRead) {
                        markNotificationAsRead(item) { success ->
                            if (success) {
                                val idx = notificationList.indexOfFirst { it.id == item.id }
                                if (idx >= 0) notificationList[idx] = item.copy(isRead = true)
                                onUnreadChanged(notificationList.any { !it.isRead })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Content states
// ============================================================

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color(0xFF2196F3))
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = Color(0xFF6B7280))
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Chưa có thông báo nào", color = Color(0xFF6B7280))
    }
}

@Composable
private fun NotificationList(
    list: List<NotificationItem>,
    onItemClick: (NotificationItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.3f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list) { item -> NotificationCard(item, onItemClick) }
    }
}

@Composable
private fun NotificationCard(notification: NotificationItem, onClick: (NotificationItem) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(notification) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) Color(0xFFF8F9FA) else Color(0xFFE3F2FD)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(notification.timestamp),
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.weight(1f)
                )
                if (!notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E88E5))
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notification.message,
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp
            )
        }
    }
}

// ============================================================
// Timestamp format helper (with +7 hours)
// ============================================================

@SuppressLint("SimpleDateFormat")
private fun formatTimestamp(timestamp: String): String {
    return try {
        val inputOutputFormat = SimpleDateFormat("HH:mm dd/MM/yyyy")
        // Parse theo timezone hệ thống (mặc định thiết bị)
        val date = inputOutputFormat.parse(timestamp) ?: return timestamp
        // Cộng thêm 7 giờ đúng 1 lần
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.HOUR_OF_DAY, 7)
        }
        inputOutputFormat.format(cal.time)
    } catch (_: Exception) {
        timestamp
    }
}

// ============================================================
// Firebase loading helper
// ============================================================

private fun loadNotifications(
    callback: (List<NotificationItem>, String?) -> Unit
) {
    val buildingId = FirebaseDataState.getCurrentBuildingId()
    val roomId = FirebaseDataState.getCurrentRoomId()
    if (buildingId == null || roomId == null) {
        callback(emptyList(), "Không thể tải thông báo")
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
            val list = mutableListOf<NotificationItem>()
            for (child in snapshot.children) {
                val id = child.key ?: continue
                val message = child.child("message").getValue(String::class.java) ?: ""
                val timestamp = child.child("timestamp").getValue(String::class.java) ?: ""
                val sentBy = child.child("sentBy").getValue(String::class.java) ?: "admin"
                val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false
                if (message.isNotEmpty()) {
                    list.add(NotificationItem(id, message, timestamp, sentBy, isRead))
                }
            }
            list.sortWith { a, b -> b.timestamp.compareTo(a.timestamp) }
            callback(list, null)
        }
        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
            callback(emptyList(), "Lỗi: ${error.message}")
        }
    })
}

private fun markNotificationAsRead(notification: NotificationItem, onResult: (Boolean) -> Unit) {
    val buildingId = FirebaseDataState.getCurrentBuildingId()
    val roomId = FirebaseDataState.getCurrentRoomId()
    if (buildingId == null || roomId == null) {
        onResult(false)
        return
    }
    val ref = com.google.firebase.database.FirebaseDatabase.getInstance()
        .getReference("buildings")
        .child(buildingId)
        .child("rooms")
        .child(roomId)
        .child("notifications")
        .child(notification.id)
        .child("isRead")
    ref.setValue(true).addOnCompleteListener { onResult(it.isSuccessful) }
} 