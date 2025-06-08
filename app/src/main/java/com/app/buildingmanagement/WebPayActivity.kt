package com.app.buildingmanagement

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class WebPayActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_pay)

        webView = findViewById(R.id.webView)

        val url = intent.getStringExtra("url") ?: return
        val androidChromeUA =
            "Mozilla/5.0 (Linux; Android 10; Pixel 3 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

        // ⚙️ Cấu hình WebView
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = androidChromeUA
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val newWebView = WebView(this@WebPayActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = webView.webViewClient
                    webChromeClient = this@WebPayActivity.webView.webChromeClient
                }

                val dialog = android.app.Dialog(this@WebPayActivity)
                dialog.setContentView(newWebView)
                dialog.show()

                val transport = resultMsg?.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                return true
            }
        }

        // ✅ Bắt deeplink và app scheme
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val loadingUrl = request?.url.toString()

                if (!loadingUrl.startsWith("http") && !loadingUrl.startsWith("https")) {
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl))
                        startActivity(intent)
                        true
                    } catch (e: Exception) {
                        Toast.makeText(this@WebPayActivity, "Không thể mở ứng dụng liên kết", Toast.LENGTH_SHORT).show()
                        true
                    }
                }

                if (loadingUrl.startsWith("myapp://payment-success") ||
                    loadingUrl.startsWith("myapp://payment-cancel")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl))
                    startActivity(intent)
                    finish()
                    return true
                }

                return false
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            when {
                url?.startsWith("data:image") == true -> {
                    try {
                        val base64Data = url.substringAfter(",")
                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val fileName = "qr_${System.currentTimeMillis()}.png"

                        val file = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            fileName
                        )
                        FileOutputStream(file).use { it.write(imageBytes) }

                        val uri = Uri.fromFile(file)
                        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))

                        Toast.makeText(this, "Đã lưu ảnh vào thư mục Tải về", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Lỗi khi lưu ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                url != null && (url.startsWith("http://") || url.startsWith("https://")) -> {
                    try {
                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                            setMimeType(mimeType)
                            addRequestHeader("User-Agent", userAgent)

                            val cookie = CookieManager.getInstance().getCookie(url)
                            if (cookie != null) {
                                addRequestHeader("Cookie", cookie)
                            }

                            setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                            setDescription("Đang tải file...")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                URLUtil.guessFileName(url, contentDisposition, mimeType)
                            )
                            setAllowedOverMetered(true)
                            setAllowedOverRoaming(true)
                        }

                        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(this, "Đang tải file...", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        Toast.makeText(this, "Không thể tải file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                else -> {
                    Toast.makeText(this, "Không thể tải file không hợp lệ", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
