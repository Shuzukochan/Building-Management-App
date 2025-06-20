package com.app.buildingmanagement.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    private lateinit var notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleNotificationPermissionResult(isGranted)
        }
        
        val user = auth.currentUser
        val phone = user?.phoneNumber

        binding?.tvPhoneNumber?.text = phone?.replace("+84", "0") ?: getString(R.string.no_phone_number)

        val cachedRoomNumber = SharedDataManager.getCachedRoomNumber()
        val cachedSnapshot = SharedDataManager.getCachedRoomSnapshot()

        if (cachedRoomNumber != null && cachedSnapshot != null) {
            currentRoomNumber = cachedRoomNumber
            binding?.tvRoomNumber?.text = getString(R.string.room_prefix, cachedRoomNumber)

            loadUserNameFromSnapshot(cachedSnapshot, phone)

            setupInitialNotificationStateIfNeeded()

        } else if (phone != null) {
            loadUserDataFromFirebase(phone)
        } else {
            binding?.tvRoomNumber?.text = getString(R.string.room_unknown)
            binding?.tvUserName?.text = getString(R.string.user_unknown)
            currentRoomNumber = null
            currentUserName = null
        }

        binding?.btnSignOut?.setOnClickListener {
            showLogoutConfirmation()
        }

        binding?.btnPaymentHistory?.setOnClickListener {
            showPaymentHistoryBottomSheet()
        }

        setupNotificationSwitch()

        binding?.btnFeedback?.setOnClickListener {
            showFeedbackBottomSheet()
        }

        binding?.btnSupport?.setOnClickListener {
            openDialer(getString(R.string.support_phone_number))
        }

        binding?.btnAbout?.setOnClickListener {
            showAboutBottomSheet()
        }

        return binding!!.root
    }

    private fun loadUserNameFromSnapshot(roomSnapshot: DataSnapshot, phone: String?) {
        if (phone == null) {
            binding?.tvUserName?.text = getString(R.string.user_unknown)
            return
        }

        val tenantsSnapshot = roomSnapshot.child(getString(R.string.db_tenants))

        for (tenantSnapshot in tenantsSnapshot.children) {
            val phoneInTenant = tenantSnapshot.child(getString(R.string.db_phone)).getValue(String::class.java)
            if (phoneInTenant == phone) {
                val userName = tenantSnapshot.child(getString(R.string.db_name)).getValue(String::class.java)
                currentUserName = userName
                binding?.tvUserName?.text = userName ?: getString(R.string.user_no_name)
                return
            }
        }

        binding?.tvUserName?.text = getString(R.string.user_not_found)
    }

    private fun setupInitialNotificationStateIfNeeded() {
        // Switch will be synced with current settings
    }

    private fun setupNotificationSwitch() {
        syncNotificationSwitchWithSystemPermission()

        binding?.layoutNotifications?.setOnClickListener {
            binding?.switchNotifications?.isChecked = !(binding?.switchNotifications?.isChecked ?: false)
        }

        binding?.switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            val sharedPref = requireActivity().getSharedPreferences(getString(R.string.pref_app_settings), Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean(getString(R.string.pref_notifications_enabled), isChecked).apply()

            handleNotificationSubscription(isChecked)

            val message = if (isChecked) getString(R.string.notification_enabled) else getString(R.string.notification_disabled)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncNotificationSwitchWithSystemPermission() {
        val sharedPref = requireActivity().getSharedPreferences(getString(R.string.pref_app_settings), Context.MODE_PRIVATE)
        var userEnabledNotifications = sharedPref.getBoolean(getString(R.string.pref_notifications_enabled), true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasSystemPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasSystemPermission && userEnabledNotifications) {
                userEnabledNotifications = false
                sharedPref.edit().putBoolean(getString(R.string.pref_notifications_enabled), false).apply()

                FCMHelper.unsubscribeFromBuildingTopics(currentRoomNumber)
                
                Toast.makeText(
                    requireContext(), 
                    getString(R.string.notification_permission_revoked_message), 
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding?.switchNotifications?.isChecked = userEnabledNotifications
    }

    override fun onResume() {
        super.onResume()
        syncNotificationSwitchWithSystemPermission()
    }

    private fun handleNotificationSubscription(enabled: Boolean) {
        if (enabled) {            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        FCMHelper.subscribeToUserBuildingTopics(currentRoomNumber)
                    }
                    else -> {
                        showNotificationPermissionExplanation()
                    }
                }
            } else {
                FCMHelper.subscribeToUserBuildingTopics(currentRoomNumber)
            }
        } else {
            FCMHelper.unsubscribeFromBuildingTopics(currentRoomNumber)
        }
    }

    private fun showNotificationPermissionExplanation() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.notification_permission_title))
            .setMessage(getString(R.string.notification_permission_settings_message))
            .setPositiveButton(getString(R.string.notification_permission_allow)) { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton(getString(R.string.notification_permission_deny)) { _, _ ->
                binding?.switchNotifications?.isChecked = false
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

        if (isGranted) {
            FCMHelper.subscribeToUserBuildingTopics(currentRoomNumber)
            
            Toast.makeText(requireContext(), getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            binding?.switchNotifications?.isChecked = false
            handleNotificationPermissionDenied()
        }
    }

    private fun handleNotificationPermissionDenied() {
        val sharedPref = requireActivity().getSharedPreferences(getString(R.string.pref_app_settings), Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(getString(R.string.pref_notifications_enabled), false).apply()
        
        Toast.makeText(requireContext(), getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
    }

    private fun loadUserDataFromFirebase(phone: String) {
        val roomsRef = FirebaseDatabase.getInstance().getReference(getString(R.string.db_rooms))

        roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundRoom: String? = null
                var foundRoomSnapshot: DataSnapshot? = null
                var foundUserName: String? = null

                for (roomSnapshot in snapshot.children) {
                    val tenantsSnapshot = roomSnapshot.child(getString(R.string.db_tenants))

                    for (tenantSnapshot in tenantsSnapshot.children) {
                        val phoneInTenant = tenantSnapshot.child(getString(R.string.db_phone)).getValue(String::class.java)
                        if (phoneInTenant == phone) {
                            foundRoom = roomSnapshot.key
                            foundRoomSnapshot = roomSnapshot
                            foundUserName = tenantSnapshot.child(getString(R.string.db_name)).getValue(String::class.java)

                            break
                        }
                    }

                    if (foundRoom != null) break
                }

                if (foundRoom != null && foundRoomSnapshot != null) {
                    SharedDataManager.setCachedData(foundRoomSnapshot, foundRoom, phone)
                }

                currentRoomNumber = foundRoom
                currentUserName = foundUserName

                binding?.tvRoomNumber?.text = foundRoom?.let { getString(R.string.room_prefix, it) } ?: getString(R.string.room_unknown_full)
                binding?.tvUserName?.text = foundUserName ?: getString(R.string.user_no_name_full)

                setupInitialNotificationStateIfNeeded()
            }

            override fun onCancelled(error: DatabaseError) {
                binding?.tvRoomNumber?.text = getString(R.string.connection_error)
                binding?.tvUserName?.text = getString(R.string.connection_error)
                currentRoomNumber = null
                currentUserName = null
            }
        })
    }

    private fun showLogoutConfirmation() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton(getString(R.string.logout_button)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .create()
            
        dialog.show()
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_color))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.nav_unselected))
    }

    private fun performLogout() {
        try {
            try {
                val mainActivity = activity as? MainActivity
                mainActivity?.onUserLogout()
            } catch (e: Exception) {
                // Handle error silently
            }

            FCMHelper.unsubscribeFromBuildingTopics(currentRoomNumber)

            SharedDataManager.clearCache()

            clearAllSharedPreferences()

            auth.signOut()

            clearFirebaseAuthState()

            val intent = Intent(requireContext(), SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            
            requireActivity().finish()

        } catch (e: Exception) {
            auth.signOut()
            val intent = Intent(requireContext(), SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun clearAllSharedPreferences() {
        try {
            val appSettings = requireContext().getSharedPreferences(getString(R.string.pref_app_settings), Context.MODE_PRIVATE)
            appSettings.edit().clear().apply()

            val appPrefs = requireContext().getSharedPreferences(getString(R.string.pref_app_prefs), Context.MODE_PRIVATE)
            appPrefs.edit().clear().apply()

            val fcmPrefs = requireContext().getSharedPreferences(getString(R.string.pref_fcm_prefs), Context.MODE_PRIVATE)
            fcmPrefs.edit().clear().apply()

        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun clearFirebaseAuthState() {
        try {
            FirebaseAuth.getInstance().signOut()
            
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun submitFeedback(feedback: String, isAnonymous: Boolean) {
        val user = auth.currentUser
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

        val feedbackData = if (isAnonymous) {
            hashMapOf(
                getString(R.string.db_room_number) to getString(R.string.db_anonymous),
                getString(R.string.db_phone) to getString(R.string.db_anonymous),
                getString(R.string.db_user_name) to getString(R.string.db_anonymous),
                getString(R.string.db_feedback) to feedback,
            )
        } else {
            hashMapOf(
                getString(R.string.db_room_number) to (currentRoomNumber ?: getString(R.string.db_unknown)),
                getString(R.string.db_phone) to (user?.phoneNumber ?: getString(R.string.db_unknown)),
                getString(R.string.db_user_name) to (currentUserName ?: getString(R.string.db_unknown)),
                getString(R.string.db_feedback) to feedback,
            )
        }

        val feedbackRef = FirebaseDatabase.getInstance().getReference(getString(R.string.db_service_feedbacks))
        feedbackRef.child(timestamp).setValue(feedbackData)
            .addOnSuccessListener {
                val message = if (isAnonymous) {
                    getString(R.string.feedback_anonymous_success)
                } else {
                    if (currentUserName != null) {
                        getString(R.string.feedback_success, currentUserName)
                    } else {
                        getString(R.string.feedback_success_you)
                    }
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), getString(R.string.feedback_error), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAboutBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        @Suppress("InflateParams")
        val view = layoutInflater.inflate(R.layout.bottom_sheet_about, null, false)

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            view.findViewById<TextView>(R.id.tvVersion).text = getString(R.string.version_format, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            view.findViewById<TextView>(R.id.tvVersion).text = getString(R.string.version_default)
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun showFeedbackBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        @Suppress("InflateParams")
        val view = layoutInflater.inflate(R.layout.bottom_sheet_feedback, null, false)

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
                Toast.makeText(requireContext(), getString(R.string.feedback_empty), Toast.LENGTH_SHORT).show()
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
            }
        }

        val bottomSheetDialog = BottomSheetDialog(requireContext())
        @Suppress("InflateParams")
        val view = layoutInflater.inflate(R.layout.bottom_sheet_payment_history, null, false)

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
                .getReference(getString(R.string.db_rooms))
                .child(currentRoomNumber!!)
                .child(getString(R.string.db_payments))

            paymentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
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
                        } else {
                            layoutEmpty.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            paymentList.sortByDescending { it.timestamp }
                            @Suppress("NotifyDataSetChanged")
                            adapter.notifyDataSetChanged()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    requireActivity().runOnUiThread {
                        progressBar.visibility = View.GONE
                        layoutEmpty.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                    Toast.makeText(requireContext(), getString(R.string.payment_load_error, error.message), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), getString(R.string.dialer_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}