package com.app.buildingmanagement.fragment.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PrivateConnectivity
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.buildingmanagement.R
import com.app.buildingmanagement.data.FirebaseDataState
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog

@Composable
fun FeedbackBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }
    var isAnonymous by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val bottomSheetDialog = BottomSheetDialog(context)
        
        // Enable drag handle and animations
        bottomSheetDialog.behavior.isDraggable = true
        bottomSheetDialog.behavior.isFitToContents = true
        bottomSheetDialog.behavior.skipCollapsed = true
        
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FeedbackBottomSheetContent(
                    feedbackText = feedbackText,
                    onFeedbackTextChange = { feedbackText = it },
                    isAnonymous = isAnonymous,
                    onAnonymousChange = { isAnonymous = it },
                    onSubmit = {
                        if (feedbackText.isNotBlank()) {
                            submitFeedback(context, feedbackText, isAnonymous)
                            bottomSheetDialog.dismiss()
                        } else {
                            Toast.makeText(context, "Vui lòng nhập nội dung góp ý", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancel = {
                        bottomSheetDialog.dismiss()
                    }
                )
            }
        }
        
        bottomSheetDialog.setContentView(composeView)
        bottomSheetDialog.setOnDismissListener { onDismiss() }
        bottomSheetDialog.show()
    }
}

@Composable
private fun FeedbackBottomSheetContent(
    feedbackText: String,
    onFeedbackTextChange: (String) -> Unit,
    isAnonymous: Boolean,
    onAnonymousChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        Color(0xFFE0E0E0),
                        RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFC8E6C9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Feedback,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Góp ý",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Gửi ý kiến của bạn",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Input section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE9ECEF), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Nội dung góp ý",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = feedbackText,
                        onValueChange = onFeedbackTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = Color.Black
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (feedbackText.isEmpty()) {
                                    Text(
                                        text = "Nhập ý kiến của bạn...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF546E7A)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Privacy option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PrivateConnectivity,
                        contentDescription = null,
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Góp ý ẩn danh",
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isAnonymous,
                        onCheckedChange = onAnonymousChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hủy", color = Color(0xFF666666))
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Gửi", color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val bottomSheetDialog = BottomSheetDialog(context)

        // Enable drag handle and animations
        bottomSheetDialog.behavior.isDraggable = true
        bottomSheetDialog.behavior.isFitToContents = true
        bottomSheetDialog.behavior.skipCollapsed = true

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AboutBottomSheetContent()
            }
        }

        bottomSheetDialog.setContentView(composeView)
        bottomSheetDialog.setOnDismissListener { onDismiss() }
        bottomSheetDialog.show()
    }
}

