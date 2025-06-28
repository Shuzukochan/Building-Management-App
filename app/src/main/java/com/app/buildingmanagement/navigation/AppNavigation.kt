package com.app.buildingmanagement.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.app.buildingmanagement.R

// Navigation routes
object AppDestinations {
    const val HOME = "home"
    const val CHART = "chart"
    const val PAYMENT = "payment"
    const val SETTINGS = "settings"
}

// Bottom Navigation Items
data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = AppDestinations.HOME,
        title = "Tổng quan",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        contentDescription = "Màn hình tổng quan"
    ),
    BottomNavItem(
        route = AppDestinations.CHART,
        title = "Biểu đồ",
        selectedIcon = Icons.Filled.Assessment,
        unselectedIcon = Icons.Outlined.Assessment,
        contentDescription = "Xem biểu đồ thống kê"
    ),
    BottomNavItem(
        route = AppDestinations.PAYMENT,
        title = "Thanh toán",
        selectedIcon = Icons.Filled.Payment,
        unselectedIcon = Icons.Outlined.Payment,
        contentDescription = "Quản lý thanh toán"
    ),
    BottomNavItem(
        route = AppDestinations.SETTINGS,
        title = "Tài khoản",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        contentDescription = "Thông tin người dùng"
    )
) 