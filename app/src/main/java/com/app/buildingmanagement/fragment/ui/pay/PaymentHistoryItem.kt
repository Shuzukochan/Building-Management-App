package com.app.buildingmanagement.fragment.ui.pay

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.buildingmanagement.model.SimplePayment

@Composable
fun PaymentHistoryItem(
    payment: SimplePayment,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(PayConstants.detailCardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = payment.getFormattedDate(),
                fontSize = PayConstants.bodyTextSize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E2E2E)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = payment.getFormattedAmount(),
                fontSize = PayConstants.subtitleTextSize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
        }
        
        Icon(
            imageVector = when (payment.status.uppercase()) {
                "PAID" -> Icons.Default.CheckCircle
                "PENDING" -> Icons.Default.Pending
                else -> Icons.Default.Error
            },
            contentDescription = "Payment Status",
            tint = when (payment.status.uppercase()) {
                "PAID" -> Color(0xFF4CAF50)
                "PENDING" -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            },
            modifier = Modifier.size(28.dp)
        )
    }
    
    HorizontalDivider(
        color = Color(0xFFE0E0E0),
        thickness = 1.dp
    )
} 