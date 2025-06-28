package com.app.buildingmanagement.fragment.ui.pay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.WaterDrop

@Composable
fun UsageDetailCard(
    isCurrentMonth: Boolean,
    displayMonth: String,
    electricUsage: Double,
    waterUsage: Double,
    electricCost: Int,
    waterCost: Int,
    totalCost: Int,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION") val formatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))
    
    // Helper function để format usage values
    fun formatUsage(value: Double, unit: String): String {
        return when {
            value < 10.0 -> String.format(Locale.getDefault(), "%.2f %s", value, unit)
            value < 100.0 -> String.format(Locale.getDefault(), "%.1f %s", value, unit) 
            else -> String.format(Locale.getDefault(), "%.0f %s", value, unit)
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PayConstants.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PayConstants.detailCardPadding)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = "Calculator",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = if (isCurrentMonth) {
                        "Tạm tính hiện tại"
                    } else {
                        "Chi tiết tháng $displayMonth"
                    },
                    fontSize = PayConstants.subtitleTextSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E2E2E)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Usage Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Electric Usage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Electric",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(18.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Text(
                            text = "Điện: ${formatUsage(electricUsage, "kWh")}",
                            fontSize = PayConstants.bodyTextSize,
                            color = Color(0xFF444444),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = "${formatter.format(electricCost)} VNĐ",
                            fontSize = PayConstants.bodyTextSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Water Usage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = "Water",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(18.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Text(
                            text = "Nước: ${formatUsage(waterUsage, "m³")}",
                            fontSize = PayConstants.bodyTextSize,
                            color = Color(0xFF444444),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = "${formatter.format(waterCost)} VNĐ",
                            fontSize = PayConstants.bodyTextSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Total
            HorizontalDivider(
                color = Color(0xFFE0E0E0),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tổng cộng:",
                    fontSize = PayConstants.subtitleTextSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E2E2E)
                )
                
                Text(
                    text = "${formatter.format(totalCost)} VNĐ",
                    fontSize = PayConstants.subtitleTextSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935)
                )
            }
        }
    }
} 