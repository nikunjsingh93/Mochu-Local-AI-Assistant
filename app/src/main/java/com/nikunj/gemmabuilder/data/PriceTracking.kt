package com.nikunj.gemmabuilder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Price tracking: when a chat or scheduled task asks for the price of a linked product, the model
// pulls out just the product and its price, the app saves it with the date, and the answer gets a
// product card plus a price-history chart. Rich blocks are fenced JSON (```card / ```chart) that
// the chat renders as native UI and shows as text if they can't be read.

data class ProductInfo(
    val title: String,
    val price: Double,
    val currency: String,
    val url: String,
    val store: String,
    /** The store's usual price shown next to a deal ("Typical price", "List price", "Was"), if any. */
    val listPrice: Double? = null
)

data class PricePoint(val at: Long, val price: Double)

private val priceWords = Regex("""\b(price|prices|cost|costs|how\s+much|deal|deals|cheaper|discount|sale|on\s+sale|track)\b""", RegexOption.IGNORE_CASE)

/** A request about the price of something it links to. */
fun wantsPrice(request: String): Boolean = priceWords.containsMatchIn(request) && linksIn(request).isNotEmpty()

private const val EXTRACT_SYSTEM = "You extract product data from web pages. Reply with only one line of JSON and nothing else."

/** The prompt asking the model for the product name and its current price on [page]. */
fun productExtractionPrompt(page: PageText, request: String): String =
    "The user asked: \"${request.take(300)}\"\n\n" +
        "Page: ${page.title}\n${page.text.take(3500)}\n\n" +
        "From this page, give the main product's name and the current price the user asked about. " +
        "If the page also shows a usual price (\"Typical price\", \"List price\", \"Was\"), give it as listPrice. " +
        "Reply with only this JSON: {\"title\": \"short product name\", \"price\": 123.45, \"listPrice\": 150.00, \"currency\": \"USD\"}. " +
        "If there is no price on the page, reply {\"price\": null}."

/**
 * Reads the model's JSON. The price must appear on the page (as "129.99", "1,299.99" or "$129"), so
 * a made-up number is dropped.
 */
