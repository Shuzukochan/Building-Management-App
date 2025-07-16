package com.app.buildingmanagement.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Date utility functions for the application
 * 
 * Provides common date formatting, parsing, and calculation utilities
 * used throughout the building management system.
 */
object DateUtils {
    
    private val vietnameseLocale = Locale("vi", "VN")
    
    // Date Formatters
    private val dateFormat = SimpleDateFormat(Constants.DateFormats.DATE_FORMAT, vietnameseLocale)
    private val timeFormat = SimpleDateFormat(Constants.DateFormats.TIME_FORMAT, vietnameseLocale)
    private val dateTimeFormat = SimpleDateFormat(Constants.DateFormats.DATE_TIME_FORMAT, vietnameseLocale)
    private val isoFormat = SimpleDateFormat(Constants.DateFormats.ISO_FORMAT, Locale.US)
    private val monthYearFormat = SimpleDateFormat(Constants.DateFormats.MONTH_YEAR_FORMAT, vietnameseLocale)
    private val displayDateFormat = SimpleDateFormat(Constants.DateFormats.DISPLAY_DATE_FORMAT, vietnameseLocale)
    
    /**
     * Format date to dd/MM/yyyy
     */
    fun formatDate(date: Date): String {
        return dateFormat.format(date)
    }
    
    /**
     * Format time to HH:mm
     */
    fun formatTime(date: Date): String {
        return timeFormat.format(date)
    }
    
    /**
     * Format date and time to dd/MM/yyyy HH:mm
     */
    fun formatDateTime(date: Date): String {
        return dateTimeFormat.format(date)
    }
    
    /**
     * Format date to ISO 8601 format
     */
    fun formatIso(date: Date): String {
        return isoFormat.format(date)
    }
    
    /**
     * Format date to MM/yyyy
     */
    fun formatMonthYear(date: Date): String {
        return monthYearFormat.format(date)
    }
    
    /**
     * Format date to display format (e.g., "Thứ Hai, 15 tháng 1 2024")
     */
    fun formatDisplayDate(date: Date): String {
        return displayDateFormat.format(date)
    }
    
