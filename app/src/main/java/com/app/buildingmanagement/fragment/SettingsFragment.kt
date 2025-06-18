package com.app.buildingmanagement.fragment

import android.Manifest
import android.app.NotificationChannel
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.buildingmanagement.R
import com.app.buildingmanagement.SignInActivity
import com.app.buildingmanagement.adapter.SimplePaymentAdapter
import com.app.buildingmanagement.databinding.FragmentSettingsBinding
import com.app.buildingmanagement.model.SimplePayment
import com.app.buildingmanagement.data.SharedDataManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import android.app.NotificationManager

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    private lateinit var auth: FirebaseAuth
    private var currentRoomNumber: String? = null
    private var isUpdatingSwitch = false // Flag để tránh infinite loop

    companion object {
        private const val TAG = "SettingsFragment"
    }

    // Permission launcher cho notification
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handleNotificationPermissionResult(isGranted)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val phone = user?.phoneNumber

        binding?.tvPhoneNumber?.text = phone?.replace("+84", "0") ?: "Chưa có số điện thoại"

        // Kiểm tra cache trước
        val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()
        if (cachedRoomNumber != null) {
            Log.d(TAG, "Using cached room number: $cachedRoomNumber")
            currentRoomNumber = cachedRoomNumber
            binding?.tvRoomNumber?.text = "Phòng $cachedRoomNumber"
        } else if (phone != null) {
            Log.d(TAG, "No cache, loading from Firebase")
            loadRoomNumberFromFirebase(phone)
        } else {
            binding?.tvRoomNumber?.text = "Không xác định"
            currentRoomNumber = null
        }

        binding?.btnSignOut?.setOnClickListener {
            showLogoutConfirmation()
        }

        // Cập nhật click listener cho payment history với Bottom Sheet
        binding?.btnPaymentHistory?.setOnClickListener {
            showPaymentHistoryBottomSheet()
        }

        // Setup notification switch
        setupNotificationSwitch()

        binding?.btnFeedback?.setOnClickListener {
            showFeedbackBottomSheet()
        }

        binding?.btnSupport?.setOnClickListener {
            openDialer("0398103352")
        }

        binding?.btnAbout?.setOnClickListener {
            showAboutBottomSheet()
        }

        return binding!!.root
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật trạng thái switch khi quay lại fragment
        updateNotificationSwitchState()
    }

    private fun setupNotificationSwitch() {
        // Set initial state
        updateNotificationSwitchState()

        // Handle layout click
        binding?.layoutNotifications?.setOnClickListener {
            if (!isUpdatingSwitch) {
                val currentState = binding?.switchNotifications?.isChecked ?: false
                handleNotificationToggle(!currentState)
            }
        }

        // Handle switch toggle
        binding?.switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            val sharedPref = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("notifications_enabled", isChecked)
                apply()
            }

            // CẬP NHẬT NOTIFICATION CHANNEL KHI USER THAY ĐỔI SETTING
            updateNotificationChannel(isChecked)

            val message = if (isChecked) "Đã bật thông báo" else "Đã tắt thông báo"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

    }

    private fun updateNotificationChannel(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "fcm_default_channel"

            val importance = if (enabled) {
                NotificationManager.IMPORTANCE_DEFAULT
            } else {
                NotificationManager.IMPORTANCE_NONE
            }

            val channel = NotificationChannel(
                channelId,
                "Building Management Notifications",
                importance
            ).apply {
                description = "Thông báo từ ban quản lý tòa nhà"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel updated from Settings - enabled: $enabled")
        }
    }

    private fun updateNotificationSwitchState() {
        isUpdatingSwitch = true

        val hasSystemPermission = hasNotificationPermission()
        val userPreference = getUserNotificationPreference()

        // Switch sẽ ON khi cả 2 điều kiện đều thỏa mãn:
        // 1. Có permission hệ thống
        // 2. User muốn bật (hoặc chưa tắt explicit)
        val shouldBeEnabled = hasSystemPermission && userPreference

        binding?.switchNotifications?.isChecked = shouldBeEnabled

        // Cập nhật text mô tả
        updateNotificationDescription(hasSystemPermission, userPreference)

        isUpdatingSwitch = false

        Log.d(TAG, "Switch state updated - System: $hasSystemPermission, User: $userPreference, Final: $shouldBeEnabled")
    }

    private fun updateNotificationDescription(hasSystemPermission: Boolean, userPreference: Boolean) {
        // Bạn có thể thêm TextView mô tả dưới switch nếu muốn
        // Hoặc update subtitle của layout notification
    }

    private fun handleNotificationToggle(wantToEnable: Boolean) {
        Log.d(TAG, "User wants to ${if (wantToEnable) "enable" else "disable"} notifications")

        if (wantToEnable) {
            // User muốn bật thông báo
            if (hasNotificationPermission()) {
                // Đã có permission, chỉ cần cập nhật preference
                setUserNotificationPreference(true)
                showToast("Đã bật thông báo")
                updateNotificationSwitchState()
            } else {
                // Chưa có permission, cần yêu cầu
                requestNotificationPermission()
            }
        } else {
            // User muốn tắt thông báo
            setUserNotificationPreference(false)
            showToast("Đã tắt thông báo")
            updateNotificationSwitchState()
        }
    }

    private fun requestNotificationPermission() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                // Android < 13 không cần runtime permission
                setUserNotificationPreference(true)
                showToast("Đã bật thông báo")
                updateNotificationSwitchState()
            }

            hasNotificationPermission() -> {
                // Đã có permission
                setUserNotificationPreference(true)
                showToast("Đã bật thông báo")
                updateNotificationSwitchState()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // Đã từ chối trước đó, hiển thị dialog giải thích
                showNotificationPermissionDialog()
            }

            else -> {
                // Lần đầu yêu cầu permission
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cần quyền thông báo")
            .setMessage("Để nhận thông báo từ ban quản lý, bạn cần cho phép ứng dụng gửi thông báo.\n\nBạn có muốn mở Cài đặt để bật quyền thông báo không?")
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                openNotificationSettings()
            }
            .setNegativeButton("Không") { _, _ ->
                // Reset switch về trạng thái OFF
                setUserNotificationPreference(false)
                updateNotificationSwitchState()
                showToast("Thông báo đã được tắt")
            }
            .show()
    }

    private fun handleNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            setUserNotificationPreference(true)
            showToast("Đã bật thông báo")
        } else {
            Log.d(TAG, "Notification permission denied")
            setUserNotificationPreference(false)
            showToast("Quyền thông báo bị từ chối. Bạn có thể bật lại trong Cài đặt.")
        }
        updateNotificationSwitchState()
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                    else -> {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings", e)
            showToast("Không thể mở cài đặt thông báo")
        }
    }

    // Helper methods
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android < 13 luôn có permission
            true
        }
    }

    private fun getUserNotificationPreference(): Boolean {
        val sharedPref = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("notifications_enabled", true) // Default là true
    }

    private fun setUserNotificationPreference(enabled: Boolean) {
        val sharedPref = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("notifications_enabled", enabled).apply()
        Log.d(TAG, "User notification preference set to: $enabled")

        // THÊM: Disable/Enable notification channel để ảnh hưởng cả khi app background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "fcm_default_channel"

            if (enabled) {
                // Enable channel
                val channel = notificationManager.getNotificationChannel(channelId)
                if (channel != null) {
                    channel.importance = NotificationManager.IMPORTANCE_DEFAULT
                    notificationManager.createNotificationChannel(channel)
                    Log.d(TAG, "Notification channel enabled")
                }
            } else {
                // Disable channel
                val channel = notificationManager.getNotificationChannel(channelId)
                if (channel != null) {
                    channel.importance = NotificationManager.IMPORTANCE_NONE
                    notificationManager.createNotificationChannel(channel)
                    Log.d(TAG, "Notification channel disabled")
                }
            }
        }

        if (enabled) {
            Log.d(TAG, "Notifications enabled - FCM will show notifications")
        } else {
            Log.d(TAG, "Notifications disabled - FCM will ignore notifications")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Existing methods remain the same...
    private fun loadRoomNumberFromFirebase(phone: String) {
        val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundRoom: String? = null
                var foundRoomSnapshot: DataSnapshot? = null

                for (roomSnapshot in snapshot.children) {
                    val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                    if (phoneInRoom == phone) {
                        foundRoom = roomSnapshot.key
                        foundRoomSnapshot = roomSnapshot
                        break
                    }
                }

                if (foundRoom != null && foundRoomSnapshot != null) {
                    // Cập nhật cache
                    SharedDataManager.setCachedData(foundRoomSnapshot, foundRoom, phone)
                    Log.d(TAG, "Updated cache with room: $foundRoom")
                }

                currentRoomNumber = foundRoom
                binding?.tvRoomNumber?.text = foundRoom?.let { "Phòng $it" } ?: "Không xác định phòng"
            }

            override fun onCancelled(error: DatabaseError) {
                binding?.tvRoomNumber?.text = "Lỗi kết nối"
                currentRoomNumber = null
                Log.e(TAG, "Error loading room number: ${error.message}")
            }
        })
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
            .setPositiveButton("Đăng xuất") { _, _ ->
                // Hủy đăng ký tất cả các FCM topics trước khi đăng xuất
                // để người dùng không nhận thông báo sau khi đăng xuất
                com.app.buildingmanagement.firebase.FCMHelper.unsubscribeFromBuildingTopics(currentRoomNumber)

                // Clear cache khi đăng xuất
                SharedDataManager.clearCache()
                Log.d(TAG, "Cache cleared on logout")

                // Đăng xuất khỏi Firebase
                auth.signOut()

                // Chuyển đến màn hình đăng nhập
                val intent = Intent(requireContext(), SignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showAboutBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_about, null)

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            view.findViewById<TextView>(R.id.tvVersion).text = "Phiên bản ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            view.findViewById<TextView>(R.id.tvVersion).text = "Phiên bản 1.0.0"
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    // Method cho feedback Bottom Sheet
    private fun showFeedbackBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_feedback, null)

        val etFeedback = view.findViewById<EditText>(R.id.etFeedback)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitFeedback)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val switchAnonymous = view.findViewById<SwitchMaterial>(R.id.switchAnonymous)

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val feedback = etFeedback.text.toString().trim()
            val isAnonymous = switchAnonymous.isChecked

            if (feedback.isNotEmpty()) {
                submitFeedback(feedback, isAnonymous)
                bottomSheetDialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Vui lòng nhập nội dung góp ý", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    // Method gửi feedback vào Firebase với timestamp key
    private fun submitFeedback(feedback: String, isAnonymous: Boolean) {
        val user = auth.currentUser

        // Tạo timestamp key theo format: yyyy-MM-dd_HH-mm-ss
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

        val feedbackData = if (isAnonymous) {
            hashMapOf(
                "roomNumber" to "anonymous",
                "phone" to "anonymous",
                "feedback" to feedback,
            )
        } else {
            hashMapOf(
                "roomNumber" to (currentRoomNumber ?: "unknown"),
                "phone" to (user?.phoneNumber ?: "unknown"),
                "feedback" to feedback,
            )
        }

        // Sử dụng timestamp làm key thay vì push()
        val feedbackRef = FirebaseDatabase.getInstance().getReference("service_feedbacks")
        feedbackRef.child(timestamp).setValue(feedbackData)
            .addOnSuccessListener {
                val message = if (isAnonymous) {
                    "Cảm ơn góp ý ẩn danh về dịch vụ của chúng tôi!"
                } else {
                    "Cảm ơn góp ý về dịch vụ của chúng tôi!"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Lỗi gửi góp ý, vui lòng thử lại", Toast.LENGTH_SHORT).show()
            }
    }

    // Method mới cho Payment History Bottom Sheet
    private fun showPaymentHistoryBottomSheet() {
        // Kiểm tra cache trước khi mở payment history
        if (currentRoomNumber == null) {
            val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()
            if (cachedRoomNumber != null) {
                currentRoomNumber = cachedRoomNumber
                Log.d(TAG, "Using cached room number for payment history: $cachedRoomNumber")
            }
        }

        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_payment_history, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPayments)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val layoutEmpty = view.findViewById<LinearLayout>(R.id.layoutEmpty)

        setupPaymentRecyclerView(recyclerView, progressBar, layoutEmpty)

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun setupPaymentRecyclerView(
        recyclerView: RecyclerView,
        progressBar: ProgressBar,
        layoutEmpty: LinearLayout
    ) {
        val paymentList = mutableListOf<SimplePayment>()
        val adapter = SimplePaymentAdapter(paymentList)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE

        if (currentRoomNumber != null) {
            Log.d(TAG, "Loading payment history for room: $currentRoomNumber")

            val paymentsRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(currentRoomNumber!!)
                .child("payments")

            paymentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Payment data - Room: $currentRoomNumber, Exists: ${snapshot.exists()}, Children: ${snapshot.childrenCount}")

                    paymentList.clear()

                    for (monthSnapshot in snapshot.children) {
                        val amount = monthSnapshot.child("amount").getValue(Long::class.java) ?: 0
                        val timestamp = monthSnapshot.child("timestamp").getValue(String::class.java) ?: ""
                        val status = monthSnapshot.child("status").getValue(String::class.java) ?: ""

                        if (amount > 0 && timestamp.isNotEmpty()) {
                            paymentList.add(SimplePayment(amount, timestamp, status))
                        }
                    }

                    // Chạy trên UI thread để đảm bảo cập nhật UI
                    requireActivity().runOnUiThread {
                        progressBar.visibility = View.GONE

                        if (paymentList.isEmpty()) {
                            layoutEmpty.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            Log.d(TAG, "No payment history found")
                        } else {
                            layoutEmpty.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            // Sắp xếp theo thời gian mới nhất
                            paymentList.sortByDescending { it.timestamp }
                            adapter.notifyDataSetChanged()
                            Log.d(TAG, "Loaded ${paymentList.size} payment records")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    requireActivity().runOnUiThread {
                        progressBar.visibility = View.GONE
                        layoutEmpty.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                    Toast.makeText(requireContext(), "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error loading payment history: ${error.message}")
                }
            })
        } else {
            Log.w(TAG, "No room number available for payment history")
            progressBar.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun openDialer(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phoneNumber")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Không thể mở ứng dụng gọi điện", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}