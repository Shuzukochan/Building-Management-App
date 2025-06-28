package com.app.buildingmanagement.fragment.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data class cho các giá trị responsive của Settings
data class SettingsResponsiveDimension(
    val mainPadding: Dp,
    val cardPadding: Dp,
    val cardMarginBottom: Dp,
    val headerMarginBottom: Dp,
    val sectionMarginBottom: Dp,
    val userInfoMarginBottom: Dp,
    val titleTextSize: TextUnit,
    val bodyTextSize: TextUnit,
    val sectionTextSize: TextUnit,
    val labelTextSize: TextUnit
)

@Composable
fun settingsResponsiveDimension(): SettingsResponsiveDimension {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.toFloat()

    return SettingsResponsiveDimension(
        mainPadding = (screenHeight * 0.010f).coerceIn(8f, 20f).dp,
        cardPadding = (screenHeight * 0.015f).coerceIn(10f, 20f).dp,
        cardMarginBottom = (screenHeight * 0.015f).coerceIn(8f, 24f).dp,
        headerMarginBottom = (screenHeight * 0.012f).coerceIn(6f, 18f).dp,
        sectionMarginBottom = (screenHeight * 0.012f).coerceIn(8f, 16f).dp,
        userInfoMarginBottom = (screenHeight * 0.015f).coerceIn(8f, 18f).dp,
        titleTextSize = (screenHeight * 0.028f).coerceIn(22f, 32f).sp,
        bodyTextSize = (screenHeight * 0.016f).coerceIn(13f, 18f).sp,
        sectionTextSize = (screenHeight * 0.020f).coerceIn(15f, 22f).sp,
        labelTextSize = (screenHeight * 0.014f).coerceIn(11f, 16f).sp
    )
}

 