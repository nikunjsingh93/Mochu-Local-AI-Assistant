package com.nikunj.gemmabuilder

import android.content.Context
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Cards under answers: stocks and crypto, news headlines, sports scores, product comparisons,
// recipes, document summaries and places (prices are in PriceTracking.kt). Each is a ```card block
// with a "type". The model only turns text it was given (pages, a document, its own answer) into
// JSON; numbers it reports must appear in that text, so the card never shows a made-up value.

enum class CardKind { Compare, Price, Stock, Sports, Place, News, Recipe, Document }

private val stockWords = Regex("""\b(stocks?|shares?|share\s+price|ticker|market\s+cap|crypto(?:currency)?|bitcoin|btc|ethereum|eth|dogecoin|doge|solana|xrp)\b""", RegexOption.IGNORE_CASE)
// "check NVDA", "price of $AAPL": an all-caps ticker after a checking verb (case-sensitive on purpose).
private val tickerAfterVerb = Regex("""\b(?:[Cc]heck|[Pp]rice\s+of|[Hh]ow\s+is|[Hh]ow's|[Qq]uote\s+for|[Tt]rack)\s+\$?([A-Z]{1,5}(?:\.[A-Z])?)\b""")
private val notTickers = setOf("AI", "US", "UK", "PDF", "API", "THE", "TV", "PC", "IT", "OK", "NEWS", "I", "A")
private val sportsWords = Regex("""\b(score|scores|won|win|lose|lost|beat|game|match|fixture|playoffs?|standings|nhl|nba|nfl|mlb|mls|premier\s+league|ipl|cricket)\b""", RegexOption.IGNORE_CASE)
private val placeWords = Regex("""\b(open\s+now|is\s+.{2,40}\s+open|opening\s+hours|store\s+hours|hours\s+of|closing\s+time|close\s+today|near\s+me|nearby)\b""", RegexOption.IGNORE_CASE)
private val newsWords = Regex("""\b(news|headlines|latest\s+on|what's\s+happening)\b""", RegexOption.IGNORE_CASE)
private val recipeWords = Regex("""\b(recipe|recipes|how\s+(?:do\s+i|to|can\s+i)\s+(?:make|cook|bake|prepare))\b""", RegexOption.IGNORE_CASE)
private val compareWords = Regex("""\b(compare|comparison|vs\.?|versus|which\s+(?:one\s+)?is\s+better|difference\s+between)\b""", RegexOption.IGNORE_CASE)

fun tickerIn(request: String): String? = tickerAfterVerb.find(request)?.groupValues?.get(1)?.takeUnless { it in notTickers }

/** Which card fits [request], if any. [hasPages]: web pages were read; [hasDocs]: documents were just attached. */
fun cardKindFor(request: String, hasPages: Boolean, hasDocs: Boolean): CardKind? = when {
    hasDocs -> CardKind.Document
    hasPages && linksIn(request, 4).size >= 2 && compareWords.containsMatchIn(request) -> CardKind.Compare
    hasPages && wantsPrice(request) -> CardKind.Price
    hasPages && (stockWords.containsMatchIn(request) || tickerIn(request) != null) -> CardKind.Stock
    hasPages && sportsWords.containsMatchIn(request) -> CardKind.Sports
    hasPages && placeWords.containsMatchIn(request) -> CardKind.Place
    hasPages && newsWords.containsMatchIn(request) -> CardKind.News
    recipeWords.containsMatchIn(request) -> CardKind.Recipe
    else -> null
}

/** The first balanced {...} in [text] (models often add words around their JSON). */
fun firstJsonObject(text: String): JSONObject? {
    var start = text.indexOf('{')
    while (start >= 0) {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) {
                    runCatching { JSONObject(text.substring(start, i + 1)) }.getOrNull()?.let { return it }
                    break
                }
            }
        }
        start = text.indexOf('{', start + 1)
    }
    return null
}

private fun number(o: JSONObject, key: String): Double? = when (val v = o.opt(key)) {
    is Number -> v.toDouble()
    is String -> v.replace(",", "").replace("%", "").replace("$", "").trim().toDoubleOrNull()
    else -> null
}?.takeIf { !it.isNaN() }