@Composable
private fun AboutBottomSheetContent() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        Color(0xFFE0E0E0),
                        RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF3E5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF7B1FA2),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Về ứng dụng",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Thông tin ứng dụng",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logo section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_hcmute),
                            contentDescription = "Logo HCMUTE",
                            modifier = Modifier
                                .height(56.dp)
                                .width(56.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.logo_iot_vision),
                            contentDescription = "Logo IoT Vision",
                            modifier = Modifier.height(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Building Management",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    val versionName = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (_: Exception) { "1.0.0" }

                    Text(
                        text = "Phiên bản $versionName",
                        fontSize = 13.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Description section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Giới thiệu đồ án",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ứng dụng quản lý tòa nhà thông minh với IoT, giúp theo dõi và quản lý tiêu thụ năng lượng, thanh toán hóa đơn một cách tiện lợi.",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Team info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Nhóm thực hiện",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    listOf(
                        "Sinh viên 1" to "Nguyễn Bá Uy - 21146362",
                        "Sinh viên 2" to "Trần Văn Huy - 21146552",
                        "Sinh viên 3" to "Huỳnh Quang Vũ - 21146366",
                        "GVHD" to "TS. Nguyễn Văn Thái"
                    ).forEach { (label, info) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                color = Color(0xFF546E7A),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = info,
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Copyright
            Text(
                text = "Đại học Sư phạm Kỹ thuật TP.HCM\n© 2025 Building Management",
                fontSize = 12.sp,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var paymentList by remember { mutableStateOf<List<Triple<Long, String, String>>>(emptyList()) } // amount, timestamp, status
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val roomNumber = if (FirebaseDataState.isDataLoaded) {
            FirebaseDataState.roomNumber
        } else {
            null
        }
        
        val buildingId = if (FirebaseDataState.isUserDataLoaded) {
            FirebaseDataState.buildingId
        } else {
            null
        }

        if (roomNumber != null) {
            try {
                Log.d("PaymentHistory", "🔍 Loading payments for roomNumber: $roomNumber, buildingId: $buildingId")
                val database = FirebaseDatabase.getInstance()
                val payments = mutableListOf<Triple<Long, String, String>>()
                var completedQueries = 0
                val totalQueries = if (buildingId != null) 4 else 2

                fun checkComplete() {
                    completedQueries++
                    Log.d("PaymentHistory", "✅ Query $completedQueries/$totalQueries completed. Total payments found: ${payments.size}")
                    if (completedQueries >= totalQueries) {
                        payments.sortByDescending { it.second }
                        paymentList = payments
                        isLoading = false
                        
                        if (payments.isEmpty()) {
                            Log.w("PaymentHistory", "⚠️ No payments found in any structure")
                            errorMessage = "Chưa có lịch sử thanh toán nào"
                        } else {
                            Log.d("PaymentHistory", "🎉 Found ${payments.size} payments total")
                        }
                    }
                }

                // Debug query to see Firebase structure
                database.getReference().addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        checkComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        checkComplete()
                    }
                })

                // Debug building structure if buildingId exists
                if (buildingId != null) {
                    database.getReference("buildings").child(buildingId).child("rooms").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            checkComplete()
                        }
                        override fun onCancelled(error: DatabaseError) {
                            checkComplete()
                        }
                    })
                } else {
                    checkComplete()
                }

                // Query 1: Old structure
                val oldPaymentsRef = database.getReference("rooms")
                    .child(roomNumber)
                    .child("payments")

                oldPaymentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            for (monthSnapshot in snapshot.children) {
                                val monthKey = monthSnapshot.key ?: continue
                                val amount = monthSnapshot.child("amount").getValue(Long::class.java) ?: 0
                                val timestamp = monthSnapshot.child("timestamp").getValue(String::class.java) ?: ""
                                val status = monthSnapshot.child("status").getValue(String::class.java) ?: ""

                                if (amount > 0) {
                                    val finalTimestamp = timestamp.ifEmpty {
                                        "${monthKey}-01_00-00-00"
                                    }
                                    payments.add(Triple(amount, finalTimestamp, status))
                                }
                            }
                        }
                        checkComplete()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        checkComplete()
                    }
                })

                // Query 2 & 3: New structure if buildingId available
                if (buildingId != null) {
                    // Try with roomNumber as key
                    val newPaymentsRef1 = database.getReference("buildings")
                        .child(buildingId)
                        .child("rooms")
                        .child(roomNumber)
                        .child("payments")

                    newPaymentsRef1.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                for (monthSnapshot in snapshot.children) {
                                    val monthKey = monthSnapshot.key ?: continue
                                    val amount = monthSnapshot.child("amount").getValue(Long::class.java) ?: 0
                                    val timestamp = monthSnapshot.child("timestamp").getValue(String::class.java) ?: ""
                                    val status = monthSnapshot.child("status").getValue(String::class.java) ?: ""

                                    if (amount > 0) {
                                        val finalTimestamp = timestamp.ifEmpty {
                                            "${monthKey}-01_00-00-00"
                                        }
                                        payments.add(Triple(amount, finalTimestamp, status))
                                    }
                                }
                            }
                            checkComplete()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            checkComplete()
                        }
                    })

                    // Try with roomNumber without "Phòng " prefix
                    val cleanRoomNumber = roomNumber.replace("Phòng ", "")
                    val newPaymentsRef2 = database.getReference("buildings")
                        .child(buildingId)
                        .child("rooms")
                        .child(cleanRoomNumber)
                        .child("payments")

                    newPaymentsRef2.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                for (monthSnapshot in snapshot.children) {
                                    val monthKey = monthSnapshot.key ?: continue
                                    val amount = monthSnapshot.child("amount").getValue(Long::class.java) ?: 0
                                    val timestamp = monthSnapshot.child("timestamp").getValue(String::class.java) ?: ""
                                    val status = monthSnapshot.child("status").getValue(String::class.java) ?: ""

                                    if (amount > 0) {
                                        val finalTimestamp = timestamp.ifEmpty {
                                            "${monthKey}-01_00-00-00"
                                        }
                                        payments.add(Triple(amount, finalTimestamp, status))
                                    }
                                }
                            }
                            checkComplete()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            checkComplete()
                        }
                    })
                }

            } catch (e: Exception) {
                errorMessage = "Có lỗi xảy ra: ${e.message}"
                isLoading = false
            }
        } else {
            errorMessage = "Không tìm thấy thông tin phòng"
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val bottomSheetDialog = BottomSheetDialog(context)

        // Enable drag handle and animations
        bottomSheetDialog.behavior.isDraggable = true
        bottomSheetDialog.behavior.isFitToContents = true
        bottomSheetDialog.behavior.skipCollapsed = true

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PaymentHistoryBottomSheetContent(
                    paymentList = paymentList,
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
        }

        bottomSheetDialog.setContentView(composeView)
        bottomSheetDialog.setOnDismissListener { onDismiss() }
        bottomSheetDialog.show()
    }
}

