package com.nikunj.gemmabuilder

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

/** Typed text becomes a URL when it looks like one, otherwise a DuckDuckGo search. */
fun browserTarget(input: String): String {
    val text = input.trim()
    val looksLikeUrl = !text.contains(' ') && (text.startsWith("http://") || text.startsWith("https://") || text.contains('.'))
    return when {
        text.startsWith("http://") || text.startsWith("https://") -> text
        looksLikeUrl -> "https://$text"
        else -> "https://duckduckgo.com/?q=" + URLEncoder.encode(text, "UTF-8")
    }
}

/** In-app browser. Pages stay inside the app; the assistant can read the current one on request. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    requestedUrl: String?,
    onRequestedUrlOpened: () -> Unit,
    canAsk: Boolean,
    onAskAboutPage: (PageText) -> Unit
) {
    val focus = LocalFocusManager.current
    var address by rememberSaveable { mutableStateOf("") }
    var currentUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(1f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var blockedNotice by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun open(target: String) {
        if (!WebPolicy.isAllowed(target)) {
            blockedNotice = "Only public web pages can be opened."
            return
        }
        blockedNotice = null
        currentUrl = target
        address = target
        webView?.loadUrl(target)
    }

    LaunchedEffect(requestedUrl) {
        if (requestedUrl != null) {
            open(requestedUrl)
            onRequestedUrlOpened()
        }
    }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    placeholder = { Text("Search or type a web address") },
                    leadingIcon = {
                        Icon(
                            if (currentUrl?.startsWith("https://") == true) Icons.Rounded.Lock else Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (address.isNotBlank()) open(browserTarget(address))
                        focus.clearFocus()
                    }),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = { webView?.reload() }, enabled = currentUrl != null) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reload")
            }
        }
        if (progress < 1f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        blockedNotice?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = WEB_USER_AGENT
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val allowed = WebPolicy.isAllowed(request.url.toString())
                                if (!allowed) blockedNotice = "Blocked a link to a non-public address."
                                return !allowed
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                address = url
                                currentUrl = url
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        webView = this
                        currentUrl?.let { loadUrl(it) }
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize()
            )
            if (currentUrl == null) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(28.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Browse the web", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Search or open any page, then tap Ask to have the on-device model summarize it or answer questions about it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (currentUrl != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val view = webView ?: return@ExtendedFloatingActionButton
                        view.evaluateJavascript(PAGE_EXTRACT_SCRIPT) { json ->
                            parseExtractedPage(json, view.url ?: currentUrl.orEmpty())
                                ?.takeIf { it.text.isNotBlank() }
                                ?.let(onAskAboutPage)
                        }
                    },
                    expanded = true,
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    text = { Text("Ask about this page") },
                    containerColor = if (canAsk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row {
                            IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                            IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                            }
                        }
                    }
                }
            }
        }
    }
}
