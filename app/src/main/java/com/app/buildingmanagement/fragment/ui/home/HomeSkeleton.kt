package com.app.buildingmanagement.fragment.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LoadingSkeleton(dimen: ResponsiveDimension) {
    val skeletonColor = Color(0xFFE9ECEF)
    val skeletonShape = MaterialTheme.shapes.medium

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .verticalScroll(rememberScrollState())
            .padding(dimen.mainPadding)
    ) {
        // --- Header Skeleton ---
        Column {
            // Title placeholder ("Tổng quan tiêu thụ")
            Box(
                modifier = Modifier
                    .height(dimen.titleTextSize.value.dp)
                    .width(220.dp)
                    .background(skeletonColor, skeletonShape)
            )
            Spacer(modifier = Modifier.height(HomeConstants.SPACING_MEDIUM.dp))
            // Subtitle placeholder ("Tháng 06/2025 • Phòng 101")
            Box(
                modifier = Modifier
                    .height(dimen.subtitleTextSize.value.dp)
                    .width(180.dp)
                    .background(skeletonColor, skeletonShape)
            )
        }

        Spacer(modifier = Modifier.height(HomeConstants.SPACING_XXL.dp))

        // --- Usage Cards Skeleton ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp) // Khoảng cách thực tế giữa 2 card
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(dimen.cardMinHeight)
                    .background(skeletonColor, RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(dimen.cardMinHeight)
                    .background(skeletonColor, RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp))
            )
        }

        Spacer(modifier = Modifier.height(HomeConstants.SPACING_XXXL.dp))

        // --- Meter Reading Section Skeleton ---
        Column {
            // Section Title placeholder ("Chỉ số đồng hồ hiện tại")
            Box(
                modifier = Modifier
                    .height(dimen.sectionTitleTextSize.value.dp)
                    .width(200.dp)
                    .background(skeletonColor, skeletonShape)
            )
            Spacer(modifier = Modifier.height(dimen.titleMarginBottom))
            // First Meter Reading Card placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp) // Chiều cao thực tế hơn
                    .background(skeletonColor, RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp))
            )
            Spacer(modifier = Modifier.height(HomeConstants.SPACING_XXXL.dp)) // Khoảng cách giữa 2 card
            // Second Meter Reading Card placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp) // Chiều cao thực tế hơn
                    .background(skeletonColor, RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp))
            )
        }

        Spacer(modifier = Modifier.height(HomeConstants.SPACING_XXXL.dp))

        // --- Tips Section Skeleton ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Chiều cao ước tính cho Tips card
                .background(skeletonColor, RoundedCornerShape(HomeConstants.CARD_CORNER_RADIUS.dp))
        )

        Spacer(modifier = Modifier.height(HomeConstants.SPACING_XXL.dp))
    }
} 