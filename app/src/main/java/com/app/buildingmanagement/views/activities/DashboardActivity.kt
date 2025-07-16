package com.app.buildingmanagement.views.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.app.buildingmanagement.controllers.StatisticsController
import com.app.buildingmanagement.models.Statistic
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme

/**
 * Dashboard Activity
 * 
 * Main dashboard screen displaying key statistics and metrics.
 * Shows overview of building management system including occupancy, revenue, and maintenance.
 */
class DashboardActivity : ComponentActivity() {
    
    private val statisticsController = StatisticsController()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BuildingManagementTheme {
                DashboardScreen()
            }
        }
        
        // Load initial data
        lifecycleScope.launch {
            statisticsController.loadStatistics()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DashboardScreen() {
        val statistics by statisticsController.statistics.collectAsState()
        val isLoading by statisticsController.isLoading.collectAsState()
        val error by statisticsController.error.collectAsState()
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dashboard") },
                    actions = {
                        IconButton(
                            onClick = {
                                lifecycleScope.launch {
                                    statisticsController.refresh()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    ErrorMessage(
                        error = error,
                        onRetry = {
                            lifecycleScope.launch {
                                statisticsController.clearError()
                                statisticsController.loadStatistics()
                            }
                        }
                    )
                } else {
                    StatisticsGrid(statistics = statistics)
                }
            }
        }
    }
    
    @Composable
    private fun StatisticsGrid(statistics: List<Statistic>) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(statistics.chunked(2)) { rowStats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowStats.forEach { stat ->
                        StatisticCard(
                            statistic = stat,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Add empty space if only one item in row
                    if (rowStats.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    
    @Composable
    private fun StatisticCard(
        statistic: Statistic,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = getIconForStatistic(statistic.icon),
                    contentDescription = statistic.title,
                    tint = Color(android.graphics.Color.parseColor(statistic.color)),
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = statistic.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = statistic.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    @Composable
    private fun ErrorMessage(
        error: String,
        onRetry: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = onRetry) {
                Text("Thử lại")
            }
        }
    }
    
    private fun getIconForStatistic(iconName: String) = when (iconName) {
        "person" -> Icons.Default.Person
        "home" -> Icons.Default.Home
        "trending_up" -> Icons.Default.TrendingUp
        "attach_money" -> Icons.Default.AttachMoney
        "schedule" -> Icons.Default.Schedule
        else -> Icons.Default.Analytics
    }
}