package com.nikunj.gemmabuilder

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

enum class BrowserActionKind { Link, Search, Disclosure, WikipediaMenu, WikipediaSearch }

data class BrowserAction(
    val label: String,
    val url: String,
    val kind: BrowserActionKind = BrowserActionKind.Link,
    val index: Int = -1,
    val documentTime: Double = 0.0,
    val hint: String = ""
)

/** Collect ordinary visible links from the top-level page. Field values and cookies stay in WebView. */
private const val WIKIPEDIA_MOBILE_ORIGIN = "https://en.m.wikipedia.org"
private const val WIKIPEDIA_MENU_LABEL = "Wikipedia main menu"
private const val WIKIPEDIA_SEARCH_LABEL = "Search Wikipedia articles"
private fun isWikipediaMobile(page: URI): Boolean =
    page.scheme == "https" && page.host == "en.m.wikipedia.org" && page.port in listOf(-1, 443)

internal fun wikipediaSearchUrl(page: String, query: String): String? {
    val uri = runCatching { URI(page) }.getOrNull() ?: return null
    val text = query.trim().take(300)
    if (!isWikipediaMobile(uri) || uri.userInfo != null || text.isBlank()) return null
    return "$WIKIPEDIA_MOBILE_ORIGIN/w/index.php?search=" + java.net.URLEncoder.encode(text, "UTF-8")
}

private const val BROWSER_ACTION_SCRIPT = """
(function (action) {
  function visible(node) {
    return !node.closest('[hidden], [aria-hidden=true], [inert]') &&
      node.getClientRects().length && getComputedStyle(node).visibility === 'visible';
  }
  function text(value) { return (value || '').replace(/\s+/g, ' ').trim().slice(0, 100); }
  function wikipediaMenu() {
    if (location.origin !== '$WIKIPEDIA_MOBILE_ORIGIN') return null;
    var menu = document.querySelector('label#mw-mf-main-menu-button[for="main-menu-input"]');
    var input = document.querySelector('input#main-menu-input[type=checkbox]');
    return menu && input && !input.form && !input.checked && menu.getClientRects().length &&
      getComputedStyle(menu).visibility === 'visible' ? menu : null;
  }
  var controls = document.querySelectorAll('input[type=search], details > summary:first-of-type');
  function describe(node, index) {
    if (!visible(node) || node.namespaceURI !== 'http://www.w3.org/1999/xhtml') return null;
    if (node.tagName === 'INPUT' && node.type === 'search' && !node.disabled && !node.readOnly &&
        !(node.form && (node.form.method.toLowerCase() !== 'get' ||
          node.form.querySelector('input[type=password], input[type=file], input[autocomplete]:not([autocomplete=off]):not([autocomplete=on])')))) {
      var label = text(node.getAttribute('aria-label') || (node.labels[0] || {}).innerText ||
        node.placeholder || 'Search');
      return {kind: 'Search', label: label, index: index, documentTime: performance.timeOrigin,
        hint: [node.name, node.id, node.autocomplete, node.form && node.form.action].join(' ').slice(0, 1000)};
    }
    if (node.tagName === 'SUMMARY' && node.parentElement.tagName === 'DETAILS') {
      return {kind: 'Disclosure', label: text(node.innerText), index: index, documentTime: performance.timeOrigin};
    }
    return null;
  }
  if (action) {
    if (location.href !== action.url || performance.timeOrigin !== action.documentTime) return false;
    if (action.kind === 'WikipediaMenu') {
      var menu = wikipediaMenu();
      if (!menu) return false;
      menu.click();
      return true;
    }
    var node = controls[action.index];
    var current = node && describe(node, action.index);
    if (!current || current.kind !== action.kind || current.label !== action.label ||
        (current.hint || '') !== action.hint) return false;
    if (action.kind === 'Search') {
      // Populate the native field without submit, Enter, input or change events.
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(node, action.value);
    } else {
      node.parentElement.open = true;
    }
    node.scrollIntoView({block: 'center'});
    return true;
  }
  var links = [];
  if (wikipediaMenu()) links.push({kind: 'WikipediaMenu', label: '$WIKIPEDIA_MENU_LABEL',
    index: 0, documentTime: performance.timeOrigin});
  for (var i = 0; i < Math.min(controls.length, 100); i++) {
    var control = describe(controls[i], i);
    if (control && control.label) links.push(control);
    if (links.length >= 15) break;
  }
  for (var node of document.querySelectorAll('a[href]')) {
    if (node.closest('form, [hidden], [aria-hidden=true]') || !node.getClientRects().length ||
        getComputedStyle(node).visibility !== 'visible' || node.hasAttribute('download') ||
        node.hasAttribute('onclick') || (node.getAttribute('role') || 'link') !== 'link') continue;
    var label = (node.innerText || node.getAttribute('aria-label') || '').trim().slice(0, 100);
    if (label && node.origin === location.origin && node.href.length <= 1000) links.push({label: label, url: node.href});
    if (links.length >= 100) break;
  }
  return JSON.stringify(links);
})
"""

