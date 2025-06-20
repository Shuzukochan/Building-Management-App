package com.app.buildingmanagement.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.R
import com.app.buildingmanagement.databinding.FragmentHomeBinding
import com.app.buildingmanagement.firebase.FCMHelper
import com.app.buildingmanagement.data.SharedDataManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), SharedDataManager.DataUpdateListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var valueEventListener: ValueEventListener? = null
    private var roomsRef: DatabaseReference? = null
    private var topicsInfoSent = false
    private var isDataLoaded = false
    private var hasCheckedNotificationPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handleNotificationPermissionResult(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        roomsRef = database.getReference("rooms")
        SharedDataManager.addListener(this)
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

        val cachedSnapshot = SharedDataManager.getCachedRoomSnapshot()
        val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()
        
        if (cachedSnapshot != null && cachedRoomNumber != null) {
            processDataSnapshot(cachedSnapshot, fromCache = true)
            isDataLoaded = true
        } else {
            showLoadingState()
        }

        loadFreshDataFromFirebase()
    }

    private fun showLoadingState() {
        binding.tvRoomNumber.text = getString(R.string.loading_text)
        binding.tvElectric.text = getString(R.string.loading_value_kwh)
        binding.tvWater.text = getString(R.string.loading_value_m3)
        binding.tvElectricUsed.text = getString(R.string.loading_value_kwh)
        binding.tvWaterUsed.text = getString(R.string.loading_value_m3)
        
        binding.tvRoomNumber.alpha = 0.6f
        binding.tvElectric.alpha = 0.6f
        binding.tvWater.alpha = 0.6f
        binding.tvElectricUsed.alpha = 0.6f
        binding.tvWaterUsed.alpha = 0.6f
    }

    private fun hideLoadingState() {
        binding.tvRoomNumber.alpha = 1.0f
        binding.tvElectric.alpha = 1.0f
        binding.tvWater.alpha = 1.0f
        binding.tvElectricUsed.alpha = 1.0f
        binding.tvWaterUsed.alpha = 1.0f
    }

    private fun loadFreshDataFromFirebase() {
        val phone = auth.currentUser?.phoneNumber

        if (phone != null) {
            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    processDataSnapshot(snapshot, fromCache = false)
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoadingState()
                    showErrorState()
                }
            }

            roomsRef?.addValueEventListener(valueEventListener!!)
        } else {
            hideLoadingState()
            showErrorState()
        }
    }

    private fun showErrorState() {
        binding.tvRoomNumber.text = getString(R.string.error_connection)
        binding.tvElectric.text = getString(R.string.zero_kwh)
        binding.tvWater.text = getString(R.string.zero_m3)
        binding.tvElectricUsed.text = getString(R.string.zero_kwh)
        binding.tvWaterUsed.text = getString(R.string.zero_m3)
    }

    private fun processDataSnapshot(snapshot: DataSnapshot, fromCache: Boolean) {
        val phone = auth.currentUser?.phoneNumber ?: return

        var latestElectric = -1
        var latestWater = -1
        var roomNumber: String? = null

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val prevMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }
        val prevMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(prevMonth.time)

        for (roomSnapshot in snapshot.children) {
            val tenantsSnapshot = roomSnapshot.child("tenants")
            var phoneFound = false

            for (tenantSnapshot in tenantsSnapshot.children) {
                val phoneInTenant = tenantSnapshot.child("phone").getValue(String::class.java)
                if (phoneInTenant == phone) {
                    phoneFound = true
                    roomNumber = roomSnapshot.key
                    break
                }
            }

            if (phoneFound && roomNumber != null) {
                if (!fromCache) {
                    SharedDataManager.setCachedData(roomSnapshot, roomNumber, phone)
                }

                if (!topicsInfoSent) {
                    sendTopicsInfoToFirebase(roomNumber)
                    topicsInfoSent = true
                }

                val nodesSnapshot = roomSnapshot.child("nodes")
                for (nodeSnapshot in nodesSnapshot.children) {
                    val lastData = nodeSnapshot.child("lastData")
                    val waterValue = lastData.child("water").getValue(Long::class.java)?.toInt()
                    val electricValue = lastData.child("electric").getValue(Long::class.java)?.toInt()

                    if (waterValue != null) latestWater = waterValue
                    if (electricValue != null) latestElectric = electricValue
                }

                val historySnapshot = roomSnapshot.child("history")
                
                val allHistoryData = mutableMapOf<String, Pair<Int?, Int?>>()
                for (dateSnapshot in historySnapshot.children) {
                    val dateKey = dateSnapshot.key ?: continue
                    val electric = dateSnapshot.child("electric").getValue(Long::class.java)?.toInt()
                    val water = dateSnapshot.child("water").getValue(Long::class.java)?.toInt()
                    allHistoryData[dateKey] = Pair(electric, water)
                }
                
                val electricUsed = calculateMonthlyConsumption(allHistoryData, currentMonth, prevMonthKey, true)
                val waterUsed = calculateMonthlyConsumption(allHistoryData, currentMonth, prevMonthKey, false)

                hideLoadingState()
                updateUI(roomNumber, latestElectric, latestWater, electricUsed, waterUsed, fromCache)
                isDataLoaded = true
                break
            }
        }
    }

    private fun calculateMonthlyConsumption(
        allHistoryData: Map<String, Pair<Int?, Int?>>, 
        currentMonth: String, 
        prevMonthKey: String, 
        isElectric: Boolean
    ): Int {
        val currentMonthData = allHistoryData
            .filterKeys { it.startsWith(currentMonth) }
            .toSortedMap()
        
        val prevMonthData = allHistoryData
            .filterKeys { it.startsWith(prevMonthKey) }
            .toSortedMap()
        
        val currentValues = currentMonthData.values.mapNotNull { 
            if (isElectric) it.first else it.second 
        }
        val prevValues = prevMonthData.values.mapNotNull { 
            if (isElectric) it.first else it.second 
        }
        
        if (currentValues.isEmpty()) {
            return 0
        }
        
        val currentMaxValue = currentValues.maxOrNull() ?: 0
        val prevMonthLastValue = prevValues.lastOrNull()
        
        return if (prevMonthLastValue != null) {
            currentMaxValue - prevMonthLastValue
        } else {
            val currentMinValue = currentValues.minOrNull() ?: 0
            currentMaxValue - currentMinValue
        }
    }

    override fun onDataUpdated(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (isAdded && _binding != null) {
            processDataSnapshot(roomSnapshot, fromCache = false)
        }
    }

    override fun onCacheReady(roomSnapshot: DataSnapshot, roomNumber: String) {
        if (isAdded && _binding != null && !isDataLoaded) {
            processDataSnapshot(roomSnapshot, fromCache = true)
        }
    }

    private fun sendTopicsInfoToFirebase(roomNumber: String) {
        try {
            val topics = listOf(
                "all_residents",
                "room_$roomNumber",
                "floor_${roomNumber.substring(0, 1)}"
            )

            sendTopicsToFirebase(roomNumber, topics)

        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun sendTopicsToFirebase(roomNumber: String, topics: List<String>) {
        database.getReference("rooms")
            .child(roomNumber)
            .child("FCM")
            .child("topics")
            .setValue(topics)
    }

    private fun updateUI(roomNumber: String?, latestElectric: Int, latestWater: Int, electricUsed: Int, waterUsed: Int, fromCache: Boolean) {
        binding.tvRoomNumber.text = if (roomNumber != null) {
            getString(R.string.room_number, roomNumber)
        } else {
            getString(R.string.room_na)
        }

        binding.tvElectric.text = if (latestElectric != -1) {
            getString(R.string.value_kwh, latestElectric)
        } else {
            getString(R.string.zero_kwh)
        }
        
        binding.tvWater.text = if (latestWater != -1) {
            getString(R.string.value_m3, latestWater)
        } else {
            getString(R.string.zero_m3)
        }

        binding.tvElectricUsed.text = getString(R.string.value_kwh, electricUsed)
        binding.tvWaterUsed.text = getString(R.string.value_m3, waterUsed)
        
        if (!hasCheckedNotificationPermission && !fromCache && roomNumber != null) {
            hasCheckedNotificationPermission = true
            checkNotificationPermissionAfterDataLoad()
        }
    }

    private fun checkNotificationPermissionAfterDataLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sharedPref = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val hasRequestedBefore = sharedPref.getBoolean("notification_permission_requested", false)

            val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val userEnabledNotifications = appSettings.getBoolean("notifications_enabled", true)

            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    if (userEnabledNotifications) {
                        val roomNumber = SharedDataManager.getCachedRoomNumber()
                        if (roomNumber != null) {
                            FCMHelper.subscribeToUserBuildingTopics(roomNumber)
                        }
                    }
                }
                hasRequestedBefore -> {
                    // Already asked before, don't ask again
                }
                else -> {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isAdded && _binding != null) {
                            showNotificationPermissionDialog()
                        }
                    }, 2000)
                }
            }
        } else {
            val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val userEnabledNotifications = appSettings.getBoolean("notifications_enabled", true)
            
            if (userEnabledNotifications) {
                val roomNumber = SharedDataManager.getCachedRoomNumber()
                if (roomNumber != null) {
                    FCMHelper.subscribeToUserBuildingTopics(roomNumber)
                }
            }
        }
    }

    private fun showNotificationPermissionDialog() {
        if (!isAdded || _binding == null) return

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.notification_permission_title))
            .setMessage(getString(R.string.notification_permission_message))
            .setPositiveButton(getString(R.string.notification_permission_allow)) { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton(getString(R.string.notification_permission_deny)) { _, _ ->
                handleNotificationPermissionDenied()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.nav_selected))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.nav_unselected))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleNotificationPermissionResult(isGranted: Boolean) {
        if (!isAdded) return

        val appPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().putBoolean("notification_permission_requested", true).apply()

        if (isGranted) {
            val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            appSettings.edit().putBoolean("notifications_enabled", true).apply()

            val roomNumber = SharedDataManager.getCachedRoomNumber()
            if (roomNumber != null) {
                FCMHelper.subscribeToUserBuildingTopics(roomNumber)
            }

            Toast.makeText(requireContext(), getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            handleNotificationPermissionDenied()
        }
    }

    private fun handleNotificationPermissionDenied() {
        if (!isAdded) return

        val appPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().putBoolean("notification_permission_requested", true).apply()

        val appSettings = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appSettings.edit().putBoolean("notifications_enabled", false).apply()

        Toast.makeText(requireContext(), getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        valueEventListener?.let {
            roomsRef?.removeEventListener(it)
        }
        
        SharedDataManager.removeListener(this)
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        SharedDataManager.removeListener(this)
    }
}