/** Whether [value] is written in [text], as "12.5", "12.50" or "12". */
fun numberOnPage(value: Double, text: String): Boolean {
    val plain = text.replace(",", "")
    val forms = buildSet {
        add(String.format(Locale.US, "%.2f", value))
        add(String.format(Locale.US, "%.1f", value))
        if (value % 1.0 == 0.0) add(value.toLong().toString())
    }
    return forms.any { Regex("""(?<![\d.])${Regex.escape(it)}(?!\d)""").containsMatchIn(plain) }
}

/** Example text from the prompts; a model sometimes copies it back instead of filling it in. */
private val exampleValues = setOf("team a", "team b", "store name", "street, city", "short name", "dish name", "document title", "next game", "first point", "second point", "third point")

/** A real value for [key]: not blank, not "null", and not copied from the prompt's example. */
private fun JSONObject.real(key: String): String? = optString(key).trim().takeIf { v ->
    v.isNotBlank() && !v.equals("null", true) && v.lowercase(Locale.US) !in exampleValues && !v.contains(" or null", true)
}

/** Text without spaces or case, for loose "does this phrase appear" checks. */
private fun squash(text: String) = text.lowercase(Locale.US).replace(Regex("""[\s\-_]+"""), "")

// ---- Prompts ----

private fun pageBlock(page: PageText, chars: Int) = "Page: ${page.title}\n${page.text.take(chars)}"

private fun stockPrompt(request: String, page: PageText) =
    "The user asked: \"${request.take(200)}\"\n\n${pageBlock(page, 3500)}\n\n" +
        "Give the stock or coin's current price from this page. Reply with only this JSON: " +
        "{\"symbol\": \"NVDA\", \"name\": \"NVIDIA\", \"price\": 123.45, \"change\": -1.23, \"changePercent\": -0.98, \"currency\": \"USD\"}. " +
        "Use null for anything not on the page."

private fun sportsPrompt(request: String, page: PageText) =
    "The user asked: \"${request.take(200)}\"\n\n${pageBlock(page, 3500)}\n\n" +
        "Give the most recent game the user asked about. Reply with only this JSON: " +
        "{\"league\": \"NHL\", \"home\": \"Team A\", \"away\": \"Team B\", \"homeScore\": 3, \"awayScore\": 2, \"status\": \"Final\", \"date\": \"Oct 21\", \"next\": \"Thu Oct 23 vs Bruins\"}. " +
        "Use null for anything not on the page."

private fun placePrompt(request: String, page: PageText) =
    "The user asked: \"${request.take(200)}\"\n\n${pageBlock(page, 3500)}\n\n" +
        // The model can't know the time, so "open now" is worked out from the hours and this.
        "It is now ${DateTimeFormatter.ofPattern("EEEE h:mm a", Locale.US).format(java.time.ZonedDateTime.now())}. " +
        "Give the place the user asked about. Reply with only this JSON: " +
        "{\"name\": \"Store name\", \"rating\": 4.5, \"reviews\": 1200, \"openNow\": true, \"hours\": \"Today 9 AM - 9 PM\", \"address\": \"street, city\"}. " +
        "Use null for anything not on the page."

private fun comparePrompt(page: PageText) =
    "${pageBlock(page, 3500)}\n\nGive this product's name, price and up to 5 key specs from the page. Reply with only this JSON: " +
        "{\"title\": \"short name\", \"price\": 123.45, \"currency\": \"USD\", \"specs\": {\"Screen\": \"16 in OLED\", \"RAM\": \"16 GB\"}}. " +
        "Use null for the price if it is not on the page."

private fun recipePrompt(answer: String) =
    "Recipe:\n${answer.take(5000)}\n\nTurn this recipe into JSON. Reply with only this JSON: " +
        "{\"title\": \"dish name\", \"time\": \"30 min\", \"servings\": \"4\", \"ingredients\": [\"2 eggs\"], \"steps\": [\"Whisk the eggs.\"]}. " +
        "Use only what the recipe says; null for anything it doesn't."

private fun documentPrompt(text: String) =
    "Document:\n${text.take(6000)}\n\nGive the document's title and its 3 most important points. Reply with only this JSON: " +
        "{\"title\": \"document title\", \"points\": [\"first point\", \"second point\", \"third point\"]}."

// ---- Building cards ----

private fun block(o: JSONObject) = "\n\n```card\n$o\n```"

private fun chartBlock(title: String, prefix: String, history: List<PricePoint>, zone: ZoneId = ZoneId.systemDefault()): String {
    if (history.size < 2) return ""
    val points = JSONArray()
    history.forEach { points.put(JSONObject().put("label", pointLabel(it.at, history, zone)).put("value", it.price)) }
    return "\n\n```chart\n" + JSONObject().put("type", "line").put("title", title).put("prefix", prefix).put("points", points) + "\n```"
}

