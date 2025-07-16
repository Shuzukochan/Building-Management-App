package com.app.buildingmanagement.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.buildingmanagement.models.Room
import com.app.buildingmanagement.utils.DateUtils

/**
 * Room Adapter for RecyclerView
 * 
 * Displays list of rooms with details including room number, status,
 * occupancy, and rent information. Supports click events and selection.
 */
class RoomAdapter(
    private val onRoomClick: (Room) -> Unit,
    private val onRoomLongClick: (Room) -> Boolean = { false }
) : ListAdapter<Room, RoomAdapter.RoomViewHolder>(RoomDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        // Since we're using Compose, we'll create a simple view for demonstration
        // In a real implementation, you might use Compose within RecyclerView
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return RoomViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(android.R.id.text1)
        private val subtitleText: TextView = itemView.findViewById(android.R.id.text2)
        
        fun bind(room: Room) {
            titleText.text = "Phòng ${room.roomNumber} - ${room.getStatusDisplayName()}"
            
            val subtitleBuilder = StringBuilder()
            subtitleBuilder.append("Loại: ${room.getTypeDisplayName()}")
            subtitleBuilder.append(" • Diện tích: ${room.area}m²")
            
            if (room.isOccupied()) {
                subtitleBuilder.append(" • Người thuê: ${room.currentOccupants}/${room.maxOccupants}")
            }
            
            subtitleBuilder.append(" • Giá: ${formatCurrency(room.monthlyRent)}")
            
            subtitleText.text = subtitleBuilder.toString()
            
            itemView.setOnClickListener {
                onRoomClick(room)
            }
            
            itemView.setOnLongClickListener {
                onRoomLongClick(room)
            }
            
            // Set background color based on status
            val backgroundColor = when (room.status) {
                "vacant" -> 0xFFE8F5E8.toInt() // Light green
                "occupied" -> 0xFFE3F2FD.toInt() // Light blue
                "maintenance" -> 0xFFFFF3E0.toInt() // Light orange
                "reserved" -> 0xFFF3E5F5.toInt() // Light purple
                else -> 0xFFFFFFFF.toInt() // White
            }
            
            itemView.setBackgroundColor(backgroundColor)
        }
    }
    
    private fun formatCurrency(amount: Double): String {
        return "${String.format("%,.0f", amount)} VND"
    }
    
    class RoomDiffCallback : DiffUtil.ItemCallback<Room>() {
        override fun areItemsTheSame(oldItem: Room, newItem: Room): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Room, newItem: Room): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Room Adapter for Compose LazyColumn
 * 
 * Alternative implementation for use with Jetpack Compose.
 * Provides the same functionality but designed for Compose UI.
 */
class RoomComposeAdapter {
    
    /**
     * Get room item for Compose
     */
    fun getRoomItem(
        room: Room,
        onRoomClick: (Room) -> Unit,
        onRoomLongClick: (Room) -> Unit = {}
    ): RoomItemData {
        return RoomItemData(
            room = room,
            title = "Phòng ${room.roomNumber}",
            subtitle = buildRoomSubtitle(room),
            statusText = room.getStatusDisplayName(),
            statusColor = getRoomStatusColor(room.status),
            priceText = formatCurrency(room.monthlyRent),
            onRoomClick = onRoomClick,
            onRoomLongClick = onRoomLongClick
        )
    }
    
    private fun buildRoomSubtitle(room: Room): String {
        val subtitleBuilder = StringBuilder()
        subtitleBuilder.append("${room.getTypeDisplayName()} • ${room.area}m²")
        
        if (room.isOccupied()) {
            subtitleBuilder.append(" • ${room.currentOccupants}/${room.maxOccupants} người")
        }
        
        if (room.floor > 0) {
            subtitleBuilder.append(" • Tầng ${room.floor}")
        }
        
        return subtitleBuilder.toString()
    }
    
    private fun getRoomStatusColor(status: String): Long {
        return when (status) {
            "vacant" -> 0xFF4CAF50 // Green
            "occupied" -> 0xFF2196F3 // Blue
            "maintenance" -> 0xFFFF9800 // Orange
            "reserved" -> 0xFF9C27B0 // Purple
            else -> 0xFF757575 // Gray
        }
    }
    
    private fun formatCurrency(amount: Double): String {
        return "${String.format("%,.0f", amount)} VND"
    }
}

/**
 * Room item data for Compose
 */
data class RoomItemData(
    val room: Room,
    val title: String,
    val subtitle: String,
    val statusText: String,
    val statusColor: Long,
    val priceText: String,
    val onRoomClick: (Room) -> Unit,
    val onRoomLongClick: (Room) -> Unit
)

/**
 * Room filter options
 */
data class RoomFilter(
    val status: String? = null,
    val type: String? = null,
    val floor: Int? = null,
    val minRent: Double? = null,
    val maxRent: Double? = null,
    val minArea: Double? = null,
    val maxArea: Double? = null,
    val hasUtilities: List<String> = emptyList()
) {
    fun matches(room: Room): Boolean {
        if (status != null && room.status != status) return false
        if (type != null && room.type != type) return false
        if (floor != null && room.floor != floor) return false
        if (minRent != null && room.monthlyRent < minRent) return false
        if (maxRent != null && room.monthlyRent > maxRent) return false
        if (minArea != null && room.area < minArea) return false
        if (maxArea != null && room.area > maxArea) return false
        
        if (hasUtilities.isNotEmpty()) {
            val roomUtilities = room.utilities.getIncludedUtilities()
            if (!hasUtilities.all { it in roomUtilities }) return false
        }
        
        return true
    }
}

/**
 * Room sorting options
 */
enum class RoomSortOption(val displayName: String) {
    ROOM_NUMBER("Số phòng"),
    PRICE_LOW_TO_HIGH("Giá: Thấp đến cao"),
    PRICE_HIGH_TO_LOW("Giá: Cao đến thấp"),
    AREA_SMALL_TO_LARGE("Diện tích: Nhỏ đến lớn"),
    AREA_LARGE_TO_SMALL("Diện tích: Lớn đến nhỏ"),
    FLOOR("Tầng"),
    STATUS("Trạng thái"),
    UPDATED_DATE("Ngày cập nhật")
}

/**
 * Room sorting utility
 */
object RoomSorter {
    fun sortRooms(rooms: List<Room>, sortOption: RoomSortOption): List<Room> {
        return when (sortOption) {
            RoomSortOption.ROOM_NUMBER -> rooms.sortedBy { it.roomNumber }
            RoomSortOption.PRICE_LOW_TO_HIGH -> rooms.sortedBy { it.monthlyRent }
            RoomSortOption.PRICE_HIGH_TO_LOW -> rooms.sortedByDescending { it.monthlyRent }
            RoomSortOption.AREA_SMALL_TO_LARGE -> rooms.sortedBy { it.area }
            RoomSortOption.AREA_LARGE_TO_SMALL -> rooms.sortedByDescending { it.area }
            RoomSortOption.FLOOR -> rooms.sortedBy { it.floor }
            RoomSortOption.STATUS -> rooms.sortedBy { it.status }
            RoomSortOption.UPDATED_DATE -> rooms.sortedByDescending { it.updatedDate }
        }
    }
}