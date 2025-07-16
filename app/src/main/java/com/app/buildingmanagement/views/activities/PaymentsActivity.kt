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
import com.app.buildingmanagement.controllers.PaymentController
import com.app.buildingmanagement.models.Payment
import com.app.buildingmanagement.models.PaymentUrgency
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Payments Activity
 * 
 * Displays payment management interface including payment list,
 * payment processing, and payment history.
 */
class PaymentsActivity : ComponentActivity() {
    
    private val paymentController = PaymentController()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BuildingManagementTheme {
                PaymentsScreen()
            }
        }
        
        // Load initial data
        lifecycleScope.launch {
            paymentController.loadAllPayments()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PaymentsScreen() {
        val payments by paymentController.payments.collectAsState()
        val isLoading by paymentController.isLoading.collectAsState()
        val error by paymentController.error.collectAsState()
        val isProcessing by paymentController.isProcessing.collectAsState()
        
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Tất cả", "Chờ xử lý", "Đã thanh toán", "Quá hạn")
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Quản lý thanh toán") },
                    actions = {
                        IconButton(
                            onClick = {
                                lifecycleScope.launch {
                                    paymentController.loadAllPayments()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        // TODO: Navigate to create payment screen
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Payment")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Row
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                
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
                                paymentController.clearError()
                                paymentController.loadAllPayments()
                            }
                        }
                    )
                } else {
                    val filteredPayments = filterPaymentsByTab(payments, selectedTab)
                    PaymentsList(
                        payments = filteredPayments,
                        isProcessing = isProcessing,
                        onPaymentAction = { paymentId, action ->
                            lifecycleScope.launch {
                                when (action) {
                                    "pay" -> paymentController.processPayment(paymentId, "bank_transfer")
                                    "cancel" -> paymentController.cancelPayment(paymentId)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    @Composable
    private fun PaymentsList(
        payments: List<Payment>,
        isProcessing: Boolean,
        onPaymentAction: (String, String) -> Unit
    ) {
        if (payments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = "No payments",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Không có thanh toán nào",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(payments) { payment ->
                    PaymentCard(
                        payment = payment,
                        isProcessing = isProcessing,
                        onPaymentAction = onPaymentAction
                    )
                }
            }
        }
    }
    
    @Composable
    private fun PaymentCard(
        payment: Payment,
        isProcessing: Boolean,
        onPaymentAction: (String, String) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            text = payment.getTypeDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "ID: ${payment.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Đến hạn: ${dateFormatter.format(payment.dueDate)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        if (payment.description?.isNotEmpty() == true) {
                            Text(
                                text = payment.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = payment.getFormattedAmount(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        PaymentStatusChip(payment = payment)
                    }
                }
                
                if (payment.status == "pending") {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onPaymentAction(payment.id, "pay") },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Thanh toán")
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { onPaymentAction(payment.id, "cancel") },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hủy")
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun PaymentStatusChip(payment: Payment) {
        val urgency = payment.getUrgencyLevel()
        val (backgroundColor, contentColor) = when (urgency) {
            PaymentUrgency.PAID -> Pair(Color(0xFF4CAF50), Color.White)
            PaymentUrgency.OVERDUE -> Pair(Color(0xFFE57373), Color.White)
            PaymentUrgency.URGENT -> Pair(Color(0xFFFF9800), Color.White)
            PaymentUrgency.WARNING -> Pair(Color(0xFFFFEB3B), Color.Black)
            PaymentUrgency.NORMAL -> Pair(Color(0xFF9E9E9E), Color.White)
        }
        
        AssistChip(
            onClick = { },
            label = { Text(payment.getStatusDisplayName()) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = backgroundColor,
                labelColor = contentColor
            )
        )
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
    
    private fun filterPaymentsByTab(payments: List<Payment>, tabIndex: Int): List<Payment> {
        return when (tabIndex) {
            1 -> payments.filter { it.status == "pending" }
            2 -> payments.filter { it.status == "paid" }
            3 -> payments.filter { it.isOverdue() }
            else -> payments
        }
    }
}