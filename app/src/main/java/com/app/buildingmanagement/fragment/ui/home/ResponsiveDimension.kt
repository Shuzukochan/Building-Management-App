package com.app.buildingmanagement.fragment.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ResponsiveDimension(
    val mainPadding: Dp,
    val headerMarginBottom: Dp,
    val cardsMarginBottom: Dp,
    val titleMarginBottom: Dp,
    val cardMarginBottom: Dp,
    val cardPadding: Dp,
    val readingCardPadding: Dp,
    val tipsCardPadding: Dp,
    val cardMinHeight: Dp,
    val titleTextSize: TextUnit,
    val subtitleTextSize: TextUnit,
    val sectionTitleTextSize: TextUnit,
    val usageValueTextSize: TextUnit,
    val readingValueTextSize: TextUnit,
    val usageLabelTextSize: TextUnit
)

@Composable
fun responsiveDimension(): ResponsiveDimension {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.toFloat()
    return ResponsiveDimension(
        mainPadding = (screenHeight * 0.013f).coerceIn(8f, 28f).dp,
        headerMarginBottom = (screenHeight * 0.018f).coerceIn(8f, 36f).dp,
        cardsMarginBottom = (screenHeight * 0.022f).coerceIn(12f, 40f).dp,
        titleMarginBottom = (screenHeight * 0.015f).coerceIn(8f, 28f).dp,
        cardMarginBottom = (screenHeight * 0.022f).coerceIn(12f, 40f).dp,
        cardPadding = (screenHeight * 0.015f).coerceIn(8f, 24f).dp,
        readingCardPadding = (screenHeight * 0.025f).coerceIn(16f, 40f).dp,
        tipsCardPadding = (screenHeight * 0.02f).coerceIn(14f, 36f).dp,
        cardMinHeight = (screenHeight * 0.17f).coerceIn(160f, 240f).dp,
        titleTextSize = (screenHeight * 0.028f).coerceIn(22f, 32f).sp,
        subtitleTextSize = (screenHeight * 0.014f).coerceIn(11f, 16f).sp,
        sectionTitleTextSize = (screenHeight * 0.022f).coerceIn(18f, 24f).sp,
        usageValueTextSize = (screenHeight * 0.017f).coerceIn(16f, 20f).sp,
        readingValueTextSize = (screenHeight * 0.019f).coerceIn(18f, 22f).sp,
        usageLabelTextSize = (screenHeight * 0.014f).coerceIn(12f, 16f).sp
    )
}