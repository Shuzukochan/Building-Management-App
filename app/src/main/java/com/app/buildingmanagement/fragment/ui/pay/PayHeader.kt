package com.app.buildingmanagement.fragment.ui.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.DpOffset
import com.app.buildingmanagement.R

@Composable
fun PayHeader(
    selectedMonth: String,
    monthOptions: List<String>,
    monthKeys: List<String>,
    onMonthSelected: (String) -> Unit,
    titleTextSize: TextUnit,
    bodyTextSize: TextUnit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var triggerWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Title - sử dụng cùng style như HomeFragment
        Text(
            text = "Thanh toán",
            fontSize = titleTextSize,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        
        Spacer(modifier = Modifier.height(com.app.buildingmanagement.fragment.ui.home.HomeConstants.SPACING_XXL.dp))
        
        // Month Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(com.app.buildingmanagement.fragment.ui.home.HomeConstants.CARD_CORNER_RADIUS.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = com.app.buildingmanagement.fragment.ui.home.HomeConstants.CARD_ELEVATION_DEFAULT.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Calendar",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Chọn tháng:",
                    fontSize = bodyTextSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E2E2E),
                    modifier = Modifier.padding(end = 6.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            triggerWidth = with(density) { coordinates.size.width.toDp() }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedMonth.isNotEmpty() && monthKeys.contains(selectedMonth)) {
                                val index = monthKeys.indexOf(selectedMonth)
                                if (index >= 0 && index < monthOptions.size) monthOptions[index] else selectedMonth
                            } else "Chọn tháng",
                            fontSize = bodyTextSize,
                            color = Color(0xFF444444)
                        )
                        
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.width(triggerWidth),
                        offset = DpOffset(0.dp, 8.dp)
                    ) {
                        monthOptions.forEachIndexed { index, displayMonth ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = displayMonth,
                                        fontSize = bodyTextSize
                                    )
                                },
                                onClick = {
                                    if (index < monthKeys.size) {
                                        onMonthSelected(monthKeys[index])
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
} 