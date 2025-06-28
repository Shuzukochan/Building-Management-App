package com.app.buildingmanagement.fragment.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.app.buildingmanagement.R
import androidx.compose.ui.unit.sp
import com.app.buildingmanagement.fragment.ui.home.HomeConstants

@Composable
fun MeterReadingSection(
    electricReading: String,
    waterReading: String,
    sectionTitleTextSize: TextUnit,
    titleMarginBottom: Dp,
    cardMarginBottom: Dp,
    readingCardPadding: Dp,
    readingValueTextSize: TextUnit
) {
    Column {
        Text(
            text = "Chỉ số đồng hồ hiện tại",
            fontSize = sectionTitleTextSize,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.padding(bottom = titleMarginBottom)
        )
        MeterReadingCard(
            icon = Icons.Default.Bolt,
            label = "Số điện",
            value = electricReading,
            accentColor = Color(0xFFF59E0B),
            iconColor = Color(0xFFF59E0B),
            readingCardPadding = readingCardPadding,
            readingValueTextSize = readingValueTextSize
        )
        Spacer(modifier = Modifier.height(HomeConstants.SPACING_XXXL.dp))
        MeterReadingCard(
            icon = Icons.Filled.WaterDrop,
            label = "Số nước",
            value = waterReading,
            accentColor = Color(0xFF0891B2),
            iconColor = Color(0xFF0891B2),
            readingCardPadding = readingCardPadding,
            readingValueTextSize = readingValueTextSize
        )
    }
}

@Composable
fun MeterReadingCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    iconColor: Color,
    readingCardPadding: Dp,
    readingValueTextSize: TextUnit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = HomeConstants.CARD_ELEVATION_READING.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(readingCardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(HomeConstants.ACCENT_LINE_WIDTH.dp)
                    .height(HomeConstants.ACCENT_LINE_HEIGHT.dp)
                    .background(accentColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(HomeConstants.ACCENT_LINE_CORNER.dp))
            )
            Spacer(modifier = Modifier.width(HomeConstants.SPACER_READING_ICON.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(HomeConstants.ICON_SIZE_READING.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
                Text(
                    text = value,
                    fontSize = readingValueTextSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.padding(top = HomeConstants.SPACING_SMALL.dp)
                )
            }
        }
    }
} 