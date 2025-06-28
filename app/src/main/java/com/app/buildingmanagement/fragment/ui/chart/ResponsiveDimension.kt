package com.app.buildingmanagement.fragment.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data class cho các giá trị responsive của Chart
data class ChartResponsiveDimension(
    val mainPadding: Dp,
    val cardPadding: Dp,
    val cardMarginBottom: Dp,
    val headerMarginBottom: Dp,
    val dateMarginBottom: Dp,
    val titleTextSize: TextUnit,
    val chartTitleTextSize: TextUnit,
    val labelTextSize: TextUnit,
    val chartHeight: Dp
)

@Composable
fun chartResponsiveDimension(): ChartResponsiveDimension {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenWidth = configuration.screenWidthDp.toFloat()

    return ChartResponsiveDimension(
        mainPadding = (screenHeight * 0.013f).coerceIn(8f, 24f).dp,
        cardPadding = (screenHeight * 0.018f).coerceIn(12f, 32f).dp,
        cardMarginBottom = (screenHeight * 0.025f).coerceIn(16f, 40f).dp,
        headerMarginBottom = (screenHeight * 0.015f).coerceIn(8f, 24f).dp,
        dateMarginBottom = (screenHeight * 0.018f).coerceIn(12f, 28f).dp,
        titleTextSize = (screenHeight * 0.028f).coerceIn(22f, 32f).sp,
        chartTitleTextSize = (screenHeight * 0.020f).coerceIn(16f, 24f).sp,
        labelTextSize = (screenHeight * 0.014f).coerceIn(12f, 16f).sp,
        chartHeight = (screenHeight * 0.35f).coerceIn(280f, 400f).dp
    )
} 