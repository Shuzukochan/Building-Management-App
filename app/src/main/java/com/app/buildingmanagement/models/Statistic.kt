package com.app.buildingmanagement.models

import java.util.Date

/**
 * Statistic data model
 * 
 * Represents statistical data for dashboard and reporting purposes.
 * Used for displaying various metrics and KPIs in the building management system.
 */
data class Statistic(
    val id: String = "",
    val title: String = "",
    val value: String = "",
    val previousValue: String? = null,
    val unit: String? = null,
    val icon: String = "",
    val color: String = "#000000",
    val type: String = "", // revenue, occupancy, maintenance, user_count, etc.
    val category: String = "", // financial, operational, user, maintenance
    val period: String = "", // daily, weekly, monthly, yearly
    val date: Date = Date(),
    val trend: StatisticTrend = StatisticTrend.NEUTRAL,
    val changePercentage: Double = 0.0,
    val metadata: Map<String, Any> = emptyMap(),
    val chartData: List<ChartDataPoint> = emptyList(),
    val isVisible: Boolean = true,
    val order: Int = 0
) {
    /**
     * Get formatted change percentage
     */
    fun getFormattedChangePercentage(): String {
        val sign = when {
            changePercentage > 0 -> "+"
            changePercentage < 0 -> ""
            else -> ""
        }
        return "$sign${String.format("%.1f", changePercentage)}%"
    }
    
    /**
     * Get trend icon
     */
    fun getTrendIcon(): String {
        return when (trend) {
            StatisticTrend.UP -> "trending_up"
            StatisticTrend.DOWN -> "trending_down"
            StatisticTrend.NEUTRAL -> "trending_flat"
        }
    }
    
    /**
     * Get trend color
     */
    fun getTrendColor(): String {
        return when (trend) {
            StatisticTrend.UP -> "#4CAF50" // Green
            StatisticTrend.DOWN -> "#F44336" // Red
            StatisticTrend.NEUTRAL -> "#9E9E9E" // Gray
        }
    }
    
    /**
     * Get formatted value with unit
     */
    fun getFormattedValue(): String {
        return if (unit != null) "$value $unit" else value
    }
    
    /**
     * Check if statistic shows improvement
     */
    fun showsImprovement(): Boolean {
        return when (type) {
            "revenue", "occupancy_rate", "user_count", "satisfaction" -> trend == StatisticTrend.UP
            "maintenance_requests", "complaints", "overdue_payments" -> trend == StatisticTrend.DOWN
            else -> trend != StatisticTrend.DOWN
        }
    }
}

/**
 * Chart data point for statistical visualizations
 */
data class ChartDataPoint(
    val label: String = "",
    val value: Double = 0.0,
    val date: Date = Date(),
    val category: String? = null,
    val color: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Get formatted value
     */
    fun getFormattedValue(): String {
        return String.format("%.2f", value)
    }
}

/**
 * Statistic trend enumeration
 */
enum class StatisticTrend {
    UP,
    DOWN,
    NEUTRAL
}

/**
 * Pre-defined statistic types
 */
object StatisticType {
    // Financial
    const val TOTAL_REVENUE = "total_revenue"
    const val MONTHLY_REVENUE = "monthly_revenue"
    const val OUTSTANDING_PAYMENTS = "outstanding_payments"
    const val AVERAGE_RENT = "average_rent"
    
    // Occupancy
    const val OCCUPANCY_RATE = "occupancy_rate"
    const val TOTAL_ROOMS = "total_rooms"
    const val OCCUPIED_ROOMS = "occupied_rooms"
    const val VACANT_ROOMS = "vacant_rooms"
    
    // Maintenance
    const val MAINTENANCE_REQUESTS = "maintenance_requests"
    const val COMPLETED_MAINTENANCE = "completed_maintenance"
    const val PENDING_MAINTENANCE = "pending_maintenance"
    const val MAINTENANCE_COST = "maintenance_cost"
    
