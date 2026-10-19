package com.nikunj.gemmabuilder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
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
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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

/**
 * The Browser tab's page and WebView. It outlives the tab, so the page (and its back history) is
 * still there after you switch to Chat and come back.
 */
class BrowserSession {
    var webView: WebView? = null
        private set
    var currentUrl by mutableStateOf<String?>(null)
    var address by mutableStateOf(TextFieldValue(""))
    var progress by mutableFloatStateOf(1f)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blockedNotice by mutableStateOf<String?>(null)

    fun open(target: String) {
        if (!WebPolicy.isAllowed(target)) {
            blockedNotice = "Only public web pages can be opened."
            return
        }
        blockedNotice = null
        currentUrl = target
        address = TextFieldValue(target)
        webView?.loadUrl(target)
    }

    /** Back to the empty start page, forgetting this page's history. */
    fun close() {
        releaseFocus()
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
        }
        currentUrl = null
        address = TextFieldValue("")
        progress = 1f
        canGoBack = false
        canGoForward = false
        blockedNotice = null
    }

    /**
     * Call before the Browser leaves the screen. Removing a focused WebView while Compose applies
     * changes makes Android move focus into the next screen mid-update, which crashes Compose.
     */
    fun releaseFocus() {
        webView?.clearFocus()
    }

    fun destroy() {
        webView?.destroy()
        webView = null
    }

    companion object {
        /** Keeps the open page's address if Android stops the app in the background. */
        val Saver: Saver<BrowserSession, String> = Saver(
            save = { it.currentUrl },
            restore = { url -> BrowserSession().apply { open(url) } }
        )
    }

    /** The session's WebView, created on first use and detached from its old screen when reused. */
    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(context: Context, background: Int): WebView {
        webView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        return WebView(context).apply {
            setBackgroundColor(background)
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
                    if (url == "about:blank") return
                    address = TextFieldValue(url)
                    currentUrl = url
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    this@BrowserSession.progress = newProgress / 100f
                }
            }
            webView = this
            currentUrl?.let { loadUrl(it) }
        }
    }
}

/** In-app browser. Pages stay inside the app; the assistant can read the current one on request. */
@Composable
fun BrowserScreen(
    session: BrowserSession,
    requestedUrl: String?,
    onRequestedUrlOpened: () -> Unit,
    canAsk: Boolean,
    onAskAboutPage: (PageText) -> Unit
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pageBackground = MaterialTheme.colorScheme.background.toArgb()
    val currentUrl = session.currentUrl

    LaunchedEffect(requestedUrl) {
        if (requestedUrl != null) {
            session.open(requestedUrl)
            onRequestedUrlOpened()
        }
    }
    BackHandler(enabled = session.canGoBack) { session.webView?.goBack() }

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
                    value = session.address,
                    onValueChange = { session.address = it },
                    singleLine = true,
                    placeholder = { Text("Search or type a web address") },
                    leadingIcon = {
                        Icon(
                            if (currentUrl?.startsWith("https://") == true) Icons.Rounded.Lock else Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (currentUrl != null || session.address.text.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                focus.clearFocus()
                                session.close()
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close page", modifier = Modifier.size(20.dp))
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val text = session.address.text
                        if (text.isNotBlank()) session.open(browserTarget(text))
                        focus.clearFocus()
                        keyboard?.hide()
                    }),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Select the address on tap, so typing starts a new search right away.
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                session.address = session.address.copy(selection = TextRange(0, session.address.text.length))
                            }
                        }
                )
            }
            IconButton(onClick = { session.webView?.reload() }, enabled = currentUrl != null) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reload")
            }
        }
        if (session.progress < 1f && currentUrl != null) {
            LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        session.blockedNotice?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            // Shown only once there's a page: an empty WebView paints white over the address bar.
            if (currentUrl != null) {
                AndroidView(
                    factory = { context -> session.attachWebView(context, pageBackground) },
                    // The WebView belongs to the session and is reused when the tab comes back.
                    onRelease = {},
                    modifier = Modifier.fillMaxSize()
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        val view = session.webView ?: return@ExtendedFloatingActionButton
                        view.evaluateJavascript(PAGE_EXTRACT_SCRIPT) { json ->
                            parseExtractedPage(json, view.url ?: currentUrl)
                                ?.takeIf { it.text.isNotBlank() }
                                ?.let { page ->
                                    session.releaseFocus()
                                    onAskAboutPage(page)
                                }
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
                            IconButton(onClick = { session.webView?.goBack() }, enabled = session.canGoBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                            IconButton(onClick = { session.webView?.goForward() }, enabled = session.canGoForward) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                            }
                        }
                    }
                }
            } else {
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
        }
    }
}
