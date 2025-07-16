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
import com.app.buildingmanagement.models.ChartDataPoint
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme
import java.util.Calendar

/**
 * Statistics Activity
 * 
 * Displays detailed statistics and analytics for the building management system.
 * Includes charts, trends, and detailed reports.
 */
class StatisticsActivity : ComponentActivity() {
    
    private val statisticsController = StatisticsController()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BuildingManagementTheme {
                StatisticsScreen()
            }
        }
        
        // Load initial data
        lifecycleScope.launch {
            statisticsController.loadStatistics()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StatisticsScreen() {
        val statistics by statisticsController.statistics.collectAsState()
        val isLoading by statisticsController.isLoading.collectAsState()
        val error by statisticsController.error.collectAsState()
        
        var selectedCategory by remember { mutableStateOf("all") }
        val categories = listOf(
            "all" to "Tất cả",
            "financial" to "Tài chính",
            "occupancy" to "Lấp đầy",
            "maintenance" to "Bảo trì",
            "users" to "Người dùng"
        )
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Thống kê & Báo cáo") },
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
                        
                        IconButton(
                            onClick = {
                                // TODO: Export statistics
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                    StatisticsContent(
                        statistics = statistics,
                        selectedCategory = selectedCategory,
                        categories = categories,
                        onCategorySelected = { selectedCategory = it }
                    )
                }
            }
        }
    }
    
    @Composable
    private fun StatisticsContent(
        statistics: List<Statistic>,
        selectedCategory: String,
        categories: List<Pair<String, String>>,
        onCategorySelected: (String) -> Unit
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Filter
            item {
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )
            }
            
            // Summary Cards
            item {
                SummarySection(statistics = statistics)
            }
            
            // Charts Section
            item {
                ChartsSection()
            }
            
            // Detailed Statistics
            val filteredStats = filterStatisticsByCategory(statistics, selectedCategory)
            items(filteredStats) { statistic ->
                DetailedStatisticCard(statistic = statistic)
            }
        }
    }
    
    @Composable
    private fun CategoryFilterRow(
        categories: List<Pair<String, String>>,
        selectedCategory: String,
        onCategorySelected: (String) -> Unit
    ) {
        LazyColumn {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { (key, name) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { onCategorySelected(key) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun SummarySection(statistics: List<Statistic>) {
        Column {
            Text(
                text = "Tổng quan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statistics.take(2).forEach { stat ->
                    SummaryCard(
                        statistic = stat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statistics.drop(2).take(2).forEach { stat ->
                    SummaryCard(
                        statistic = stat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    
    @Composable
    private fun SummaryCard(
        statistic: Statistic,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = Color(android.graphics.Color.parseColor(statistic.color)).copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = getIconForStatistic(statistic.icon),
                    contentDescription = statistic.title,
                    tint = Color(android.graphics.Color.parseColor(statistic.color)),
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = statistic.value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = statistic.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (statistic.changePercentage != 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (statistic.changePercentage > 0) 
                                Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = "Trend",
                            tint = if (statistic.changePercentage > 0) 
                                Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Text(
                            text = statistic.getFormattedChangePercentage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statistic.changePercentage > 0) 
                                Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ChartsSection() {
        Column {
            Text(
                text = "Biểu đồ",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Doanh thu theo tháng",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Placeholder for chart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Chart",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "Biểu đồ sẽ được hiển thị ở đây",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun DetailedStatisticCard(statistic: Statistic) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statistic.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "Loại: ${statistic.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (statistic.period.isNotEmpty()) {
                            Text(
                                text = "Kỳ: ${statistic.period}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = statistic.getFormattedValue(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(android.graphics.Color.parseColor(statistic.color))
                        )
                        
                        if (statistic.previousValue != null) {
                            Text(
                                text = "Trước: ${statistic.previousValue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ErrorMessage(
        error: String,
        onRetry: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
        "analytics" -> Icons.Default.Analytics
        else -> Icons.Default.BarChart
    }
    
    private fun filterStatisticsByCategory(statistics: List<Statistic>, category: String): List<Statistic> {
        if (category == "all") return statistics
        
        return statistics.filter { stat ->
            when (category) {
                "financial" -> stat.type.contains("revenue") || stat.type.contains("payment")
                "occupancy" -> stat.type.contains("room") || stat.type.contains("occupancy")
                "maintenance" -> stat.type.contains("maintenance")
                "users" -> stat.type.contains("user") || stat.type.contains("tenant")
                else -> true
            }
        }
    }
}