internal val BROWSER_ACTION_SNAPSHOT = "$BROWSER_ACTION_SCRIPT(null)"

private val sensitiveLink = Regex(
    """\b(delete|remove|pay|payment|billing|checkout|purchase|order|password|login|logout|signin|signup|account|unsubscribe|authorize|confirm|submit|transfer|cancel|revoke|sign[\s_-]*(in|out|up)|log[\s_-]*(in|out))\b""",
    RegexOption.IGNORE_CASE
)
private val sensitiveField = Regex("""password|passcode|one.time|credential|token|credit|card.number|security.code|cc-|email|phone""", RegexOption.IGNORE_CASE)

internal fun browserActions(result: String?, pageUrl: String): List<BrowserAction> {
    if (!WebPolicy.isAllowed(pageUrl)) return emptyList()
    val page = URI(pageUrl)
    if (page.scheme != "https" || page.userInfo != null) return emptyList()
    val array = runCatching { JSONArray(JSONArray("[$result]").getString(0)) }.getOrNull() ?: return emptyList()
    return (0 until minOf(100, array.length())).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val label = item.optString("label").replace(Regex("""\s+"""), " ").trim().take(100)
        if (label.isBlank() || sensitiveLink.containsMatchIn(label.replace('_', ' '))) return@mapNotNull null
        val kind = item.optString("kind")
        if (kind == "WikipediaMenu") {
            val documentTime = item.optDouble("documentTime", 0.0)
            if (!isWikipediaMobile(page) ||
                label != WIKIPEDIA_MENU_LABEL || !documentTime.isFinite() || documentTime <= 0
            ) return@mapNotNull null
            return@mapNotNull BrowserAction(label, pageUrl, BrowserActionKind.WikipediaMenu, documentTime = documentTime)
        }
        if (kind == "Search" || kind == "Disclosure") {
            val hint = item.optString("hint")
            val controlIndex = item.optInt("index", -1)
            val documentTime = item.optDouble("documentTime", 0.0)
            if (controlIndex !in 0..99 || !documentTime.isFinite() || documentTime <= 0 ||
                sensitiveField.containsMatchIn("$label $hint") || sensitiveLink.containsMatchIn(hint.replace('_', ' '))
            ) return@mapNotNull null
            return@mapNotNull BrowserAction(label, pageUrl, BrowserActionKind.valueOf(kind), controlIndex, documentTime, hint)
        }
        if (kind.isNotEmpty()) return@mapNotNull null
        val url = item.optString("url").takeIf { it.length <= 1000 } ?: return@mapNotNull null
        val target = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
        if (label.isBlank() || !WebPolicy.isAllowed(url) || target.scheme != page.scheme ||
            !target.host.equals(page.host, ignoreCase = true) || target.port != page.port ||
            target.userInfo != null || target.query != null || target.fragment != null ||
            target.path == page.path || sensitiveLink.containsMatchIn("$label ${target.path.replace('_', ' ')}")
        ) return@mapNotNull null
        BrowserAction(label, url)
    }.distinctBy { Triple(it.kind, it.url, it.index) }.let { actions ->
        if (isWikipediaMobile(page)) {
            listOf(BrowserAction(WIKIPEDIA_SEARCH_LABEL, pageUrl, BrowserActionKind.WikipediaSearch)) + actions
        } else actions
    }.take(40)
}

internal fun browserControlScript(action: BrowserAction, value: String): String {
    require(action.kind != BrowserActionKind.Link)
    val arguments = JSONObject()
        .put("kind", action.kind.name).put("label", action.label).put("url", action.url)
        .put("index", action.index).put("documentTime", action.documentTime)
        .put("hint", action.hint).put("value", value.take(300))
    return "$BROWSER_ACTION_SCRIPT($arguments)"
}

internal fun selectedBrowserAction(reply: String, links: List<BrowserAction>): BrowserAction? {
    val number = reply.trim().toIntOrNull() ?: return null
    return if (number in 1..links.size) links[number - 1] else null
}
