package com.app.buildingmanagement

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat

open class BaseActivity : AppCompatActivity() {
    private var pb: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        enableEdgeToEdge()
        // ❌ BỎ dòng setContentView(R.layout.activity_base) - Đây là nguyên nhân gây conflict

        // ViewCompat.setOnApplyWindowInsetsListener sẽ được xử lý trong từng Activity con
    }

    fun showProgressBar() {
        try {
            if (pb == null) {
                pb = Dialog(this).apply {
                    setContentView(R.layout.progress_bar)
                    setCancelable(false)
                }
            }
            pb?.show()
        } catch (e: Exception) {
            // Tránh crash nếu Activity đã bị destroy
            android.util.Log.e("BaseActivity", "Error showing progress bar: ${e.message}")
        }
    }

    fun hideProgressBar() {
        try {
            pb?.dismiss()
        } catch (e: Exception) {
            // Tránh crash nếu Dialog đã bị destroy
            android.util.Log.e("BaseActivity", "Error hiding progress bar: ${e.message}")
        }
    }

    fun showToast(activity: Activity, msg: String) {
        try {
            if (!activity.isFinishing && !activity.isDestroyed) {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Error showing toast: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            pb?.dismiss()
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Error dismissing dialog in onDestroy: ${e.message}")
        } finally {
            pb = null
        }
    }
}