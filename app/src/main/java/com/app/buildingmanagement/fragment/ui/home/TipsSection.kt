package com.app.buildingmanagement.fragment.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TipsSection(tipsCardPadding: Dp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = HomeConstants.CARD_ELEVATION_DEFAULT.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tipsCardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Biểu tượng mẹo tiết kiệm",
                tint = Color(0xFF059669),
                modifier = Modifier.size(HomeConstants.ICON_SIZE_TIPS.dp)
            )
            Spacer(modifier = Modifier.width(HomeConstants.SPACING_XL.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mẹo tiết kiệm hôm nay",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF047857)
                )
                Text(
                    text = "Tắt thiết bị khi không dùng để tiết kiệm 10–15% chi phí!",
                    fontSize = 13.sp,
                    color = Color(0xFF047857),
                    modifier = Modifier.padding(top = 3.dp),
                    lineHeight = HomeConstants.SPACING_XXL.sp
                )
            }
        }
    }
}