    // User Management
    const val TOTAL_USERS = "total_users"
    const val ACTIVE_TENANTS = "active_tenants"
    const val NEW_REGISTRATIONS = "new_registrations"
    const val USER_SATISFACTION = "user_satisfaction"
    
    // Operational
    const val COMPLAINTS = "complaints"
    const val RESOLVED_ISSUES = "resolved_issues"
    const val RESPONSE_TIME = "response_time"
    const val LEASE_RENEWALS = "lease_renewals"
}

/**
 * Statistic categories
 */
object StatisticCategory {
    const val FINANCIAL = "financial"
    const val OPERATIONAL = "operational"
    const val USER = "user"
    const val MAINTENANCE = "maintenance"
    const val PERFORMANCE = "performance"
}

/**
 * Time periods for statistics
 */
object StatisticPeriod {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val QUARTERLY = "quarterly"
    const val YEARLY = "yearly"
    const val ALL_TIME = "all_time"
}

/**
 * Dashboard statistic configuration
 */
data class DashboardConfig(
    val userId: String = "",
    val visibleStatistics: List<String> = emptyList(),
    val statisticOrder: Map<String, Int> = emptyMap(),
    val refreshInterval: Int = 300, // seconds
    val theme: String = "default",
    val chartTypes: Map<String, String> = emptyMap(), // statistic_id to chart_type
    val filters: Map<String, String> = emptyMap()
)

/**
 * Statistic helper functions
 */
object StatisticHelper {
    
    /**
     * Calculate percentage change
     */
    fun calculatePercentageChange(current: Double, previous: Double): Double {
        return if (previous != 0.0) {
            ((current - previous) / previous) * 100
        } else {
            0.0
        }
    }
    
    /**
     * Determine trend based on percentage change
     */
    fun determineTrend(percentageChange: Double, threshold: Double = 0.1): StatisticTrend {
        return when {
            percentageChange > threshold -> StatisticTrend.UP
            percentageChange < -threshold -> StatisticTrend.DOWN
            else -> StatisticTrend.NEUTRAL
        }
    }
    
    /**
     * Format currency value
     */
    fun formatCurrency(amount: Double, currency: String = "VND"): String {
        return "${String.format("%,.0f", amount)} $currency"
    }
    
    /**
     * Format percentage
     */
    fun formatPercentage(value: Double): String {
        return "${String.format("%.1f", value)}%"
    }
    
    /**
     * Get default color for statistic type
     */
    fun getDefaultColor(type: String): String {
        return when (type) {
            StatisticType.TOTAL_REVENUE, StatisticType.MONTHLY_REVENUE -> "#4CAF50" // Green
            StatisticType.OCCUPANCY_RATE, StatisticType.OCCUPIED_ROOMS -> "#2196F3" // Blue
            StatisticType.MAINTENANCE_REQUESTS, StatisticType.PENDING_MAINTENANCE -> "#FF9800" // Orange
            StatisticType.TOTAL_USERS, StatisticType.ACTIVE_TENANTS -> "#9C27B0" // Purple
            StatisticType.COMPLAINTS -> "#F44336" // Red
            else -> "#757575" // Gray
        }
    }
    
    /**
     * Get default icon for statistic type
     */
    fun getDefaultIcon(type: String): String {
        return when (type) {
            StatisticType.TOTAL_REVENUE, StatisticType.MONTHLY_REVENUE -> "attach_money"
            StatisticType.OCCUPANCY_RATE -> "trending_up"
            StatisticType.TOTAL_ROOMS, StatisticType.OCCUPIED_ROOMS -> "home"
            StatisticType.MAINTENANCE_REQUESTS -> "build"
            StatisticType.TOTAL_USERS, StatisticType.ACTIVE_TENANTS -> "people"
            StatisticType.COMPLAINTS -> "report_problem"
            StatisticType.USER_SATISFACTION -> "sentiment_satisfied"
            else -> "analytics"
        }
    }
}