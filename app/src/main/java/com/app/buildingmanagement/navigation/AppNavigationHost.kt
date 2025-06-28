package com.app.buildingmanagement.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.app.buildingmanagement.fragment.HomeScreen
import com.app.buildingmanagement.fragment.ChartScreen
import com.app.buildingmanagement.fragment.PaymentScreen
import com.app.buildingmanagement.fragment.SettingsScreen

@Composable
fun AppNavigationHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME,
        modifier = modifier
    ) {
        composable(AppDestinations.HOME) {
            HomeScreen()
        }
        
        composable(AppDestinations.CHART) {
            ChartScreen()
        }
        
        composable(AppDestinations.PAYMENT) {
            PaymentScreen()
        }
        
        composable(AppDestinations.SETTINGS) {
            SettingsScreen()
        }
    }
}