@Composable
private fun PaymentHistoryBottomSheetContent(
    paymentList: List<Triple<Long, String, String>>,
    isLoading: Boolean,
    errorMessage: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        Color(0xFFE0E0E0),
                        RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFE3F2FD), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = Color(0xFF1976D2),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Lịch sử thanh toán",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Xem các giao dịch đã thực hiện",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content section - remove outer card, show content directly
            when {
                isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Đang tải...",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }

                errorMessage != null -> {
                    // Error state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF666666),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                paymentList.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = Color(0xFFBDBDBD),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Chưa có lịch sử thanh toán",
                                textAlign = TextAlign.Center,
                                color = Color(0xFF666666),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                else -> {
                    // Payment list - no outer card, just LazyColumn with individual cards
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp, max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(paymentList) { payment ->
                            PaymentHistoryItemCard(payment = payment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentHistoryItemCard(
    payment: Triple<Long, String, String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFE0E0E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = when (payment.third.lowercase()) {
                            "paid", "đã thanh toán" -> Color(0xFFE8F5E9)
                            "pending", "chờ xử lý" -> Color(0xFFFFF3E0)
                            else -> Color(0xFFFFEBEE)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (payment.third.lowercase()) {
                        "paid", "đã thanh toán" -> Icons.Default.CheckCircle
                        "pending", "chờ xử lý" -> Icons.Default.History
                        else -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when (payment.third.lowercase()) {
                        "paid", "đã thanh toán" -> Color(0xFF43A047)
                        "pending", "chờ xử lý" -> Color(0xFFFFA000)
                        else -> Color(0xFFD32F2F)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Payment info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatCurrency(payment.first),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatTimestamp(payment.second),
                    fontSize = 13.sp,
                    color = Color(0xFF666666)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Status badge
            Surface(
                color = when (payment.third.lowercase()) {
                    "paid", "đã thanh toán" -> Color(0xFFE8F5E9)
                    "pending", "chờ xử lý" -> Color(0xFFFFF3E0)
                    else -> Color(0xFFFFEBEE)
                },
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = when (payment.third.lowercase()) {
                        "paid", "đã thanh toán" -> Color(0xFF43A047).copy(alpha = 0.3f)
                        "pending", "chờ xử lý" -> Color(0xFFFFA000).copy(alpha = 0.3f)
                        else -> Color(0xFFD32F2F).copy(alpha = 0.3f)
                    }
                )
            ) {
                Text(
                    text = when (payment.third.lowercase()) {
                        "paid", "đã thanh toán" -> "Đã thanh toán"
                        "pending", "chờ xử lý" -> "Chờ xử lý"
                        else -> payment.third
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (payment.third.lowercase()) {
                        "paid", "đã thanh toán" -> Color(0xFF43A047)
                        "pending", "chờ xử lý" -> Color(0xFFFFA000)
                        else -> Color(0xFFD32F2F)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
