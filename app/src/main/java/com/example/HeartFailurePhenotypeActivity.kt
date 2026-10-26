package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

/** Offline research worksheet; separate from ECG report storage and upload paths. */
class HeartFailurePhenotypeActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        uploadCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        uploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "file" && uri.path == "/android_asset/heart_failure/index.html")
                    return false
                if (uri.scheme == "https") startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = callback
                return try {
                    filePicker.launch(params.createIntent())
                    true
                } catch (_: Exception) {
                    uploadCallback?.onReceiveValue(null)
                    uploadCallback = null
                    false
                }
            }
        }
        webView.loadUrl("file:///android_asset/heart_failure/index.html")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
