package com.app.buildingmanagement

import android.app.DownloadManager
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.app.buildingmanagement.ui.theme.BuildingManagementTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

class WebPayActivity : ComponentActivity() {

    private var webView: WebView? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var isWebViewPaused = false
    private var originalUrl: String? = null
    private var isWebViewCrashed = false
    private var lastValidUrl: String? = null
    private var paymentProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        originalUrl = intent.getStringExtra("url")

        setupWindow()

        setContent {
            BuildingManagementTheme {
                WebPayScreen(
                    onWebViewCreated = { webView ->
                        this.webView = webView
                        setupWebView(webView)
                    }
                )
            }
        }

        setupBackPressedHandler()
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
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }

            decorView.setBackgroundColor(Color.WHITE)
        }
    }

    private fun setupWebView(webView: WebView) {
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

            // ✅ THÊM CÁC SETTINGS THIẾU từ so sánh AppCompatActivity:
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

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

        webView.fitsSystemWindows = true

        setupWebViewClient(webView)
        setupWebChromeClient(webView)

        originalUrl?.let { webView.loadUrl(it) }
    }

    private fun setupWebViewClient(webView: WebView) {
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
                            val intent = Intent(Intent.ACTION_VIEW, loadingUrl.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            Toast.makeText(this@WebPayActivity, "Đang mở trong trình duyệt...", Toast.LENGTH_SHORT).show()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }

                    !loadingUrl.startsWith("http") && !loadingUrl.startsWith("https") && isBankingScheme(loadingUrl) -> {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, loadingUrl.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            Toast.makeText(this@WebPayActivity, "Đang chuyển đến app ngân hàng...", Toast.LENGTH_SHORT).show()
                            true
                        } catch (_: Exception) {
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
                        } catch (_: Exception) {
                            false
                        }
                    }

                    !loadingUrl.startsWith("http") && !loadingUrl.startsWith("https") -> {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, loadingUrl.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            true
                        } catch (_: Exception) {
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

                // ✅ SỬ DỤNG LOGIC JAVASCRIPT GIỐNG Y CHANG APPCOMPATACTIVITY CŨ
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
                    } catch (_: Exception) {
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

    private fun setupWebChromeClient(webView: WebView) {
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
                if (webView != null && webView!!.canGoBack()) {
                    webView!!.goBack()
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

    private fun showPaymentResult(success: Boolean, message: String) {
        runOnUiThread {
            Toast.makeText(this, message, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

            webView?.postDelayed({
                setResult(if (success) RESULT_OK else RESULT_CANCELED)
                finish()
            }, if (success) 2000 else 1000)
        }
    }

    private fun reloadOriginalPaymentUrl() {
        originalUrl?.let { url ->
            webView?.loadUrl(url)
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

        webView?.let { webView ->
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
                    } catch (_: Exception) {
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

        webView?.onPause()
        isWebViewPaused = true
    }

    override fun onRestart() {
        super.onRestart()

        setupWindow()

        webView?.setBackgroundColor(Color.WHITE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && webView != null) {

            webView!!.postDelayed({
                webView!!.evaluateJavascript("""
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
            val uri = url.toUri()
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

                    webView?.postDelayed({
                        webView?.evaluateJavascript("window.location.href") { currentUrl ->
                            if (currentUrl.contains("myapp://payment-success")) {
                                return@evaluateJavascript // Deep link detected, no duplicate processing needed
                            }
                        }
                    }, 3000)

                    return
                }

                savePaymentToFirebase(orderCode, paymentLinkId)
            }

        } catch (_: Exception) {
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
        val buildingIdFromIntent = intent.getStringExtra("buildingId")
        val roomIdFromIntent = intent.getStringExtra("roomId")

        if (roomNumber == null || monthToPayFor == null) {
            showPaymentResult(false, "Thiếu thông tin phòng hoặc tháng thanh toán")
            return
        }

        // If we have buildingId and roomId from intent, use them directly
        if (buildingIdFromIntent != null && roomIdFromIntent != null) {
            Log.d("WebPayActivity", "🎯 Using buildingId and roomId from intent: $buildingIdFromIntent/$roomIdFromIntent")
            saveToNewStructure(buildingIdFromIntent, roomIdFromIntent, monthToPayFor, orderCode, paymentLinkId, amount, phone, roomNumber)
            return
        }

        // Convert phone number format (+84 -> 0)
        val phoneFormatted = phone.replace("+84", "0")

        // Fallback: get buildingId and roomId from phone_to_room
        database.getReference("phone_to_room")
            .child(phoneFormatted)
            .get()
            .addOnSuccessListener { phoneSnapshot ->
                if (phoneSnapshot.exists()) {
                    val buildingId = phoneSnapshot.child("buildingId").getValue(String::class.java)
                    val roomId = phoneSnapshot.child("roomId").getValue(String::class.java)

                    if (buildingId != null && roomId != null) {
                        // Save to new structure
                        saveToNewStructure(buildingId, roomId, monthToPayFor, orderCode, paymentLinkId, amount, phone, roomNumber)
                    } else {
                        // Fallback to old structure
                        saveToOldStructure(roomNumber, monthToPayFor, orderCode, paymentLinkId, amount, phone)
                    }
                } else {
                    // Fallback to old structure
                    saveToOldStructure(roomNumber, monthToPayFor, orderCode, paymentLinkId, amount, phone)
                }
            }
            .addOnFailureListener {
                // Fallback to old structure
                saveToOldStructure(roomNumber, monthToPayFor, orderCode, paymentLinkId, amount, phone)
            }
    }

    private fun saveToNewStructure(
        buildingId: String,
        roomId: String,
        monthToPayFor: String,
        orderCode: String?,
        paymentLinkId: String?,
        amount: Int,
        phone: String,
        roomNumber: String
    ) {
        Log.d("WebPayActivity", "💾 Saving payment to NEW structure: buildings/$buildingId/rooms/$roomId/payments/$monthToPayFor")
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

        database.getReference("buildings")
            .child(buildingId)
            .child("rooms")
            .child(roomId)
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

    private fun saveToOldStructure(
        roomNumber: String,
        monthToPayFor: String,
        orderCode: String?,
        paymentLinkId: String?,
        amount: Int,
        phone: String
    ) {
        Log.d("WebPayActivity", "💾 Saving payment to OLD structure: rooms/$roomNumber/payments/$monthToPayFor")
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
}

@Composable
private fun WebPayScreen(
    onWebViewCreated: (WebView) -> Unit
) {

    // ✅ Mimic ConstraintLayout behavior từ XML
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars) // Giống fitsSystemWindows="true"
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(Color.WHITE)

                    // ✅ QUAN TRỌNG: Set layout params giống XML
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    onWebViewCreated(this)

                    (context as? WebPayActivity)?.setupDownloadListener(this)
                }
            },
            update = { webView ->
                webView.setBackgroundColor(Color.WHITE)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Extension function để setup download listener
private fun WebPayActivity.setupDownloadListener(webView: WebView) {
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
                    val request = DownloadManager.Request(url.toUri()).apply {
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

                    val dm = this@setupDownloadListener.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(this@setupDownloadListener, "Đang tải file...", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    Toast.makeText(this@setupDownloadListener, "Không thể tải file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                Toast.makeText(this@setupDownloadListener, "Không thể tải file không hợp lệ", Toast.LENGTH_SHORT).show()
            }
        }
    }
}