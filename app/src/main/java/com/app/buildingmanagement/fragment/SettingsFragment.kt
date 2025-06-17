package com.app.buildingmanagement.fragment

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.buildingmanagement.R
import com.app.buildingmanagement.SignInActivity
import com.app.buildingmanagement.adapter.SimplePaymentAdapter
import com.app.buildingmanagement.databinding.FragmentSettingsBinding
import com.app.buildingmanagement.model.SimplePayment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    private lateinit var auth: FirebaseAuth
    private var currentRoomNumber: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val phone = user?.phoneNumber

        binding?.tvPhoneNumber?.text = phone?.replace("+84", "0") ?: "Chưa có số điện thoại"

        if (phone != null) {
            val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")

            roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundRoom: String? = null
                    for (roomSnapshot in snapshot.children) {
                        val phoneInRoom = roomSnapshot.child("phone").getValue(String::class.java)
                        if (phoneInRoom == phone) {
                            foundRoom = roomSnapshot.key
                            break
                        }
                    }
                    currentRoomNumber = foundRoom
                    binding?.tvRoomNumber?.text = foundRoom?.let { "Phòng $it" } ?: "Không xác định phòng"
                }

                override fun onCancelled(error: DatabaseError) {
                    binding?.tvRoomNumber?.text = "Lỗi kết nối"
                    currentRoomNumber = null
                }
            })
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

        binding?.layoutNotifications?.setOnClickListener {
            binding?.switchNotifications?.isChecked = !(binding?.switchNotifications?.isChecked ?: false)
        }

        binding?.switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            val sharedPref = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("notifications_enabled", isChecked)
                apply()
            }

            val message = if (isChecked) "Đã bật thông báo" else "Đã tắt thông báo"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        val sharedPref = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        binding?.switchNotifications?.isChecked = notificationsEnabled

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

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
            .setPositiveButton("Đăng xuất") { _, _ ->
                // Hủy đăng ký tất cả các FCM topics trước khi đăng xuất
                // để người dùng không nhận thông báo sau khi đăng xuất
                com.app.buildingmanagement.firebase.FCMHelper.unsubscribeFromBuildingTopics(currentRoomNumber)

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
            val paymentsRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(currentRoomNumber!!)
                .child("payments")

            paymentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("PaymentDebug", "Room: $currentRoomNumber")
                    Log.d("PaymentDebug", "Snapshot exists: ${snapshot.exists()}")
                    Log.d("PaymentDebug", "Children count: ${snapshot.childrenCount}")

                    paymentList.clear()

                    for (monthSnapshot in snapshot.children) {
                        Log.d("PaymentDebug", "Month key: ${monthSnapshot.key}")
                        Log.d("PaymentDebug", "Month data: ${monthSnapshot.value}")

                        val amount = monthSnapshot.child("amount").getValue(Long::class.java) ?: 0
                        val timestamp = monthSnapshot.child("timestamp").getValue(String::class.java) ?: ""
                        val status = monthSnapshot.child("status").getValue(String::class.java) ?: ""

                        Log.d("PaymentDebug", "Amount: $amount, Timestamp: $timestamp, Status: $status")

                        if (amount > 0 && timestamp.isNotEmpty()) {
                            paymentList.add(SimplePayment(amount, timestamp, status))
                        }
                    }

                    Log.d("PaymentDebug", "Final payment list size: ${paymentList.size}")

                    // Chạy trên UI thread để đảm bảo cập nhật UI
                    requireActivity().runOnUiThread {
                        progressBar.visibility = View.GONE

                        if (paymentList.isEmpty()) {
                            layoutEmpty.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            Log.d("PaymentDebug", "Showing empty state")
                        } else {
                            layoutEmpty.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            // Sắp xếp theo thời gian mới nhất
                            paymentList.sortByDescending { it.timestamp }
                            adapter.notifyDataSetChanged()
                            Log.d("PaymentDebug", "Showing ${paymentList.size} payments")
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
                }
            })
        } else {
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
