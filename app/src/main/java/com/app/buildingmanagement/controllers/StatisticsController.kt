package com.app.buildingmanagement.controllers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.app.buildingmanagement.models.Statistic
import com.app.buildingmanagement.repositories.UserRepository
import com.app.buildingmanagement.repositories.PaymentRepository
import com.app.buildingmanagement.repositories.RoomRepository
import java.util.Date
import java.util.Calendar

/**
 * Statistics Controller
 * 
 * Manages business logic for statistics and dashboard data.
 * Handles data aggregation, calculations, and state management for statistics UI.
 */
class StatisticsController {
    
    private val userRepository = UserRepository()
    private val paymentRepository = PaymentRepository()
    private val roomRepository = RoomRepository()
    
    private val _statistics = MutableStateFlow<List<Statistic>>(emptyList())
    val statistics: StateFlow<List<Statistic>> = _statistics.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Load all statistics data
     */
    suspend fun loadStatistics() {
        try {
            _isLoading.value = true
            _error.value = null
            
            val stats = mutableListOf<Statistic>()
            
            // Get user statistics
            val totalUsers = userRepository.getTotalUsersCount()
            stats.add(Statistic(
                id = "total_users",
                title = "Tổng số người dùng",
                value = totalUsers.toString(),
                icon = "person",
                color = "#4CAF50"
            ))
            
            // Get room statistics
            val totalRooms = roomRepository.getTotalRoomsCount()
            val occupiedRooms = roomRepository.getOccupiedRoomsCount()
            val occupancyRate = if (totalRooms > 0) {
                ((occupiedRooms.toDouble() / totalRooms) * 100).toInt()
            } else 0
            
            stats.add(Statistic(
                id = "total_rooms",
                title = "Tổng số phòng",
                value = totalRooms.toString(),
                icon = "home",
                color = "#2196F3"
            ))
            
            stats.add(Statistic(
                id = "occupancy_rate",
                title = "Tỷ lệ lấp đầy",
                value = "$occupancyRate%",
                icon = "trending_up",
                color = "#FF9800"
            ))
            
            // Get payment statistics
            val thisMonthRevenue = paymentRepository.getMonthlyRevenue(getCurrentMonth())
            val pendingPayments = paymentRepository.getPendingPaymentsCount()
            
            stats.add(Statistic(
                id = "monthly_revenue",
                title = "Doanh thu tháng này",
                value = formatCurrency(thisMonthRevenue),
                icon = "attach_money",
                color = "#4CAF50"
            ))
            
            stats.add(Statistic(
                id = "pending_payments",
                title = "Thanh toán chờ xử lý",
                value = pendingPayments.toString(),
                icon = "schedule",
                color = "#F44336"
            ))
            
            _statistics.value = stats
            
        } catch (e: Exception) {
            _error.value = "Lỗi khi tải thống kê: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Get monthly revenue statistics for chart
     */
    suspend fun getMonthlyRevenueChart(year: Int): List<Pair<String, Double>> {
        return try {
            val monthlyData = mutableListOf<Pair<String, Double>>()
            
            for (month in 1..12) {
                val revenue = paymentRepository.getMonthlyRevenue(month, year)
                val monthName = getMonthName(month)
                monthlyData.add(Pair(monthName, revenue))
            }
            
            monthlyData
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get room occupancy statistics
     */
    suspend fun getRoomOccupancyStats(): Map<String, Int> {
        return try {
            mapOf(
                "occupied" to roomRepository.getOccupiedRoomsCount(),
                "vacant" to roomRepository.getVacantRoomsCount(),
                "maintenance" to roomRepository.getMaintenanceRoomsCount()
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Get payment status distribution
     */
    suspend fun getPaymentStatusStats(): Map<String, Int> {
        return try {
            mapOf(
                "paid" to paymentRepository.getPaidPaymentsCount(),
                "pending" to paymentRepository.getPendingPaymentsCount(),
                "overdue" to paymentRepository.getOverduePaymentsCount()
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Get daily revenue for current month
     */
    suspend fun getDailyRevenueCurrentMonth(): List<Pair<Int, Double>> {
        return try {
            paymentRepository.getDailyRevenueForMonth(getCurrentMonth(), getCurrentYear())
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Refresh all statistics
     */
    suspend fun refresh() {
        loadStatistics()
    }
    
    // Helper functions
    private fun getCurrentMonth(): Int {
        return Calendar.getInstance().get(Calendar.MONTH) + 1
    }
    
    private fun getCurrentYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR)
    }
    
    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "Th1"
            2 -> "Th2"
            3 -> "Th3"
            4 -> "Th4"
            5 -> "Th5"
            6 -> "Th6"
            7 -> "Th7"
            8 -> "Th8"
            9 -> "Th9"
            10 -> "Th10"
            11 -> "Th11"
            12 -> "Th12"
            else -> "N/A"
        }
    }
    
    private fun formatCurrency(amount: Double): String {
        return "${String.format("%,.0f", amount)} VND"
    }
}