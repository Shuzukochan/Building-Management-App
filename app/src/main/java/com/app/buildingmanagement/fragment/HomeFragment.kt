package com.app.buildingmanagement.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.WebPayActivity
import com.app.buildingmanagement.databinding.FragmentHomeBinding
import com.google.firebase.database.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: FirebaseDatabase
    private lateinit var meterRef: DatabaseReference
    private var valueEventListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance()
        meterRef = database.getReference("meter")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Firebase realtime update
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val electricity = snapshot.child("electricity").getValue(Int::class.java) ?: -1
                val water = snapshot.child("water").getValue(Int::class.java) ?: -1

                binding.tvElectric.text = "Điện: $electricity kWh"
                binding.tvWater.text = "Nước: $water m³"
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        meterRef.addValueEventListener(valueEventListener!!)

        // Nút thanh toán bằng OCB (mở app)
        binding.btnPay.setOnClickListener {
            val amount = binding.edtAmount.text.toString().toIntOrNull()

            if (amount == null || amount <= 0) {
                Toast.makeText(requireContext(), "Vui lòng nhập số tiền hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val url = "https://dl.vietqr.io/pay?app=ocb&ba=0561000629874@vcb&am=$amount&tn=ck"
            val intent = Intent(requireContext(), WebPayActivity::class.java)
            intent.putExtra("url", url)
            startActivity(intent)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        valueEventListener?.let {
            meterRef.removeEventListener(it)
        }
        _binding = null
    }
}