fun parseProductReply(reply: String, page: PageText): ProductInfo? {
    val json = Regex("""\{[^{}]*\}""").find(reply)?.value ?: return null
    val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val price = o.optDouble("price", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return null
    if (!priceOnPage(price, page.text)) return null
    val title = o.optString("title").trim().ifBlank { page.title }.take(120)
    val currency = o.optString("currency").trim().uppercase(Locale.US).take(4).ifBlank { "USD" }
    // The usual price must also be on the page and above the current one to count.
    // Stores label it plainly ("Typical price: $154.99", "List Price: $199.99", "Was $89"), so the page is read first.
    val labeled = Regex("""(?:typical\s+price|list\s+price|was|reg(?:ular)?\.?\s+price|msrp)\s*:?\s*[$€£₹]\s?([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        .findAll(page.text).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.firstOrNull { it > price }
    val listPrice = labeled ?: o.optDouble("listPrice", Double.NaN).takeIf { !it.isNaN() && it > price && priceOnPage(it, page.text) }
    return ProductInfo(title, price, currency, page.url, storeName(page.url), listPrice)
}

/** Whether [price] is written somewhere in [text]. */
fun priceOnPage(price: Double, text: String): Boolean {
    val plain = text.replace(",", "")
    val cents = String.format(Locale.US, "%.2f", price)
    val whole = if (price % 1.0 == 0.0) price.toLong().toString() else null
    fun written(number: String) = Regex("""(?<![\d.])${Regex.escape(number)}(?!\d)""").containsMatchIn(plain)
    return written(cents) || (whole != null && written(whole))
}

private fun storeName(url: String): String =
    hostOf(url).removePrefix("www.").removePrefix("m.").substringBefore('.').replaceFirstChar { it.uppercase() }

/** One history per product page, shared by chats and tasks: the URL without its query string. */
private fun productKey(url: String): String = url.substringBefore('?').substringBefore('#').trimEnd('/').lowercase(Locale.US)

private fun historyFile(context: Context, key: String): File =
    File(context.filesDir, "price_history/${Integer.toHexString(key.hashCode())}.json")

/** Saves today's price (one point per 10 minutes at most) and returns the whole history, oldest first. */
fun recordPrice(context: Context, product: ProductInfo, now: Long = System.currentTimeMillis()): List<PricePoint> =
    recordValue(context, productKey(product.url), product.price, now)

/** Saves [value] under [key] (a product page, or "stock:NVDA") and returns the history, oldest first. */
fun recordValue(context: Context, key: String, value: Double, now: Long = System.currentTimeMillis()): List<PricePoint> {
    val file = historyFile(context, key)
    val history = loadPriceHistory(file).toMutableList()
    val last = history.lastOrNull()
    if (last != null && now - last.at < 600_000L) history[history.lastIndex] = PricePoint(now, value) else history += PricePoint(now, value)
    val kept = history.takeLast(120)
    file.parentFile?.mkdirs()
    file.writeText(JSONArray().apply { kept.forEach { put(JSONObject().put("at", it.at).put("price", it.price)) } }.toString())
    return kept
}

private fun loadPriceHistory(file: File): List<PricePoint> = runCatching {
    val array = JSONArray(file.readText())
    (0 until array.length()).map { i -> array.getJSONObject(i).let { PricePoint(it.getLong("at"), it.getDouble("price")) } }
}.getOrDefault(emptyList())

fun currencySymbol(code: String): String = when (code.uppercase(Locale.US)) {
    "USD", "CAD", "AUD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "INR" -> "₹"
    "JPY" -> "¥"
    else -> "$code "
}

/** The ```card and ```chart blocks added under the answer. */
fun priceBlocks(product: ProductInfo, history: List<PricePoint>, zone: ZoneId = ZoneId.systemDefault()): String {
    val previous = history.dropLast(1).lastOrNull()?.price
    val card = JSONObject()
        .put("type", "product")
        .put("title", product.title)
        .put("price", product.price)
        .put("currency", product.currency)
        .put("store", product.store)
        .put("url", product.url)
        .apply { if (previous != null && previous != product.price) put("was", previous) }
        .apply { product.listPrice?.let { put("listPrice", it) } }
        .apply { if (history.size >= 2) put("low", history.minOf { it.price }).put("high", history.maxOf { it.price }) }
    val out = StringBuilder("\n\n```card\n").append(card.toString()).append("\n```")
    if (history.size >= 2) {
        val points = JSONArray()
        history.forEach { p -> points.put(JSONObject().put("label", pointLabel(p.at, history, zone)).put("value", p.price)) }
        // Only the prices Mochu has checked itself; a daily task adds one each day.
        val chart = JSONObject().put("type", "line").put("title", "Your price checks").put("prefix", currencySymbol(product.currency)).put("points", points)
        out.append("\n\n```chart\n").append(chart.toString()).append("\n```")
    }
    return out.toString()
}

/**
 * Asks [engine] for the product and price on the first readable page, records it, and returns the
 * card (and chart) blocks, or "" when no price could be confirmed.
 */
suspend fun priceBlocksFor(context: Context, engine: BuilderEngine, request: String, pages: List<PageText>): String {
    if (!wantsPrice(request)) return ""
    for (page in pages.take(2)) {
        val output = StringBuilder()
        runCatching {
            engine.generate("price-extract", productExtractionPrompt(page, request), EXTRACT_SYSTEM).collect { output.append(it) }
        }
        engine.resetSession()
        engine.takeGenerationStats()
        val product = parseProductReply(splitThinking(output.toString()).answer, page) ?: continue
        return priceBlocks(product, recordPrice(context, product))
    }
    return ""
}

/** A chart label: the date, or the time when every point is from the same day. */
fun pointLabel(at: Long, history: List<PricePoint>, zone: ZoneId = ZoneId.systemDefault()): String {
    val days = history.map { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.distinct()
    val pattern = if (days.size <= 1) "h:mm a" else "MMM d"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(at).atZone(zone))
}
