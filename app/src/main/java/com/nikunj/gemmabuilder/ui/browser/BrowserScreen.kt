package com.nikunj.gemmabuilder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebStorage
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.webkit.WebStorageCompat
import androidx.webkit.PrerenderException
import androidx.webkit.PrerenderOperationCallback
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import kotlin.coroutines.resume

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
    private var previewHtml: String? = null
    private var preparedLink: Pair<String, CancellationSignal>? = null

    @androidx.annotation.OptIn(WebViewCompat.ExperimentalUrlPrerender::class)
    fun prepareLink(page: String, link: BrowserAction, onError: () -> Unit): Boolean {
        val view = webView ?: return false
        if (page != currentUrl || page != view.url || link.kind != BrowserActionKind.Link ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.PRERENDER_WITH_URL)
        ) return false
        cancelPreparedLink()
        val signal = CancellationSignal()
        preparedLink = link.url to signal
        return try {
            WebViewCompat.prerenderUrlAsync(
                view, link.url, signal, androidx.core.content.ContextCompat.getMainExecutor(view.context),
                object : PrerenderOperationCallback {
                    override fun onPrerenderActivated() = Unit
                    override fun onError(exception: PrerenderException) {
                        if (preparedLink?.second === signal) {
                            preparedLink = null
                            onError()
                        }
                    }
                }
            )
            true
        } catch (_: RuntimeException) {
            cancelPreparedLink()
            false
        }
    }

    fun cancelPreparedLink() {
        val prepared = preparedLink
        preparedLink = null
        prepared?.second?.cancel()
    }

    fun applyBrowserAction(page: String, action: BrowserAction, value: String, onFailure: () -> Unit) {
        val view = webView ?: return onFailure()
        if (currentUrl != page || view.url != page || progress < 1f) return onFailure()
        if (action.kind == BrowserActionKind.Link) {
            if (preparedLink?.first == action.url) preparedLink = null
            open(action.url)
        } else if (action.kind == BrowserActionKind.WikipediaSearch) {
            val target = wikipediaSearchUrl(page, value) ?: return onFailure()
            open(target)
        } else {
            view.evaluateJavascript(browserControlScript(action, value)) { result ->
                if (result != "true") onFailure()
            }
        }
    }

    fun clearSiteData(url: String) {
        if (url != currentUrl || !WebPolicy.isAllowed(url)) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) return
        close()
        try {
            WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), url) {
                if (currentUrl == null) blockedNotice = "Site data cleared."
            }
        } catch (_: IllegalArgumentException) {
            blockedNotice = "Couldn't clear data for this site."
        }
    }

    suspend fun actionsOnPage(url: String): List<BrowserAction> {
        val view = webView ?: return emptyList()
        if (url != currentUrl || url != view.url || progress < 1f) return emptyList()
        val result = withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<String?> { continuation ->
                view.evaluateJavascript(BROWSER_ACTION_SNAPSHOT) { json ->
                    if (continuation.isActive) continuation.resume(json)
                }
            }
        }
        return if (url == currentUrl && url == view.url) browserActions(result, url) else emptyList()
    }

    fun open(target: String) {
        cancelPreparedLink()
        if (!WebPolicy.isAllowed(target)) {
            blockedNotice = "Only public web pages can be opened."
            return
        }
        blockedNotice = null
        previewHtml = null
        currentUrl = target
        address = TextFieldValue(target)
        webView?.loadUrl(target)
    }

    /** An in-memory page has an opaque origin and no access to app or phone files. */
    fun openHtml(html: String) {
        cancelPreparedLink()
        previewHtml = html
        blockedNotice = null
        currentUrl = "about:blank"
        address = TextFieldValue("HTML preview")
        webView?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    /** Back to the empty start page, forgetting this page's history. */
    fun close() {
        cancelPreparedLink()
        previewHtml = null
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
        cancelPreparedLink()
        webView?.destroy()
        webView = null
    }

    companion object {
        /** Keeps the open page's address if Android stops the app in the background. */
        val Saver: Saver<BrowserSession, String> = Saver(
            save = { it.currentUrl?.takeIf { url -> WebPolicy.isAllowed(url) } },
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
                    if (previewHtml != null && url.startsWith("data:")) return
                    previewHtml = null
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
            val html = previewHtml
            if (html != null) loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            else currentUrl?.let { loadUrl(it) }
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
    canSuggestAction: Boolean,
    onSuggestAction: suspend (String, List<BrowserAction>) -> BrowserAction?,
    onAskAboutPage: (PageText) -> Unit
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pageBackground = MaterialTheme.colorScheme.background.toArgb()
    val currentUrl = session.currentUrl
    val scope = rememberCoroutineScope()
    var pageMenu by remember { mutableStateOf(false) }
    var clearSite by remember { mutableStateOf<String?>(null) }
    var actionRequestPage by remember { mutableStateOf<String?>(null) }
    var actionRequest by remember { mutableStateOf("") }
    var findingAction by remember { mutableStateOf(false) }
    var suggestedAction by remember { mutableStateOf<BrowserAction?>(null) }
    var fieldValue by remember { mutableStateOf("") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    val canClearSite = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
    }
    val canPrerender = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.PRERENDER_WITH_URL)
    }

    LaunchedEffect(requestedUrl) {
        if (requestedUrl != null) {
            session.open(requestedUrl)
            onRequestedUrlOpened()
        }
    }
    LaunchedEffect(currentUrl) {
        if (actionRequestPage != currentUrl) {
            actionRequestPage = null
            suggestedAction = null
            actionFeedback = null
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
            if (currentUrl != null && WebPolicy.isAllowed(currentUrl)) {
                Box {
                    IconButton(onClick = { pageMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Page options")
                    }
                    DropdownMenu(expanded = pageMenu, onDismissRequest = { pageMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Act on this page") },
                            enabled = canSuggestAction && !findingAction && session.progress >= 1f && currentUrl.startsWith("https://"),
                            onClick = {
                                pageMenu = false
                                actionRequest = ""
                                actionRequestPage = currentUrl
                            }
                        )
                        if (canClearSite) {
                            DropdownMenuItem(
                                text = { Text("Clear site data") },
                                onClick = { pageMenu = false; clearSite = currentUrl }
                            )
                        }
                    }
                }
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
        if (findingAction || actionFeedback != null) {
            Text(
                if (findingAction) "Finding a page action…" else actionFeedback.orEmpty(),
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
                        if (!canAsk || session.progress < 1f) return@ExtendedFloatingActionButton
                        val view = session.webView ?: return@ExtendedFloatingActionButton
                        val sourceUrl = view.url ?: currentUrl
                        view.evaluateJavascript(PAGE_EXTRACT_SCRIPT) { json ->
                            if (view.url != sourceUrl || session.currentUrl == null) return@evaluateJavascript
                            parseExtractedPage(json, sourceUrl)
                                ?.takeIf { it.text.isNotBlank() || it.structuredData.isNotBlank() }
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
    clearSite?.let { url ->
        AlertDialog(
            onDismissRequest = { clearSite = null },
            title = { Text("Clear site data?") },
            text = { Text("This closes the page and removes cookies and stored data for ${hostOf(url)} and related subdomains. You may be signed out.") },
            confirmButton = {
                TextButton(onClick = { clearSite = null; session.clearSiteData(url) }) { Text("Clear data") }
            },
            dismissButton = { TextButton(onClick = { clearSite = null }) { Text("Cancel") } }
        )
    }
    val requestPage = actionRequestPage
    val destination = suggestedAction
    if (requestPage != null && requestPage == currentUrl && destination != null) {
        var preparingLink by remember(destination) { mutableStateOf(false) }
        DisposableEffect(destination) {
            onDispose { session.cancelPreparedLink() }
        }
        val actionName = when (destination.kind) {
            BrowserActionKind.Link -> "Open link"
            BrowserActionKind.Search -> "Fill search field"
            BrowserActionKind.Disclosure -> "Expand section"
            BrowserActionKind.WikipediaMenu -> "Open menu"
            BrowserActionKind.WikipediaSearch -> "Search Wikipedia"
        }
        val needsText = destination.kind in listOf(BrowserActionKind.Search, BrowserActionKind.WikipediaSearch)
        AlertDialog(
            onDismissRequest = { suggestedAction = null; actionRequestPage = null },
            title = { Text(actionName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(destination.label)
                    when (destination.kind) {
                        BrowserActionKind.Link -> {
                            Text(destination.url)
                            if (canPrerender) {
                                TextButton(enabled = !preparingLink, onClick = {
                                    preparingLink = session.prepareLink(requestPage, destination) {
                                        preparingLink = false
                                    }
                                }) { Text(if (preparingLink) "Preloading…" else "Preload link") }
                            }
                        }
                        BrowserActionKind.Search, BrowserActionKind.WikipediaSearch -> TextField(
                            value = fieldValue,
                            onValueChange = { fieldValue = it.take(300) },
                            label = { Text("Search text") },
                            supportingText = if (destination.kind == BrowserActionKind.Search) {
                                { Text("You can submit the search on the page.") }
                            } else null,
                            singleLine = true
                        )
                        BrowserActionKind.Disclosure, BrowserActionKind.WikipediaMenu -> Unit
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !needsText || fieldValue.isNotBlank(), onClick = {
                    session.applyBrowserAction(requestPage, destination, fieldValue) {
                        actionFeedback = "The page changed. Try the action again."
                    }
                    suggestedAction = null
                    actionRequestPage = null
                }) { Text(actionName) }
            },
            dismissButton = {
                TextButton(onClick = { suggestedAction = null; actionRequestPage = null }) { Text("Cancel") }
            }
        )
    } else if (requestPage != null && requestPage == currentUrl && !findingAction) {
        AlertDialog(
            onDismissRequest = { actionRequestPage = null },
            title = { Text("Act on this page") },
            text = {
                TextField(
                    value = actionRequest,
                    onValueChange = { actionRequest = it.take(300) },
                    label = { Text("What do you want to do?") },
                    placeholder = { Text("Open the shipping information") }
                )
            },
            confirmButton = {
                TextButton(enabled = actionRequest.isNotBlank() && canSuggestAction, onClick = {
                    val request = actionRequest.trim()
                    findingAction = true
                    actionFeedback = null
                    scope.launch {
                        try {
                            val link = onSuggestAction(request, session.actionsOnPage(requestPage))
                            if (actionRequestPage == requestPage && session.currentUrl == requestPage) {
                                fieldValue = ""
                                suggestedAction = link
                                if (link == null) {
                                    actionFeedback = "No matching action found. Try a visible link, search field, or expandable section."
                                    actionRequestPage = null
                                }
                            }
                        } finally {
                            findingAction = false
                        }
                    }
                }) { Text("Find action") }
            },
            dismissButton = { TextButton(onClick = { actionRequestPage = null }) { Text("Cancel") } }
        )
    }
}
