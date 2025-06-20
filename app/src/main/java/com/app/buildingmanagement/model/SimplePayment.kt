package com.app.buildingmanagement.model

import java.text.NumberFormat
import java.util.*

data class SimplePayment(
    val amount: Long,
    val timestamp: String,
    val status: String
) {
    fun getFormattedAmount(): String {
        val formatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))
        return "${formatter.format(amount)} VNĐ"
    }

    fun getFormattedDate(): String {
        return try {
            val parts = timestamp.split(" ")
            if (parts.size >= 2) {
                val datePart = parts[0] // 2025-06-13
                val timePart = parts[1] // 00:52:00

                val dateComponents = datePart.split("-")
                val timeComponents = timePart.split(":")

                if (dateComponents.size >= 3 && timeComponents.size >= 2) {
                    "${dateComponents[2]}/${dateComponents[1]}/${dateComponents[0]} ${timeComponents[0]}:${timeComponents[1]}"
                } else {
                    timestamp
                }
            } else {
                timestamp
            }
        } catch (e: Exception) {
            timestamp
        }
    }
}
