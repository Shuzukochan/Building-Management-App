package com.app.buildingmanagement.fragment.ui.home

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.app.buildingmanagement.R
import com.app.buildingmanagement.fragment.ui.home.HomeConstants

@Composable
fun UsageCards(
    electricUsed: String,
    waterUsed: String,
    cardMinHeight: Dp,
    cardPadding: Dp,
    usageValueTextSize: TextUnit,
    usageLabelTextSize: TextUnit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        UsageCard(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            icon = Icons.Default.Bolt,
            value = electricUsed,
            backgroundColor = Color(0xFFFEF3C7),
            iconColor = Color(0xFFF59E0B),
            textColor = Color(0xFFE65100),
            cardMinHeight = cardMinHeight,
            cardPadding = cardPadding,
            usageValueTextSize = usageValueTextSize,
            usageLabelTextSize = usageLabelTextSize
        )
        UsageCard(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            icon = Icons.Filled.WaterDrop,
            value = waterUsed,
            backgroundColor = Color(0xFFCFFAFE),
            iconColor = Color(0xFF0891B2),
            textColor = Color(0xFF0E7490),
            cardMinHeight = cardMinHeight,
            cardPadding = cardPadding,
            usageValueTextSize = usageValueTextSize,
            usageLabelTextSize = usageLabelTextSize
        )
    }
}

@Composable
fun UsageCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    backgroundColor: Color,
    iconColor: Color,
    textColor: Color,
    cardMinHeight: Dp,
    cardPadding: Dp,
    usageValueTextSize: TextUnit,
    usageLabelTextSize: TextUnit
) {
    Card(
        modifier = modifier.height(cardMinHeight),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = HomeConstants.CARD_ELEVATION_DEFAULT.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(HomeConstants.ICON_SIZE_USAGE.dp)
            )
            Spacer(modifier = Modifier.height(HomeConstants.SPACING_LARGE.dp))
            Text(
                text = value,
                fontSize = usageValueTextSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(HomeConstants.SPACING_MEDIUM.dp))
            Text(
                text = "Đã sử dụng",
                fontSize = usageLabelTextSize,
                color = Color(0xFF616161),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
} 