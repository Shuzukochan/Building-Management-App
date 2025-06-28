package com.app.buildingmanagement.fragment.ui.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.buildingmanagement.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Warning

enum class PaymentStatus {
    LOADING, PAID, PENDING, UNPAID, NO_NEED
}

@Composable
fun PaymentStatusCard(
    status: PaymentStatus,
    displayMonth: String,
    note: String,
    modifier: Modifier = Modifier
) {
    val (gradient, icon, statusText) = when (status) {
        PaymentStatus.LOADING -> Triple(
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD))
            ),
            Icons.Default.Pending,
            "Đang tải thông tin..."
        )
        PaymentStatus.PAID -> Triple(
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF4CAF50), Color(0xFF66BB6A))
            ),
            Icons.Default.CheckCircle,
            "Đã thanh toán tháng $displayMonth"
        )
        PaymentStatus.PENDING -> Triple(
            Brush.horizontalGradient(
                colors = listOf(Color(0xFFFF9800), Color(0xFFFFA726))
            ),
            Icons.Default.Pending,
            "Ước tính tháng $displayMonth"
        )
        PaymentStatus.UNPAID -> Triple(
            Brush.horizontalGradient(
                colors = listOf(Color(0xFFF44336), Color(0xFFEF5350))
            ),
            Icons.Default.Warning,
            "Chưa thanh toán tháng $displayMonth"
        )
        PaymentStatus.NO_NEED -> Triple(
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF4CAF50), Color(0xFF66BB6A))
            ),
            Icons.Default.CheckCircle,
            "Không cần thanh toán tháng $displayMonth"
        )
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PayConstants.statusCardCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(PayConstants.statusCardPadding)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Status Icon",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = PayConstants.subtitleTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = note,
                    color = Color(0xFFE8F5E8),
                    fontSize = PayConstants.bodyTextSize,
                    lineHeight = PayConstants.bodyTextSize * 1.2f
                )
            }
        }
    }
} 