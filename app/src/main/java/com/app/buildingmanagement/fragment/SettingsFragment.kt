package com.app.buildingmanagement.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.SignInActivity
import com.app.buildingmanagement.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    private lateinit var auth: FirebaseAuth

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
                    binding?.tvRoomNumber?.text = foundRoom?.let { "Phòng $it" } ?: "Không xác định phòng"
                }

                override fun onCancelled(error: DatabaseError) {
                    binding?.tvRoomNumber?.text = "Lỗi kết nối"
                }
            })
        } else {
            binding?.tvRoomNumber?.text = "Không xác định"
        }

        binding?.btnSignOut?.setOnClickListener {
            showLogoutConfirmation()
        }

        return binding!!.root
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
            .setPositiveButton("Đăng xuất") { _, _ ->
                auth.signOut()
                val intent = Intent(requireContext(), SignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