private fun stockCard(context: Context, o: JSONObject, page: PageText): String? {
    val price = number(o, "price")?.takeIf { it > 0 && numberOnPage(it, page.text) } ?: return null
    val symbol = o.optString("symbol").trim().uppercase(Locale.US).take(12).takeIf { it.isNotBlank() && it != "NULL" } ?: return null
    val currency = o.optString("currency").trim().uppercase(Locale.US).take(4).ifBlank { "USD" }.takeUnless { it == "NULL" } ?: "USD"
    val change = number(o, "change")?.takeIf { numberOnPage(kotlin.math.abs(it), page.text) }
    val percent = number(o, "changePercent")?.takeIf { numberOnPage(kotlin.math.abs(it), page.text) }
    val history = recordValue(context, "stock:$symbol", price)
    val card = JSONObject().put("type", "stock").put("symbol", symbol).put("name", o.optString("name").takeUnless { it == "null" }.orEmpty().take(60))
        .put("price", price).put("currency", currency).put("url", page.url)
        .apply { change?.let { put("change", it) }; percent?.let { put("changePercent", it) } }
    return block(card) + chartBlock("$symbol price", currencySymbol(currency), history)
}

private fun sportsCard(o: JSONObject, page: PageText): String? {
    val home = o.real("home")?.takeIf { squash(page.text).contains(squash(it).take(6)) } ?: return null
    val away = o.real("away")?.takeIf { squash(page.text).contains(squash(it).take(6)) } ?: return null
    val hs = number(o, "homeScore")
    val aw = number(o, "awayScore")
    // Scores are small numbers that appear all over a page, so they're kept only together and only if both are present.
    val scored = hs != null && aw != null && numberOnPage(hs, page.text) && numberOnPage(aw, page.text)
    val card = JSONObject().put("type", "sports").put("home", home.take(40)).put("away", away.take(40)).put("url", page.url)
    if (scored) card.put("homeScore", hs!!.toInt()).put("awayScore", aw!!.toInt())
    listOf("league", "status", "date", "next").forEach { key -> o.real(key)?.let { card.put(key, it.take(80)) } }
    return block(card)
}

private fun placeCard(o: JSONObject, page: PageText): String? {
    val name = o.real("name") ?: return null
    val card = JSONObject().put("type", "place").put("name", name.take(60)).put("url", page.url)
    number(o, "rating")?.takeIf { it in 0.0..5.0 && numberOnPage(it, page.text) }?.let { card.put("rating", it) }
    number(o, "reviews")?.takeIf { it > 0 && numberOnPage(it, page.text) }?.let { card.put("reviews", it.toLong()) }
    if (o.opt("openNow") is Boolean) card.put("openNow", o.getBoolean("openNow"))
    listOf("hours", "address").forEach { key -> o.real(key)?.let { card.put(key, it.take(100)) } }
    return block(card)
}

private fun compareItem(o: JSONObject, page: PageText): JSONObject? {
    val title = o.optString("title").trim().ifBlank { page.title }.take(70)
    val item = JSONObject().put("title", title).put("url", page.url).put("store", hostOf(page.url).removePrefix("www."))
    number(o, "price")?.takeIf { it > 0 && priceOnPage(it, page.text) }?.let {
        item.put("price", it).put("currency", o.optString("currency").uppercase(Locale.US).take(4).ifBlank { "USD" })
    }
    val specs = JSONObject()
    o.optJSONObject("specs")?.let { s ->
        s.keys().asSequence().take(6).forEach { key ->
            val value = s.optString(key).trim()
            // A spec is kept only when its value is written on the page.
            if (value.isNotBlank() && value != "null" && squash(page.text).contains(squash(value))) specs.put(key.take(24), value.take(40))
        }
    }
    item.put("specs", specs)
    return item
}

private fun newsCard(pages: List<PageText>, found: List<SearchResult>): String? {
    val items = JSONArray()
    val seen = mutableSetOf<String>()
    (pages.map { SearchResult(it.title, it.url, "") } + found).forEach { r ->
        val title = r.title.trim()
        if (items.length() >= 6 || title.length < 12 || !seen.add(r.url)) return@forEach
        items.put(JSONObject().put("title", title.take(140)).put("source", hostOf(r.url).removePrefix("www.")).put("url", r.url))
    }
    if (items.length() < 2) return null
    return block(JSONObject().put("type", "news").put("title", "Headlines").put("items", items))
}

