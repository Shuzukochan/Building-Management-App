package com.app.buildingmanagement.fragment

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.buildingmanagement.MainActivity
import com.app.buildingmanagement.R
import com.app.buildingmanagement.SignInActivity
import com.app.buildingmanagement.adapter.SimplePaymentAdapter
import com.app.buildingmanagement.databinding.FragmentSettingsBinding
import com.app.buildingmanagement.model.SimplePayment
import com.app.buildingmanagement.data.SharedDataManager
import com.app.buildingmanagement.firebase.FCMHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    private lateinit var auth: FirebaseAuth
    private var currentRoomNumber: String? = null
    private var currentUserName: String? = null

    companion object {
        private const val TAG = "SettingsFragment"
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
        val cachedSnapshot = SharedDataManager.getCachedRoomSnapshot()

        if (cachedRoomNumber != null && cachedSnapshot != null) {
            Log.d(TAG, "Using cached data: $cachedRoomNumber")
            currentRoomNumber = cachedRoomNumber
            binding?.tvRoomNumber?.text = "Phòng $cachedRoomNumber"

            // Tìm tên user từ cached data
            loadUserNameFromSnapshot(cachedSnapshot, phone)

            // CHỈ SETUP NẾU LẦN ĐẦU TIÊN
            setupInitialNotificationStateIfNeeded()

        } else if (phone != null) {
            Log.d(TAG, "No cache, loading from Firebase")
            loadUserDataFromFirebase(phone)
        } else {
            binding?.tvRoomNumber?.text = "Không xác định"
            binding?.tvUserName?.text = "Không xác định"
            currentRoomNumber = null
            currentUserName = null
        }

        binding?.btnSignOut?.setOnClickListener {
            showLogoutConfirmation()
        }

        binding?.btnPaymentHistory?.setOnClickListener {
            showPaymentHistoryBottomSheet()
        }

        // SETUP NOTIFICATION SWITCH
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

    private fun loadUserNameFromSnapshot(roomSnapshot: DataSnapshot, phone: String?) {
        if (phone == null) {
            binding?.tvUserName?.text = "Không xác định"
            return
        }

        val tenantsSnapshot = roomSnapshot.child("tenants")

        // Duyệt qua tất cả các thành viên trong phòng
        for (tenantSnapshot in tenantsSnapshot.children) {
            val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
            if (phoneInTenant == phone) {
                val userName = tenantSnapshot.child("name").getValue(String::class.java)
                currentUserName = userName
                binding?.tvUserName?.text = userName ?: "Chưa có tên"
                Log.d(TAG, "Found user name from cache: $userName")
                return
            }
        }

        binding?.tvUserName?.text = "Không tìm thấy"
        Log.w(TAG, "User name not found in cached data")
    }

    /**
     * HYBRID APPROACH: Chỉ setup initial state nếu chưa có setting
     * MainActivity sẽ handle các case khác
     */
    private fun setupInitialNotificationStateIfNeeded() {
        val sharedPref = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        if (!sharedPref.contains("notifications_enabled")) {
            // TRƯỜNG HỢP HIẾM KHI MAINACTIVITY CHƯA HANDLE - BACKUP
            Log.d(TAG, "MainActivity missed first time setup - handling here")
            sharedPref.edit().putBoolean("notifications_enabled", true).apply()
            FCMHelper.subscribeToUserBuildingTopics(currentRoomNumber)
        } else {
            // ĐÃ CÓ SETTING - MAINACTIVITY ĐÃ HANDLE RỒI
            val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
            Log.d(TAG, "Setting already exists: notifications_enabled = $notificationsEnabled (handled by MainActivity)")
        }
    }

    private fun setupNotificationSwitch() {
        // Set initial state từ SharedPreferences
        val sharedPref = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        binding?.switchNotifications?.isChecked = notificationsEnabled

        // Handle layout click
        binding?.layoutNotifications?.setOnClickListener {
            binding?.switchNotifications?.isChecked = !(binding?.switchNotifications?.isChecked ?: false)
        }

        // Handle switch toggle - XỬ LÝ FCM TOPICS
        binding?.switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            // Lưu preference
            sharedPref.edit().putBoolean("notifications_enabled", isChecked).apply()

            // XỬ LÝ FCM TOPIC SUBSCRIPTION
            handleNotificationSubscription(isChecked)

            val message = if (isChecked) "Đã bật thông báo" else "Đã tắt thông báo"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * XỬ LÝ SUBSCRIBE/UNSUBSCRIBE FCM TOPICS
     */
    private fun handleNotificationSubscription(enabled: Boolean) {
        if (enabled) {
            // BẬT NOTIFICATION: Subscribe lại các topics
            Log.d(TAG, "Enabling notifications - subscribing to FCM topics...")
            FCMHelper.subscribeToUserBuildingTopics(currentRoomNumber)
            
            // Thêm delay nhỏ và force re-subscribe từ MainActivity nếu có thể
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val mainActivity = activity as? MainActivity
                    mainActivity?.forceResubscribeNotifications()
                    Log.d(TAG, "Force re-subscribe triggered from MainActivity")
                } catch (e: Exception) {
                    Log.d(TAG, "Could not call MainActivity method: ${e.message}")
                }
            }, 500)
            
        } else {
            // TẮT NOTIFICATION: Unsubscribe khỏi tất cả topics
            Log.d(TAG, "Disabling notifications - unsubscribing from FCM topics...")
            FCMHelper.unsubscribeFromBuildingTopics(currentRoomNumber)
        }
    }

    private fun loadUserDataFromFirebase(phone: String) {
        val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundRoom: String? = null
                var foundRoomSnapshot: DataSnapshot? = null
                var foundUserName: String? = null

                // Duyệt qua tất cả các phòng
                for (roomSnapshot in snapshot.children) {
                    val tenantsSnapshot = roomSnapshot.child("tenants")

                    // Duyệt qua tất cả các thành viên trong phòng
                    for (tenantSnapshot in tenantsSnapshot.children) {
                        val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInTenant == phone) {
                            foundRoom = roomSnapshot.key
                            foundRoomSnapshot = roomSnapshot
                            foundUserName = tenantSnapshot.child("name").getValue(String::class.java)

                            Log.d(TAG, "Found user: $foundUserName in room: $foundRoom")
                            break
                        }
                    }

                    if (foundRoom != null) break
                }

                if (foundRoom != null && foundRoomSnapshot != null) {
                    SharedDataManager.setCachedData(foundRoomSnapshot, foundRoom, phone)
                    Log.d(TAG, "Updated cache with room: $foundRoom and user: $foundUserName")
                }

                currentRoomNumber = foundRoom
                currentUserName = foundUserName

                binding?.tvRoomNumber?.text = foundRoom?.let { "Phòng $it" } ?: "Không xác định phòng"
                binding?.tvUserName?.text = foundUserName ?: "Chưa có tên"

                // CHỈ SETUP NẾU LẦN ĐẦU TIÊN
                setupInitialNotificationStateIfNeeded()
            }

            override fun onCancelled(error: DatabaseError) {
                binding?.tvRoomNumber?.text = "Lỗi kết nối"
                binding?.tvUserName?.text = "Lỗi kết nối"
                currentRoomNumber = null
                currentUserName = null
                Log.e(TAG, "Error loading user data: ${error.message}")
            }
        })
    }

    private fun showLogoutConfirmation() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
            .setPositiveButton("Đăng xuất") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Hủy", null)
            .create()
            
        dialog.show()
        
        // Đảm bảo màu nút hiển thị rõ ràng
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_color))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.nav_unselected))
    }

    private fun performLogout() {
        Log.d(TAG, "=== STARTING LOGOUT PROCESS ===")
        
        try {
            // 0. Gọi MainActivity logout nếu có thể
            try {
                val mainActivity = activity as? MainActivity
                mainActivity?.onUserLogout()
                Log.d(TAG, "Called MainActivity.onUserLogout()")
            } catch (e: Exception) {
                Log.d(TAG, "Could not call MainActivity.onUserLogout(): ${e.message}")
            }

            // 1. Hủy đăng ký tất cả các FCM topics trước khi đăng xuất
            Log.d(TAG, "Step 1: Unsubscribing from FCM topics...")
            FCMHelper.unsubscribeFromBuildingTopics(currentRoomNumber)

            // 2. Clear tất cả cache trong SharedDataManager
            Log.d(TAG, "Step 2: Clearing SharedDataManager cache...")
            SharedDataManager.clearCache()

            // 3. Clear tất cả SharedPreferences liên quan
            Log.d(TAG, "Step 3: Clearing SharedPreferences...")
            clearAllSharedPreferences()

            // 4. Đăng xuất khỏi Firebase Auth
            Log.d(TAG, "Step 4: Signing out from Firebase Auth...")
            auth.signOut()

            // 5. FORCE CLEAR FIREBASE AUTH PERSISTENCE (quan trọng!)
            Log.d(TAG, "Step 5: Force clearing Firebase Auth state...")
            clearFirebaseAuthState()

            // 6. Verify logout
            Log.d(TAG, "Step 6: Verifying logout...")
            val currentUser = auth.currentUser
            Log.d(TAG, "Current user after logout: $currentUser")

            // 7. Navigate to login screen với clear task
            Log.d(TAG, "Step 7: Navigating to SignIn...")
            val intent = Intent(requireContext(), SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            
            // 8. Finish current activity
            requireActivity().finish()

            Log.d(TAG, "=== LOGOUT PROCESS COMPLETED ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error during logout process", e)
            
            // Backup logout - chỉ basic steps
            auth.signOut()
            val intent = Intent(requireContext(), SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun clearAllSharedPreferences() {
        try {
            // Clear app settings
            val appSettings = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            appSettings.edit().clear().apply()
            Log.d(TAG, "Cleared app_settings")

            // Clear app prefs
            val appPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            appPrefs.edit().clear().apply()
            Log.d(TAG, "Cleared app_prefs")

            // Clear FCM prefs
            val fcmPrefs = requireContext().getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            fcmPrefs.edit().clear().apply()
            Log.d(TAG, "Cleared fcm_prefs")

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing SharedPreferences", e)
        }
    }

    private fun clearFirebaseAuthState() {
        try {
            // Force set auth instance to null và clear any cached data
            // Đây là cách đảm bảo Firebase Auth thực sự logout
            
            // Log state trước khi clear
            Log.d(TAG, "Before clear - User: ${auth.currentUser?.phoneNumber}")
            
            // Gọi signOut một lần nữa để chắc chắn
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            
            // Log state sau khi clear
            Log.d(TAG, "After clear - User: ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing Firebase Auth state", e)
        }
    }

    private fun submitFeedback(feedback: String, isAnonymous: Boolean) {
        val user = auth.currentUser
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

        val feedbackData = if (isAnonymous) {
            hashMapOf(
                "roomNumber" to "anonymous",
                "phone" to "anonymous",
                "userName" to "anonymous",
                "feedback" to feedback,
            )
        } else {
            hashMapOf(
                "roomNumber" to (currentRoomNumber ?: "unknown"),
                "phone" to (user?.phoneNumber ?: "unknown"),
                "userName" to (currentUserName ?: "unknown"),
                "feedback" to feedback,
            )
        }

        val feedbackRef = FirebaseDatabase.getInstance().getReference("service_feedbacks")
        feedbackRef.child(timestamp).setValue(feedbackData)
            .addOnSuccessListener {
                val message = if (isAnonymous) {
                    "Cảm ơn góp ý ẩn danh về dịch vụ của chúng tôi!"
                } else {
                    "Cảm ơn ${currentUserName ?: "bạn"} đã góp ý về dịch vụ của chúng tôi!"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Lỗi gửi góp ý, vui lòng thử lại", Toast.LENGTH_SHORT).show()
            }
    }

    // Các method khác giữ nguyên...
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

    private fun showPaymentHistoryBottomSheet() {
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

                    requireActivity().runOnUiThread {
                        progressBar.visibility = View.GONE

                        if (paymentList.isEmpty()) {
                            layoutEmpty.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            Log.d(TAG, "No payment history found")
                        } else {
                            layoutEmpty.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
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