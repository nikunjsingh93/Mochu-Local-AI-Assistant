package com.nikunj.gemmabuilder

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/** Pulls the title and main-content text out of the loaded page. */
const val PAGE_EXTRACT_SCRIPT = """
(function () {
  var root = document.querySelector('main, article, [role=main]') || document.body;
  if (!root) return JSON.stringify({ title: document.title, text: '' });
  var clone = root.cloneNode(true);
  clone.querySelectorAll('script, style, noscript, svg, nav, footer, header, aside, form, iframe, [aria-hidden=true]')
    .forEach(function (n) { n.remove(); });
  return JSON.stringify({ title: document.title, text: (clone.innerText || '').slice(0, 40000) });
})()
"""

/**
 * Loads [url] in an off-screen WebView (a real Chrome engine, so JavaScript-rendered pages and most
 * bot checks work) and returns its readable text, or null on timeout or error.
 */
@SuppressLint("SetJavaScriptEnabled")
suspend fun readWithWebView(context: Context, url: String, timeoutMs: Long = 25_000): PageText? =
    withContext(Dispatchers.Main) {
        val webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = WEB_USER_AGENT
            settings.blockNetworkImage = true
            // A phone-sized layout so responsive pages render their normal mobile content.
            measure(1080, 2400)
            layout(0, 0, 1080, 2400)
        }
        try {
            withTimeoutOrNull(timeoutMs) {
                val loaded = suspendCancellableCoroutine { cont ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            !WebPolicy.isAllowed(request.url.toString())

                        override fun onPageFinished(view: WebView, finishedUrl: String) {
                            if (cont.isActive) cont.resume(true)
                        }
                    }
                    webView.loadUrl(url)
                }
                if (!loaded) return@withTimeoutOrNull null
                // Many shops render listings well after the load event and only as you scroll. Scroll a
                // step at a time and stop once the page text has stopped growing (or after ~12 s).
                var lastLength = -1
                var stablePolls = 0
                for (step in 1..12) {
                    delay(1_000)
                    val length = suspendCancellableCoroutine { cont ->
                        webView.evaluateJavascript(
                            "window.scrollTo(0, document.body.scrollHeight * $step / 12); (document.body.innerText || '').length"
                        ) { result -> if (cont.isActive) cont.resume(result?.toIntOrNull() ?: 0) }
                    }
                    stablePolls = if (length == lastLength) stablePolls + 1 else 0
                    lastLength = length
                    if (stablePolls >= 2 && length >= 1_500) break
                }
                val json = suspendCancellableCoroutine { cont ->
                    webView.evaluateJavascript(PAGE_EXTRACT_SCRIPT) { result -> if (cont.isActive) cont.resume(result) }
                }
                parseExtractedPage(json, webView.url ?: url)
            }
        } catch (e: Exception) {
            null
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

/** Parses the result of [PAGE_EXTRACT_SCRIPT] as returned by WebView.evaluateJavascript. */
fun parseExtractedPage(json: String?, url: String): PageText? {
    // evaluateJavascript returns a JSON-encoded string literal; unwrap it, then parse the object.
    val payload = runCatching { JSONArray("[" + json + "]").getString(0) }.getOrNull() ?: return null
    val obj = runCatching { org.json.JSONObject(payload) }.getOrNull() ?: return null
    return PageText(
        title = obj.optString("title").trim(),
        url = url,
        text = obj.optString("text").lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    )
}