private fun recipeCard(o: JSONObject, answer: String): String? {
    val words = squash(answer)
    fun list(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotBlank() && it != "null" } }.orEmpty()
    // Ingredients must come from the answer; a few word changes are fine, so only the first word is checked.
    val ingredients = list("ingredients").filter { ing -> ing.split(" ").firstOrNull { it.length > 2 && !it.first().isDigit() }?.let { words.contains(squash(it)) } ?: true }.take(20)
    val steps = list("steps").take(12)
    if (ingredients.size < 2 || steps.isEmpty()) return null
    val card = JSONObject().put("type", "recipe").put("title", o.optString("title").takeUnless { it.isBlank() || it == "null" } ?: "Recipe")
        .put("ingredients", JSONArray(ingredients)).put("steps", JSONArray(steps))
    listOf("time", "servings").forEach { key -> o.real(key)?.let { card.put(key, it.take(30)) } }
    return block(card)
}

/** Pages in a PDF, or null for other files. */
fun pdfPageCount(path: String): Int? = if (!path.lowercase(Locale.US).endsWith(".pdf")) null else runCatching {
    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> android.graphics.pdf.PdfRenderer(fd).use { it.pageCount } }
}.getOrNull()

private fun documentCard(o: JSONObject, path: String): String? {
    val points = o.optJSONArray("points")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.length > 3 && it != "null" } }.orEmpty().take(3)
    if (points.isEmpty()) return null
    val card = JSONObject().put("type", "document")
        .put("title", o.optString("title").trim().takeUnless { it.isBlank() || it == "null" }?.take(80) ?: chatDocumentName(path))
        .put("file", chatDocumentName(path))
        .put("points", JSONArray(points))
        .put("asks", JSONArray(listOf("Explain the key points in more detail", "List any dates and deadlines", "What should I do next?")))
    pdfPageCount(path)?.let { card.put("pages", it) }
    return block(card)
}

// ---- Running it ----

private const val CARD_SYSTEM = "You turn text into JSON for an app. Reply with only one JSON object and nothing else."

private suspend fun askJson(engine: BuilderEngine, prompt: String): JSONObject? {
    val output = StringBuilder()
    runCatching { engine.generate("rich-card", prompt, CARD_SYSTEM).collect { output.append(it) } }
    engine.resetSession()
    engine.takeGenerationStats()
    return firstJsonObject(splitThinking(output.toString()).answer)
}

/**
 * The card (and chart) blocks for an answer, or "" when none fits or nothing could be confirmed.
 * [onModelUsed] runs before the model is asked, since that resets the chat's open session.
 */
suspend fun richBlocksFor(
    context: Context,
    engine: BuilderEngine,
    request: String,
    pages: List<PageText>,
    found: List<SearchResult>,
    answer: String,
    documents: List<String>,
    onModelUsed: () -> Unit = {}
): String {
    val kind = cardKindFor(request, pages.isNotEmpty(), documents.isNotEmpty()) ?: return ""
    if (kind == CardKind.News) return newsCard(pages, found).orEmpty()
    onModelUsed()
    return when (kind) {
        CardKind.Price -> priceBlocksFor(context, engine, request, pages)
        CardKind.Stock -> pages.take(2).firstNotNullOfOrNull { page -> askJson(engine, stockPrompt(request, page))?.let { stockCard(context, it, page) } }
        CardKind.Sports -> pages.take(2).firstNotNullOfOrNull { page -> askJson(engine, sportsPrompt(request, page))?.let { sportsCard(it, page) } }
        CardKind.Place -> pages.take(2).firstNotNullOfOrNull { page -> askJson(engine, placePrompt(request, page))?.let { placeCard(it, page) } }
        CardKind.Compare -> {
            val items = pages.take(3).mapNotNull { page -> askJson(engine, comparePrompt(page))?.let { compareItem(it, page) } }
            if (items.size >= 2) block(JSONObject().put("type", "compare").put("items", JSONArray(items))) else null
        }
        CardKind.Recipe -> askJson(engine, recipePrompt(answer))?.let { recipeCard(it, answer) }
        CardKind.Document -> {
            val path = documents.first()
            askJson(engine, documentPrompt(chatDocumentText(path)))?.let { documentCard(it, path) }
        }
        CardKind.News -> null
    }.orEmpty()
}
