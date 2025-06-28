package com.app.buildingmanagement.fragment.ui.pay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun PaymentButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(PayConstants.buttonHeight),
        shape = RoundedCornerShape(PayConstants.cardCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) Color(0xFFFF9800) else Color(0xFF9E9E9E),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF9E9E9E),
            disabledContentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = PayConstants.subtitleTextSize,
            fontWeight = FontWeight.Bold
        )
    }
} 