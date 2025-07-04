package com.app.buildingmanagement.fragment.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun HeaderSection(
    roomNumber: String, 
    titleTextSize: TextUnit, 
    subtitleTextSize: TextUnit,
    onNotificationClick: () -> Unit = {},
    hasUnread: Boolean = true
) {
    Column {
        // Title row with notification icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tổng quan tiêu thụ",
                fontSize = titleTextSize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            
            // Notification icon with badge
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F4F6))
                        .clickable { onNotificationClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Thông báo",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Badge đỏ chỉ khi còn tin chưa đọc
                if (hasUnread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(HomeConstants.SPACING_MEDIUM.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getCurrentMonthText(),
                fontSize = subtitleTextSize,
                color = Color(0xFF6B7280)
            )
            Spacer(modifier = Modifier.width(HomeConstants.SPACING_LARGE.dp))
            Box(
                modifier = Modifier
                    .size(HomeConstants.SPACING_SMALL.dp)
                    .background(Color(0xFFD1D5DB), shape = MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(HomeConstants.SPACING_LARGE.dp))
            Text(
                text = roomNumber,
                fontSize = subtitleTextSize,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun getCurrentMonthText(): String {
    val calendar = java.util.Calendar.getInstance()
    val month = calendar.get(java.util.Calendar.MONTH) + 1
    val year = calendar.get(java.util.Calendar.YEAR)
    return "Tháng ${month.toString().padStart(2, '0')}/$year"
} 