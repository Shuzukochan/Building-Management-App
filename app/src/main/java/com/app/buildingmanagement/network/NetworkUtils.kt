package com.app.buildingmanagement.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

/**
 * Network utility functions for connectivity and network operations
 * 
 * Provides methods to check network connectivity, monitor network changes,
 * and handle network-related operations in the building management app.
 */
object NetworkUtils {
    
    /**
     * Check if device has internet connectivity
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }
    
    /**
     * Check if device is connected to WiFi
     */
    fun isWiFiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            networkInfo?.isConnected ?: false
        }
    }
    
    /**
     * Check if device is connected to mobile data
     */
    fun isMobileDataConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
            networkInfo?.isConnected ?: false
        }
    }
    
    /**
     * Get network connection type
     */
    fun getConnectionType(context: Context): ConnectionType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.NONE
            
            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return ConnectionType.NONE
            
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> ConnectionType.WIFI
                ConnectivityManager.TYPE_MOBILE -> ConnectionType.MOBILE
                ConnectivityManager.TYPE_ETHERNET -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
        }
    }
    
    /**
     * Check if device has fast internet connection
     */
    fun isFastConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // Check if it's 4G or better
                    networkCapabilities.linkDownstreamBandwidthKbps > 1000 // > 1 Mbps
                }
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> true
                ConnectivityManager.TYPE_ETHERNET -> true
                ConnectivityManager.TYPE_MOBILE -> {
                    when (networkInfo.subtype) {
                        android.telephony.TelephonyManager.NETWORK_TYPE_LTE,
                        android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP,
                        android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD -> true
                        else -> false
                    }
                }
                else -> false
            }
        }
    }
    
    /**
     * Check internet connectivity by pinging a server
     */
    suspend fun hasInternetConnectivity(
        host: String = "8.8.8.8",
        port: Int = 53,
        timeoutMs: Int = 3000
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.close()
                true
            } catch (e: IOException) {
                false
            } catch (e: UnknownHostException) {
                false
            }
        }
    }
    
    /**
     * Get network signal strength (for mobile connections)
     */
    fun getSignalStrength(context: Context): SignalStrength {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return SignalStrength.UNKNOWN
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return SignalStrength.UNKNOWN
            
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return SignalStrength.EXCELLENT // WiFi generally has good signal
            }
            
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                val signalStrengthValue = networkCapabilities.signalStrength
                return when {
                    signalStrengthValue >= -70 -> SignalStrength.EXCELLENT
                    signalStrengthValue >= -85 -> SignalStrength.GOOD
                    signalStrengthValue >= -100 -> SignalStrength.FAIR
                    signalStrengthValue >= -110 -> SignalStrength.POOR
                    else -> SignalStrength.VERY_POOR
                }
            }
        }
        
        return SignalStrength.UNKNOWN
    }
    
    /**
     * Get network operator name
     */
    fun getNetworkOperatorName(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            telephonyManager.networkOperatorName
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if device is in roaming
     */
    fun isRoaming(context: Context): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            telephonyManager.isNetworkRoaming
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get data usage information (requires appropriate permissions)
     */
    fun getDataUsageInfo(context: Context): DataUsageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork ?: return null
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
                
                DataUsageInfo(
                    downstreamBandwidth = networkCapabilities.linkDownstreamBandwidthKbps,
                    upstreamBandwidth = networkCapabilities.linkUpstreamBandwidthKbps
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Format network speed for display
     */
    fun formatNetworkSpeed(speedKbps: Int): String {
        return when {
            speedKbps >= 1024 * 1024 -> String.format("%.1f Gbps", speedKbps / (1024.0 * 1024.0))
            speedKbps >= 1024 -> String.format("%.1f Mbps", speedKbps / 1024.0)
            else -> "$speedKbps Kbps"
        }
    }
    
    /**
     * Get network connection info summary
     */
    fun getNetworkInfo(context: Context): NetworkInfo {
        val isConnected = isNetworkAvailable(context)
        val connectionType = getConnectionType(context)
        val isWiFi = isWiFiConnected(context)
        val isMobile = isMobileDataConnected(context)
        val isFast = isFastConnection(context)
        val signalStrength = getSignalStrength(context)
        val operatorName = getNetworkOperatorName(context)
        val isRoaming = isRoaming(context)
        val dataUsage = getDataUsageInfo(context)
        
        return NetworkInfo(
            isConnected = isConnected,
            connectionType = connectionType,
            isWiFi = isWiFi,
            isMobile = isMobile,
            isFast = isFast,
            signalStrength = signalStrength,
            operatorName = operatorName,
            isRoaming = isRoaming,
            dataUsageInfo = dataUsage
        )
    }
    
    /**
     * Retry network operation with exponential backoff
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        factor: Double = 2.0,
        operation: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
                }
            }
        }
        
        throw lastException ?: Exception("Unknown error in retry operation")
    }
}

/**
 * Network connection types
 */
enum class ConnectionType {
    NONE,
    WIFI,
    MOBILE,
    ETHERNET,
    UNKNOWN
}

/**
 * Signal strength levels
 */
enum class SignalStrength {
    VERY_POOR,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT,
    UNKNOWN
}

/**
 * Data usage information
 */
data class DataUsageInfo(
    val downstreamBandwidth: Int, // in Kbps
    val upstreamBandwidth: Int    // in Kbps
)

/**
 * Complete network information
 */
data class NetworkInfo(
    val isConnected: Boolean,
    val connectionType: ConnectionType,
    val isWiFi: Boolean,
    val isMobile: Boolean,
    val isFast: Boolean,
    val signalStrength: SignalStrength,
    val operatorName: String?,
    val isRoaming: Boolean,
    val dataUsageInfo: DataUsageInfo?
)