    /**
     * Parse date from string in dd/MM/yyyy format
     */
    fun parseDate(dateString: String): Date? {
        return try {
            dateFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse date and time from string in dd/MM/yyyy HH:mm format
     */
    fun parseDateTime(dateTimeString: String): Date? {
        return try {
            dateTimeFormat.parse(dateTimeString)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse date from ISO 8601 format
     */
    fun parseIso(isoString: String): Date? {
        return try {
            isoFormat.parse(isoString)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get current date
     */
    fun getCurrentDate(): Date {
        return Date()
    }
    
    /**
     * Get current time in milliseconds
     */
    fun getCurrentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
    
    /**
     * Get start of day for given date
     */
    fun getStartOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
    
    /**
     * Get end of day for given date
     */
    fun getEndOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.time
    }
    
    /**
     * Get start of month for given date
     */
    fun getStartOfMonth(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
    
    /**
     * Get end of month for given date
     */
    fun getEndOfMonth(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.time
    }
    
    /**
     * Get start of year for given date
     */
    fun getStartOfYear(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
    
    /**
     * Get end of year for given date
     */
    fun getEndOfYear(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.time
    }
    
    /**
     * Add days to a date
     */
    fun addDays(date: Date, days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return calendar.time
    }
    
    /**
     * Add months to a date
     */
    fun addMonths(date: Date, months: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.MONTH, months)
        return calendar.time
    }
    
    /**
     * Add years to a date
     */
    fun addYears(date: Date, years: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.YEAR, years)
        return calendar.time
    }
    
    /**
     * Calculate difference in days between two dates
     */
    fun daysBetween(startDate: Date, endDate: Date): Int {
        val diffInMillis = endDate.time - startDate.time
        return (diffInMillis / (24 * 60 * 60 * 1000)).toInt()
    }
    
    /**
     * Calculate difference in months between two dates
     */
    fun monthsBetween(startDate: Date, endDate: Date): Int {
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        
        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate
        
        var months = 0
        while (startCalendar.before(endCalendar)) {
            startCalendar.add(Calendar.MONTH, 1)
            months++
        }
        
        return months
    }
    
    /**
     * Calculate difference in years between two dates
     */
    fun yearsBetween(startDate: Date, endDate: Date): Int {
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        
        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate
        
        return endCalendar.get(Calendar.YEAR) - startCalendar.get(Calendar.YEAR)
    }
    
    /**
     * Check if date is today
     */
    fun isToday(date: Date): Boolean {
        val today = getCurrentDate()
        return isSameDay(date, today)
    }
    
    /**
     * Check if date is yesterday
     */
    fun isYesterday(date: Date): Boolean {
        val yesterday = addDays(getCurrentDate(), -1)
        return isSameDay(date, yesterday)
    }
    
    /**
     * Check if date is tomorrow
     */
    fun isTomorrow(date: Date): Boolean {
        val tomorrow = addDays(getCurrentDate(), 1)
        return isSameDay(date, tomorrow)
    }
    
    /**
     * Check if two dates are on the same day
     */
    fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    /**
     * Check if two dates are in the same month
     */
    fun isSameMonth(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }
    
    /**
     * Check if date is in the past
     */
    fun isPast(date: Date): Boolean {
        return date.before(getCurrentDate())
    }
    
    /**
     * Check if date is in the future
     */
    fun isFuture(date: Date): Boolean {
        return date.after(getCurrentDate())
    }
    
    /**
     * Get relative time string (e.g., "2 giờ trước", "3 ngày sau")
     */
    fun getRelativeTime(date: Date): String {
        val now = getCurrentDate()
        val diffInMillis = date.time - now.time
        val absDiffInMillis = kotlin.math.abs(diffInMillis)
        
        val isPast = diffInMillis < 0
        val suffix = if (isPast) "trước" else "sau"
        
        return when {
            absDiffInMillis < 60_000 -> "Vừa xong" // Less than 1 minute
            absDiffInMillis < 3600_000 -> { // Less than 1 hour
                val minutes = (absDiffInMillis / 60_000).toInt()
                "$minutes phút $suffix"
            }
            absDiffInMillis < 86400_000 -> { // Less than 1 day
                val hours = (absDiffInMillis / 3600_000).toInt()
                "$hours giờ $suffix"
            }
            absDiffInMillis < 2592000_000 -> { // Less than 30 days
                val days = (absDiffInMillis / 86400_000).toInt()
                "$days ngày $suffix"
            }
            absDiffInMillis < 31536000_000 -> { // Less than 1 year
                val months = (absDiffInMillis / 2592000_000).toInt()
                "$months tháng $suffix"
            }
            else -> {
                val years = (absDiffInMillis / 31536000_000).toInt()
                "$years năm $suffix"
            }
        }
    }
    
    /**
     * Get age from birth date
     */
    fun getAge(birthDate: Date): Int {
        val now = Calendar.getInstance()
        val birth = Calendar.getInstance()
        birth.time = birthDate
        
        var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        
        if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        
        return age
    }
    
    /**
     * Get Vietnamese day of week name
     */
    fun getDayOfWeekVietnamese(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Thứ Hai"
            Calendar.TUESDAY -> "Thứ Ba"
            Calendar.WEDNESDAY -> "Thứ Tư"
            Calendar.THURSDAY -> "Thứ Năm"
            Calendar.FRIDAY -> "Thứ Sáu"
            Calendar.SATURDAY -> "Thứ Bảy"
            Calendar.SUNDAY -> "Chủ Nhật"
            else -> ""
        }
    }
    
    /**
     * Get Vietnamese month name
     */
    fun getMonthVietnamese(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        return when (calendar.get(Calendar.MONTH)) {
            Calendar.JANUARY -> "Tháng Một"
            Calendar.FEBRUARY -> "Tháng Hai"
            Calendar.MARCH -> "Tháng Ba"
            Calendar.APRIL -> "Tháng Tư"
            Calendar.MAY -> "Tháng Năm"
            Calendar.JUNE -> "Tháng Sáu"
            Calendar.JULY -> "Tháng Bảy"
            Calendar.AUGUST -> "Tháng Tám"
            Calendar.SEPTEMBER -> "Tháng Chín"
            Calendar.OCTOBER -> "Tháng Mười"
            Calendar.NOVEMBER -> "Tháng Mười Một"
            Calendar.DECEMBER -> "Tháng Mười Hai"
            else -> ""
        }
    }
    
    /**
     * Check if year is leap year
     */
    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
    
    /**
     * Get number of days in month
     */
    fun getDaysInMonth(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}