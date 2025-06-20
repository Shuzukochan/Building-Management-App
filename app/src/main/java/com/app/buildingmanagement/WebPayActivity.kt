package com.app.buildingmanagement

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class WebPayActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var isWebViewPaused = false
    private var originalUrl: String? = null
    private var isWebViewCrashed = false
    private var lastValidUrl: String? = null
    private var paymentProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupWindow()
        setContentView(R.layout.activity_web_pay)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        webView = findViewById(R.id.webView)
        originalUrl = intent.getStringExtra("url")

        setupWebView()
    }

    private fun setupWindow() {
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            }

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                statusBarColor = Color.WHITE
                navigationBarColor = Color.WHITE
            }

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }

            decorView.setBackgroundColor(Color.WHITE)
        }
    }

    private fun setupWebView() {
        val androidChromeUA = "Mozilla/5.0 (Linux; Android 10; Pixel 3 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

        with(webView.settings) {
            @Suppress("SetJavaScriptEnabled")
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = androidChromeUA
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                allowUniversalAccessFromFileURLs = true
                allowFileAccessFromFileURLs = true
            }
            
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    safeBrowsingEnabled = false
                }
            }
            
            cacheMode = WebSettings.LOAD_DEFAULT
            
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                setRenderPriority(WebSettings.RenderPriority.HIGH)
            }
            
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.setBackgroundColor(Color.WHITE)

        setupWebViewClient()
        setupWebChromeClient()
        setupBackPressedHandler()
        setupDownloadListener()

        originalUrl?.let { webView.loadUrl(it) }
    }

    private fun setupWebViewClient() {
        webView.webViewClient = object : WebViewClient() {

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                isWebViewCrashed = true

                runOnUiThread {
                    reloadOriginalPaymentUrl()
                }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val loadingUrl = request?.url.toString()

                when {
                    loadingUrl.startsWith("myapp://payment-success") -> {
                        handlePaymentSuccess(loadingUrl)
                        return true
                    }
                    loadingUrl.startsWith("myapp://payment-cancel") -> {
                        handlePaymentCancel()
                        return true
                    }

                    loadingUrl.contains("/success") -> {
                        return false
                    }

                    loadingUrl.contains("/cancel") -> {
                        handlePaymentCancel()
                        return true
                    }

                    loadingUrl.startsWith("https://") && isBankingUrl(loadingUrl) -> {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            Toast.makeText(this@WebPayActivity, "Đang mở trong trình duyệt...", Toast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }

                    !loadingUrl.startsWith("http") && !loadingUrl.startsWith("https") && isBankingScheme(loadingUrl) -> {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            Toast.makeText(this@WebPayActivity, "Đang chuyển đến app ngân hàng...", Toast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            Toast.makeText(this@WebPayActivity, "Vui lòng cài đặt app ngân hàng để tiếp tục", Toast.LENGTH_LONG).show()
                            false
                        }
                    }

                    loadingUrl.startsWith("intent://") -> {
                        return try {
                            val intent = Intent.parseUri(loadingUrl, Intent.URI_INTENT_SCHEME)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }

                    !loadingUrl.startsWith("http") && !loadingUrl.startsWith("https") -> {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            true
                        } catch (e: Exception) {
                            Toast.makeText(this@WebPayActivity, "Không thể mở ứng dụng liên kết", Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (url != null && !url.contains("casso.vn") && !url.contains("payos.vn/home")) {
                    lastValidUrl = url
                }

                view?.setBackgroundColor(Color.WHITE)

                view?.evaluateJavascript("""
                    (function() {
                        var popups = document.querySelectorAll('[style*="position: fixed"], [style*="position: absolute"], .modal, .popup, .overlay');
                        popups.forEach(function(popup) {
                            if (popup.style.zIndex > 1000) {
                                popup.style.display = 'none';
                            }
                        });
                        
                        window.open = function(url, name, specs) {
                            window.location.href = url;
                            return null;
                        };
                        
                        if (document.body) {
                            document.body.style.opacity = '0.99';
                            setTimeout(function() { 
                                document.body.style.opacity = '1'; 
                            }, 100);
                        }
                        
                        var isHomePage = document.title.includes('PayOS') && 
                                        !document.title.includes('Thanh toán') &&
                                        !window.location.href.includes('/payment/');
                        
                        return JSON.stringify({
                            isHomePage: isHomePage,
                            title: document.title,
                            url: window.location.href
                        });
                    })();
                """.trimIndent()) { result ->

                    try {
                        val jsonResult = org.json.JSONObject(result.replace("\"", ""))
                        val isHomePage = jsonResult.optBoolean("isHomePage", false)

                        if (isHomePage && originalUrl != null) {
                            view.postDelayed({
                                view.loadUrl(originalUrl!!)
                            }, 1000)
                        }
                    } catch (e: Exception) {
                        // Handle error silently
                    }
                }

                url?.let { checkForPaymentResult(it) }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)

                when {
                    url == "about:blank" && !isWebViewCrashed -> {
                        reloadOriginalPaymentUrl()
                    }
                    url?.contains("casso.vn") == true || url?.contains("payos.vn/home") == true -> {
                        view?.postDelayed({
                            reloadOriginalPaymentUrl()
                        }, 500)
                    }
                }

                view?.setBackgroundColor(Color.WHITE)
            }
        }
    }

    private fun setupWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = view
                resultMsg?.sendToTarget()

                return false
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    android.app.AlertDialog.Builder(this@WebPayActivity)
                        .setTitle("Hủy thanh toán")
                        .setMessage("Bạn có chắc muốn hủy thanh toán?")
                        .setPositiveButton("Có") { _, _ ->
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                        .setNegativeButton("Không", null)
                        .show()
                }
            }
        })
    }

    private fun setupDownloadListener() {
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
                        
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                        } else {
                            android.media.MediaScannerConnection.scanFile(
                                this,
                                arrayOf(file.absolutePath),
                                arrayOf("image/png"),
                                null
                            )
                        }

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
    }

    private fun reloadOriginalPaymentUrl() {
        originalUrl?.let { url ->
            webView.loadUrl(url)
        } ?: run {
            showPaymentResult(false, "Phiên thanh toán đã hết hạn")
        }
    }

    private fun isBankingUrl(url: String): Boolean {
        val bankingKeywords = listOf(
            "vietcombank", "techcombank", "bidv", "agribank", "mbbank", "acb.com",
            "vpbank", "sacombank", "viettinbank", "hdbank", "tpbank", "ocb.com",
            "shb.com", "eximbank", "msb.com", "vib.com", "seabank", "lpbank",
            "banking", "ebanking", "mobile-banking", "smartbanking", "internet-banking",
            "app-redirect", "deeplink", "redirect-to-app", "open-app"
        )
        return bankingKeywords.any { url.contains(it, ignoreCase = true) }
    }

    private fun isBankingScheme(url: String): Boolean {
        val bankingSchemes = listOf(
            "vietcombank://", "vcbdigibank://", "vcb://", "techcombank://", "tcb://",
            "bidv://", "smartbanking://", "agribank://", "agb://", "mbbank://", "mb://",
            "acb://", "acbapp://", "vpbank://", "vpb://", "sacombank://", "stb://",
            "viettinbank://", "vtb://", "hdbank://", "hdb://", "tpbank://", "tpb://",
            "ocb://", "shb://", "eximbank://", "msb://", "vib://", "seabank://",
            "lienvietpostbank://", "lpbank://", "momo://", "zalopay://", "zlp://",
            "viettelpay://", "vnpay://", "payoo://", "airpay://"
        )
        return bankingSchemes.any { url.startsWith(it, ignoreCase = true) }
    }

    override fun onResume() {
        super.onResume()

        setupWindow()

        if (::webView.isInitialized) {
            webView.onResume()
            webView.setBackgroundColor(Color.WHITE)

            if (isWebViewCrashed) {
                reloadOriginalPaymentUrl()
                isWebViewCrashed = false
            } else if (isWebViewPaused) {

                webView.evaluateJavascript("""
                    (function() {
                        var contentLength = document.body ? document.body.innerHTML.length : 0;
                        var isHomePage = document.title.includes('PayOS') && 
                                        !document.title.includes('Thanh toán') &&
                                        !window.location.href.includes('/payment/');
                        var currentUrl = window.location.href;
                        
                        var popups = document.querySelectorAll('[style*="position: fixed"], [style*="position: absolute"], .modal, .popup, .overlay');
                        popups.forEach(function(popup) {
                            if (popup.style.zIndex > 1000) {
                                popup.style.display = 'none';
                            }
                        });
                        
                        return JSON.stringify({
                            contentLength: contentLength,
                            isHomePage: isHomePage,
                            currentUrl: currentUrl,
                            title: document.title
                        });
                    })();
                """.trimIndent()) { result ->

                    try {
                        val jsonResult = org.json.JSONObject(result.replace("\"", ""))
                        val contentLength = jsonResult.optInt("contentLength", 0)
                        val isHomePage = jsonResult.optBoolean("isHomePage", false)

                        if (contentLength == 0 || isHomePage) {
                            reloadOriginalPaymentUrl()
                        }
                    } catch (e: Exception) {
                        reloadOriginalPaymentUrl()
                    }
                }

                isWebViewPaused = false
            }

            webView.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        var popups = document.querySelectorAll('[style*="position: fixed"], [style*="position: absolute"], .modal, .popup, .overlay');
                        popups.forEach(function(popup) {
                            if (popup.style.zIndex > 1000) {
                                popup.style.display = 'none';
                            }
                        });
                        
                        if (document.body) {
                            document.body.style.opacity = '0.99';
                            setTimeout(function() { 
                                document.body.style.opacity = '1'; 
                            }, 100);
                        }
                        return true;
                    })();
                """.trimIndent(), null)
            }, 500)
        }
    }

    override fun onPause() {
        super.onPause()

        if (::webView.isInitialized) {
            webView.onPause()
            isWebViewPaused = true
        }
    }

    override fun onRestart() {
        super.onRestart()

        setupWindow()

        if (::webView.isInitialized) {
            webView.setBackgroundColor(Color.WHITE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && ::webView.isInitialized) {

            webView.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        var popups = document.querySelectorAll('[style*="position: fixed"], [style*="position: absolute"], .modal, .popup, .overlay');
                        popups.forEach(function(popup) {
                            if (popup.style.zIndex > 1000) {
                                popup.style.display = 'none';
                            }
                        });
                        
                        return window.location.href;
                    })();
                """.trimIndent()) { url ->

                    if (url.contains("casso.vn") || url.contains("payos.vn/home")) {
                        reloadOriginalPaymentUrl()
                    }
                }
            }, 300)
        }
    }

    private fun handlePaymentSuccess(url: String) {
        try {
            val uri = Uri.parse(url)
            val orderCode = uri.getQueryParameter("orderCode")
            val status = uri.getQueryParameter("status")
            val paymentLinkId = uri.getQueryParameter("paymentLinkId")
            val code = uri.getQueryParameter("code")

            val isValidSuccess = when {
                url.startsWith("myapp://payment-success") && orderCode != null && status == "PAID" -> true
                url.contains("/success") && code == "00" -> true
                url.contains("success") && (orderCode != null || status == "PAID" || status == "success") -> true
                else -> false
            }

            if (isValidSuccess) {
                if (orderCode == null && url.contains("/success")) {

                    webView.postDelayed({
                        webView.evaluateJavascript("window.location.href") { currentUrl ->
                            if (currentUrl.contains("myapp://payment-success")) {
                                return@evaluateJavascript // Deep link detected, no duplicate processing needed
                            }
                        }
                    }, 3000)

                    return
                }

                savePaymentToFirebase(orderCode, paymentLinkId)
            }

        } catch (e: Exception) {
            if (url.startsWith("myapp://payment-success") || url.contains("PAID")) {
                savePaymentToFirebase(null, null)
            }
        }
    }

    private fun handlePaymentCancel() {
        showPaymentResult(false, "Thanh toán đã bị hủy")
    }

    private fun checkForPaymentResult(url: String) {
        if (!url.startsWith("myapp://")) {
            val successPatterns = listOf("success")
            val cancelPatterns = listOf("cancel", "failed", "error")

            when {
                successPatterns.any { url.contains(it, ignoreCase = true) } -> {
                    // Wait for deep link
                }
                cancelPatterns.any { url.contains(it, ignoreCase = true) } -> {
                    handlePaymentCancel()
                }
            }
        }
    }

    private fun savePaymentToFirebase(orderCode: String?, paymentLinkId: String?) {
        if (paymentProcessed) {
            return
        }

        paymentProcessed = true

        val phone = auth.currentUser?.phoneNumber
        if (phone == null) {
            showPaymentResult(false, "Không thể xác thực người dùng")
            return
        }

        val roomNumber = intent.getStringExtra("roomNumber")
        val monthToPayFor = intent.getStringExtra("month")
        val amount = intent.getIntExtra("amount", 0)

        if (roomNumber == null || monthToPayFor == null) {
            showPaymentResult(false, "Thiếu thông tin phòng hoặc tháng thanh toán")
            return
        }

        val paymentData = mapOf(
            "status" to "PAID",
            "orderCode" to (orderCode ?: "unknown_${System.currentTimeMillis()}"),
            "paymentLinkId" to (paymentLinkId ?: ""),
            "paymentDate" to System.currentTimeMillis(),
            "amount" to amount,
            "paidBy" to phone,
            "roomNumber" to roomNumber,
            "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        database.getReference("rooms")
            .child(roomNumber)
            .child("payments")
            .child(monthToPayFor)
            .setValue(paymentData)
            .addOnSuccessListener {
                showPaymentResult(true, "Thanh toán thành công!")
            }
            .addOnFailureListener { e ->
                showPaymentResult(false, "Lỗi lưu thông tin thanh toán: ${e.message}")
            }
    }

    private fun showPaymentResult(success: Boolean, message: String) {
        runOnUiThread {
            Toast.makeText(this, message, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

            webView.postDelayed({
                setResult(if (success) RESULT_OK else RESULT_CANCELED)
                finish()
            }, if (success) 2000 else 1000)
        }
    }
}
