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
            // Handle error silently
        }
    }

    fun hideProgressBar() {
        try {
            pb?.dismiss()
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    fun showToast(activity: Activity, msg: String) {
        try {
            if (!activity.isFinishing && !activity.isDestroyed) {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            pb?.dismiss()
        } catch (e: Exception) {
            // Handle error silently
        } finally {
            pb = null
        }
    }
}