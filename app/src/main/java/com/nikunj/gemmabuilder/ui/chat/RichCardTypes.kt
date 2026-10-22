package com.nikunj.gemmabuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

// The card types besides products (RichBlocks.kt): stocks, news, sports, comparisons, recipes,
// documents and places. Each returns false when its JSON is missing what it needs.

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun Caption(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

private fun JSONObject.text(key: String): String? = optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()

@Composable
private fun OpenButton(label: String, url: String?) {
    val uri = LocalUriHandler.current
    if (url != null && (url.startsWith("http") || url.startsWith("geo:"))) {
        OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) { Text(label) }
    }
}

private val upColor = Color(0xFF2E9E5B)

@Composable
internal fun StockCard(o: JSONObject): Boolean {
    val price = o.optDouble("price", Double.NaN).takeIf { !it.isNaN() } ?: return false
    val prefix = currencySymbol(o.optString("currency", "USD"))
    val change = o.optDouble("change", Double.NaN).takeIf { !it.isNaN() }
    val percent = o.optDouble("changePercent", Double.NaN).takeIf { !it.isNaN() }
    CardSurface {
        Caption(listOfNotNull(o.text("symbol"), o.text("name")).joinToString(" · "), MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(money(prefix, price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val delta = change ?: percent
            if (delta != null) {
                val up = delta >= 0
                val parts = listOfNotNull(
                    change?.let { (if (up) "+" else "-") + money(prefix, kotlin.math.abs(it)) },
                    percent?.let { String.format(Locale.US, "(%+.2f%%)", it) }
                )
                Text(
                    "${if (up) "▲" else "▼"} ${parts.joinToString(" ")} today",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (up) upColor else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        OpenButton("Open source page", o.text("url"))
    }
    return true
}

@Composable
internal fun NewsCard(o: JSONObject): Boolean {
    val items = o.optJSONArray("items") ?: return false
    if (items.length() == 0) return false
    val uri = LocalUriHandler.current
    CardSurface {
        Text(o.text("title") ?: "Headlines", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        (0 until items.length()).mapNotNull { items.optJSONObject(it) }.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { item.text("url")?.let { runCatching { uri.openUri(it) } } }
                    .padding(vertical = 6.dp)
            ) {
                Text(item.optString("title"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
                item.text("source")?.let { Caption(it) }
            }
        }
    }
    return true
}

@Composable
private fun TeamRow(name: String, score: Int?, winner: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (score != null) Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
internal fun SportsCard(o: JSONObject): Boolean {
    val home = o.text("home") ?: return false
    val away = o.text("away") ?: return false
    val hs = if (o.has("homeScore") && o.has("awayScore")) o.optInt("homeScore") else null
    val aw = if (hs != null) o.optInt("awayScore") else null
    CardSurface {
        listOfNotNull(o.text("league"), o.text("status"), o.text("date")).takeIf { it.isNotEmpty() }
            ?.let { Caption(it.joinToString(" · "), MaterialTheme.colorScheme.primary) }
        TeamRow(away, aw, aw != null && hs != null && aw > hs)
        TeamRow(home, hs, aw != null && hs != null && hs > aw)
        o.text("next")?.let { Caption("Next: $it") }
        OpenButton("Open source page", o.text("url"))
    }
    return true
}

@Composable
internal fun CompareCard(o: JSONObject): Boolean {
    val array = o.optJSONArray("items") ?: return false
    val items = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    if (items.size < 2) return false
    val specNames = items.flatMap { it.optJSONObject("specs")?.keys()?.asSequence()?.toList().orEmpty() }.distinct().take(8)
    val uri = LocalUriHandler.current
    CardSurface {
        Text("Comparison", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (specNames.isNotEmpty()) {
                Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Spacer(Modifier.height(64.dp))
                    specNames.forEach { Caption(it) }
                }
            }
            items.forEach { item ->
                Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.height(64.dp)) {
                        Text(item.optString("title"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        val price = item.optDouble("price", Double.NaN)
                        Text(
                            if (price.isNaN()) "Price not found" else money(currencySymbol(item.optString("currency", "USD")), price),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (price.isNaN()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    val specs = item.optJSONObject("specs")
                    specNames.forEach { name -> Text(specs?.text(name) ?: "—", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    item.text("url")?.let { url ->
                        Text(
                            item.text("store") ?: "Open",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { runCatching { uri.openUri(url) } }
                        )
                    }
                }
            }
        }
    }
    return true
}

@Composable
internal fun RecipeCard(o: JSONObject): Boolean {
    val ingredients = o.strings("ingredients")
    val steps = o.strings("steps")
    if (ingredients.isEmpty()) return false
    CardSurface {
        Text(o.text("title") ?: "Recipe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        listOfNotNull(o.text("time")?.let { "⏱ $it" }, o.text("servings")?.let { "🍽 Serves $it" })
            .takeIf { it.isNotEmpty() }?.let { Caption(it.joinToString("   ")) }
        Text("Ingredients", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        // Ticks are for cooking along; they aren't saved with the chat.
        ingredients.forEach { ingredient ->
            var checked by rememberSaveable(ingredient) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { checked = !checked }) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text(
                    ingredient,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (steps.isNotEmpty()) {
            Text("Steps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    return true
}

@Composable
internal fun DocumentCard(o: JSONObject): Boolean {
    val points = o.strings("points")
    if (points.isEmpty()) return false
    val ask = LocalAskAction.current
    CardSurface {
        val pages = o.optInt("pages", 0).takeIf { it > 0 }?.let { "$it ${if (it == 1) "page" else "pages"}" }
        Caption(listOfNotNull("📄 " + (o.text("file") ?: "Document"), pages).joinToString(" · "), MaterialTheme.colorScheme.primary)
        Text(o.text("title") ?: "Document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Key points", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        points.forEach { point ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                Text(point, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val asks = o.strings("asks")
        if (asks.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                asks.forEach { q -> SuggestionChip(onClick = { ask(q) }, label = { Text(q) }) }
            }
        }
    }
    return true
}

@Composable
internal fun PlaceCard(o: JSONObject): Boolean {
    val name = o.text("name") ?: return false
    CardSurface {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        o.optDouble("rating", Double.NaN).takeIf { !it.isNaN() }?.let { rating ->
            val reviews = o.optLong("reviews", 0).takeIf { it > 0 }?.let { " (${String.format(Locale.US, "%,d", it)} reviews)" }.orEmpty()
            Text("★ ${String.format(Locale.US, "%.1f", rating)}$reviews", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (o.has("openNow")) {
            val open = o.optBoolean("openNow")
            Text(
                if (open) "Open now" else "Closed now",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (open) upColor else MaterialTheme.colorScheme.error
            )
        }
        o.text("hours")?.let { Caption("🕒 $it") }
        o.text("address")?.let { Caption("📍 $it") }
        val query = listOfNotNull(name, o.text("address")).joinToString(" ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Straight to the Maps app: the in-app browser can't follow Google Maps' app-only redirects.
            val context = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(onClick = {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val maps = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=$encoded"))
                val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"))
                runCatching { context.startActivity(maps) }.onFailure { runCatching { context.startActivity(web) } }
            }) { Text("Open in Maps") }
            OpenButton("Source", o.text("url"))
        }
    }
    return true
}
