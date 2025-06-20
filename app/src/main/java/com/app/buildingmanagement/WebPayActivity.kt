package com.app.buildingmanagement

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
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
            
            // Fix deprecated flags với version check
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            }

            // Fix deprecated statusBarColor và navigationBarColor
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                statusBarColor = Color.WHITE
                navigationBarColor = Color.WHITE
            }

            // Fix deprecated systemUiVisibility
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
            // ✅ TẮT multiple windows để tránh popup
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            
            // Fix deprecated file access settings
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                allowUniversalAccessFromFileURLs = true
                allowFileAccessFromFileURLs = true
            }
            
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // Fix deprecated safeBrowsingEnabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    safeBrowsingEnabled = false
                }
            }
            
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // Fix deprecated setRenderPriority
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
                val crashInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail?.didCrash()
                } else {
                    "unknown"
                }
                Log.e("WebPayActivity", "🔥 WebView render process gone! Crashed: $crashInfo")
                isWebViewCrashed = true

                runOnUiThread {
                    reloadOriginalPaymentUrl()
                }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val loadingUrl = request?.url.toString()
                Log.d("WebPayActivity", "Intercepting URL: $loadingUrl")

                when {
                    // ✅ Chỉ xử lý deep link myapp:// ngay lập tức
                    loadingUrl.startsWith("myapp://payment-success") -> {
                        handlePaymentSuccess(loadingUrl)
                        return true
                    }
                    loadingUrl.startsWith("myapp://payment-cancel") -> {
                        handlePaymentCancel(loadingUrl)
                        return true
                    }

                    // ✅ PayOS success page - KHÔNG xử lý ngay, đợi deep link
                    loadingUrl.contains("/success") -> {
                        Log.d("WebPayActivity", "PayOS success page detected, waiting for deep link...")
                        return false // Load bình thường, đợi deep link
                    }

                    // ✅ PayOS cancel page
                    loadingUrl.contains("/cancel") -> {
                        handlePaymentCancel(loadingUrl)
                        return true
                    }

                    // ✅ Banking HTTPS URLs - Mở bằng Chrome
                    loadingUrl.startsWith("https://") && isBankingUrl(loadingUrl) -> {
                        return try {
                            Log.d("WebPayActivity", "🌐 Opening banking HTTPS URL: $loadingUrl")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            Toast.makeText(this@WebPayActivity, "Đang mở trong trình duyệt...", Toast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            Log.e("WebPayActivity", "Cannot open banking HTTPS URL: ${e.message}")
                            false
                        }
                    }

                    // ✅ Banking deep link schemes
                    !loadingUrl.startsWith("http") && !loadingUrl.startsWith("https") && isBankingScheme(loadingUrl) -> {
                        return try {
                            Log.d("WebPayActivity", "🏦 Opening banking app: $loadingUrl")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            Toast.makeText(this@WebPayActivity, "Đang chuyển đến app ngân hàng...", Toast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            Log.e("WebPayActivity", "Cannot open banking app: ${e.message}")
                            Toast.makeText(this@WebPayActivity, "Vui lòng cài đặt app ngân hàng để tiếp tục", Toast.LENGTH_LONG).show()
                            false
                        }
                    }

                    // Intent URLs
                    loadingUrl.startsWith("intent://") -> {
                        return try {
                            val intent = Intent.parseUri(loadingUrl, Intent.URI_INTENT_SCHEME)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            true
                        } catch (e: Exception) {
                            Log.e("WebPayActivity", "Cannot parse intent URL: ${e.message}")
                            false
                        }
                    }

                    // Non-HTTP URLs
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
                Log.d("WebPayActivity", "Page finished loading: $url")

                // Lưu URL hợp lệ
                if (url != null && !url.contains("casso.vn") && !url.contains("payos.vn/home")) {
                    lastValidUrl = url
                    Log.d("WebPayActivity", "Saved valid URL: $lastValidUrl")
                }

                view?.setBackgroundColor(Color.WHITE)

                // ✅ Inject JavaScript để tắt popup và force redraw
                view?.evaluateJavascript("""
                    (function() {
                        // Tắt tất cả popup và overlay
                        var popups = document.querySelectorAll('[style*="position: fixed"], [style*="position: absolute"], .modal, .popup, .overlay');
                        popups.forEach(function(popup) {
                            if (popup.style.zIndex > 1000) {
                                popup.style.display = 'none';
                            }
                        });
                        
                        // Override window.open để tránh popup
                        window.open = function(url, name, specs) {
                            window.location.href = url;
                            return null;
                        };
                        
                        // Force redraw
                        if (document.body) {
                            document.body.style.opacity = '0.99';
                            setTimeout(function() { 
                                document.body.style.opacity = '1'; 
                            }, 100);
                        }
                        
                        // Check if this is PayOS home page
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
                    Log.d("WebPayActivity", "Page analysis: $result")

                    try {
                        val jsonResult = org.json.JSONObject(result.replace("\"", ""))
                        val isHomePage = jsonResult.optBoolean("isHomePage", false)

                        if (isHomePage && originalUrl != null) {
                            Log.w("WebPayActivity", "⚠️ Detected redirect to home page, reloading payment URL")
                            view.postDelayed({
                                view.loadUrl(originalUrl!!)
                            }, 1000)
                        }
                    } catch (e: Exception) {
                        Log.e("WebPayActivity", "Error parsing page analysis: ${e.message}")
                    }
                }

                url?.let { checkForPaymentResult(it) }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebPayActivity", "Page started loading: $url")

                when {
                    url == "about:blank" && !isWebViewCrashed -> {
                        Log.w("WebPayActivity", "⚠️ WebView reset to about:blank, reloading original URL")
                        reloadOriginalPaymentUrl()
                    }
                    url?.contains("casso.vn") == true || url?.contains("payos.vn/home") == true -> {
                        Log.w("WebPayActivity", "⚠️ Redirected to PayOS home, reloading payment URL")
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
            // ✅ TẮT onCreateWindow để tránh popup dialog
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                Log.d("WebPayActivity", "🚫 Blocking popup window creation")

                // ✅ Thay vì tạo popup, load URL trong WebView hiện tại
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = view
                resultMsg?.sendToTarget()

                return false // Không tạo window mới
            }

            // ✅ Override các method khác để tránh popup
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                Log.d("WebPayActivity", "JS Alert blocked: $message")
                result?.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                Log.d("WebPayActivity", "JS Confirm blocked: $message")
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
                        
                        // Fix deprecated ACTION_MEDIA_SCANNER_SCAN_FILE
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                        } else {
                            // Use MediaScannerConnection for API 29+
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
            Log.d("WebPayActivity", "🔄 Reloading original payment URL: $url")
            webView.loadUrl(url)
        } ?: run {
            Log.e("WebPayActivity", "❌ No original URL to reload")
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
        Log.d("WebPayActivity", "🔄 Activity resumed")

        setupWindow()

        if (::webView.isInitialized) {
            webView.onResume()
            webView.setBackgroundColor(Color.WHITE)

            if (isWebViewCrashed) {
                Log.w("WebPayActivity", "⚠️ WebView was crashed, reloading...")
                reloadOriginalPaymentUrl()
                isWebViewCrashed = false
            } else if (isWebViewPaused) {
                Log.d("WebPayActivity", "🔄 WebView was paused, checking content...")

                webView.evaluateJavascript("""
                    (function() {
                        var contentLength = document.body ? document.body.innerHTML.length : 0;
                        var isHomePage = document.title.includes('PayOS') && 
                                        !document.title.includes('Thanh toán') &&
                                        !window.location.href.includes('/payment/');
                        var currentUrl = window.location.href;
                        
                        // Tắt popup nếu có
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
                    Log.d("WebPayActivity", "Resume check result: $result")

                    try {
                        val jsonResult = org.json.JSONObject(result.replace("\"", ""))
                        val contentLength = jsonResult.optInt("contentLength", 0)
                        val isHomePage = jsonResult.optBoolean("isHomePage", false)

                        if (contentLength == 0 || isHomePage) {
                            Log.w("WebPayActivity", "⚠️ WebView content is empty or redirected to home, reloading...")
                            reloadOriginalPaymentUrl()
                        }
                    } catch (e: Exception) {
                        Log.e("WebPayActivity", "Error parsing resume check: ${e.message}")
                        reloadOriginalPaymentUrl()
                    }
                }

                isWebViewPaused = false
            }

            // Force redraw
            webView.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        // Tắt popup
                        var popups = document.querySelectorAll('[style*="position: fixed"], [style*="position: absolute"], .modal, .popup, .overlay');
                        popups.forEach(function(popup) {
                            if (popup.style.zIndex > 1000) {
                                popup.style.display = 'none';
                            }
                        });
                        
                        // Force redraw
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
        Log.d("WebPayActivity", "⏸️ Activity paused")

        if (::webView.isInitialized) {
            webView.onPause()
            isWebViewPaused = true
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("WebPayActivity", "⏹️ Activity stopped")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("WebPayActivity", "🔄 Activity restarted")

        setupWindow()

        if (::webView.isInitialized) {
            webView.setBackgroundColor(Color.WHITE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && ::webView.isInitialized) {
            Log.d("WebPayActivity", "🔍 Window focus gained, checking WebView state")

            webView.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        // Tắt tất cả popup
                        var popups = document.querySelectorAll('[style*="position: fixed"], [style*="position: absolute"], .modal, .popup, .overlay');
                        popups.forEach(function(popup) {
                            if (popup.style.zIndex > 1000) {
                                popup.style.display = 'none';
                            }
                        });
                        
                        return window.location.href;
                    })();
                """.trimIndent()) { url ->
                    Log.d("WebPayActivity", "Current URL on focus: $url")

                    if (url.contains("casso.vn") || url.contains("payos.vn/home")) {
                        Log.w("WebPayActivity", "⚠️ Detected home page on focus, reloading payment")
                        reloadOriginalPaymentUrl()
                    }
                }
            }, 300)
        }
    }

    private fun handlePaymentSuccess(url: String) {
        Log.d("WebPayActivity", "Payment Success URL: $url")

        try {
            val uri = Uri.parse(url)
            val orderCode = uri.getQueryParameter("orderCode")
            val status = uri.getQueryParameter("status")
            val paymentLinkId = uri.getQueryParameter("paymentLinkId")
            val code = uri.getQueryParameter("code") // PayOS code parameter

            Log.d("WebPayActivity", "OrderCode: $orderCode, Status: $status, PaymentLinkId: $paymentLinkId, Code: $code")

            // ✅ Cải thiện logic kiểm tra success
            val isValidSuccess = when {
                // Deep link với đầy đủ thông tin
                url.startsWith("myapp://payment-success") && orderCode != null && status == "PAID" -> true

                // PayOS success page với code=00
                url.contains("/success") && code == "00" -> true

                // Fallback: URL chứa success và có parameters
                url.contains("success") && (orderCode != null || status == "PAID" || status == "success") -> true

                else -> false
            }

            if (isValidSuccess) {
                // ✅ Đợi một chút để đảm bảo có đầy đủ thông tin
                if (orderCode == null && url.contains("/success")) {
                    Log.d("WebPayActivity", "⏳ Success page detected but missing orderCode, waiting for deep link...")

                    // Đợi deep link với đầy đủ thông tin (tối đa 10s)
                    webView.postDelayed({
                        // Kiểm tra lại sau 3s
                        webView.evaluateJavascript("window.location.href") { currentUrl ->
                            Log.d("WebPayActivity", "Checking URL after delay: $currentUrl")
                            if (currentUrl.contains("myapp://payment-success")) {
                                // Deep link đã được trigger, không cần xử lý thêm
                                Log.d("WebPayActivity", "Deep link detected, skipping duplicate processing")
                            }
                        }
                    }, 3000)

                    return // Không xử lý ngay, đợi deep link
                }

                savePaymentToFirebase(orderCode, paymentLinkId)
            } else {
                Log.w("WebPayActivity", "Invalid success parameters, ignoring...")
                // ✅ Không hiển thị lỗi ngay, có thể là bước trung gian
            }

        } catch (e: Exception) {
            Log.e("WebPayActivity", "Error parsing payment success URL", e)

            // ✅ Chỉ fallback nếu là deep link hoặc có dấu hiệu rõ ràng
            if (url.startsWith("myapp://payment-success") || url.contains("PAID")) {
                savePaymentToFirebase(null, null)
            }
        }
    }

    private fun handlePaymentCancel(url: String) {
        Log.d("WebPayActivity", "Payment Cancel URL: $url")
        showPaymentResult(false, "Thanh toán đã bị hủy")
    }

    // ✅ Cập nhật checkForPaymentResult để tránh duplicate
    private fun checkForPaymentResult(url: String) {
        // ✅ Chỉ xử lý nếu chưa có deep link
        if (!url.startsWith("myapp://")) {
            val successPatterns = listOf("success")
            val cancelPatterns = listOf("cancel", "failed", "error")

            when {
                successPatterns.any { url.contains(it, ignoreCase = true) } -> {
                    // ✅ Không gọi handlePaymentSuccess ngay, đợi deep link
                    Log.d("WebPayActivity", "Success pattern detected in URL, waiting for deep link...")
                }
                cancelPatterns.any { url.contains(it, ignoreCase = true) } -> {
                    handlePaymentCancel(url)
                }
            }
        }
    }

    private fun savePaymentToFirebase(orderCode: String?, paymentLinkId: String?) {
        // ✅ Tránh duplicate processing
        if (paymentProcessed) {
            Log.d("WebPayActivity", "Payment already processed, skipping...")
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

        Log.d("WebPayActivity", "=== SAVING PAYMENT ===")
        Log.d("WebPayActivity", "Room Number: $roomNumber")
        Log.d("WebPayActivity", "Month: $monthToPayFor")
        Log.d("WebPayActivity", "Amount: $amount")

        database.getReference("rooms")
            .child(roomNumber)
            .child("payments")
            .child(monthToPayFor)
            .setValue(paymentData)
            .addOnSuccessListener {
                Log.d("WebPayActivity", "Payment saved successfully")
                showPaymentResult(true, "Thanh toán thành công!")
            }
            .addOnFailureListener { e ->
                Log.e("WebPayActivity", "Failed to save payment", e)
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
