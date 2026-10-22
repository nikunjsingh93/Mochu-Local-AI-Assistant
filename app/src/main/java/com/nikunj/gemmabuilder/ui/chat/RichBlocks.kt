package com.nikunj.gemmabuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

// Rich blocks in replies: ```card (a product) and ```chart (a line of values). They return false
// when the JSON can't be read, so the caller shows the block as plain code instead.

internal fun money(prefix: String, value: Double): String =
    prefix + if (value % 1.0 == 0.0) String.format(Locale.US, "%,d", value.toLong()) else String.format(Locale.US, "%,.2f", value)

/** Sends a follow-up message from a card (the "Ask more" chips). */
val LocalAskAction = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun RichCardBlock(json: String): Boolean {
    val o = runCatching { JSONObject(json) }.getOrNull() ?: return false
    return when (o.optString("type", "product")) {
        "product" -> ProductCard(o)
        "stock" -> StockCard(o)
        "news" -> NewsCard(o)
        "sports" -> SportsCard(o)
        "compare" -> CompareCard(o)
        "recipe" -> RecipeCard(o)
        "document" -> DocumentCard(o)
        "place" -> PlaceCard(o)
        else -> false
    }
}

@Composable
private fun ProductCard(o: JSONObject): Boolean {
    val price = o.optDouble("price", Double.NaN).takeIf { !it.isNaN() } ?: return false
    val prefix = currencySymbol(o.optString("currency", "USD"))
    val was = o.optDouble("was", Double.NaN).takeIf { !it.isNaN() }
    val low = o.optDouble("low", Double.NaN).takeIf { !it.isNaN() }
    val high = o.optDouble("high", Double.NaN).takeIf { !it.isNaN() }
    val url = o.optString("url")
    val uri = LocalUriHandler.current
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            o.optString("store").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(o.optString("title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(money(prefix, price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (was != null) {
                    val down = price < was
                    Text(
                        "${if (down) "▼" else "▲"} ${money(prefix, kotlin.math.abs(price - was))} since last check",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (down) androidx.compose.ui.graphics.Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            val listPrice = o.optDouble("listPrice", Double.NaN).takeIf { !it.isNaN() && it > price }
            if (listPrice != null) {
                Text(
                    "Typical price ${money(prefix, listPrice)} · ${kotlin.math.round((1 - price / listPrice) * 100).toInt()}% off",
                    style = MaterialTheme.typography.labelLarge,
                    color = androidx.compose.ui.graphics.Color(0xFF2E9E5B)
                )
            }
            if (low != null && high != null) {
                Text(
                    "Lowest ${money(prefix, low)} · Highest ${money(prefix, high)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (url.startsWith("http")) {
                OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) { Text("Open product page") }
            }
        }
    }
    return true
}

@Composable
fun RichChartBlock(json: String): Boolean {
    val o = runCatching { JSONObject(json) }.getOrNull() ?: return false
    val array = o.optJSONArray("points") ?: return false
    val points = (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.let { p -> p.optDouble("value", Double.NaN).takeIf { !it.isNaN() }?.let { p.optString("label") to it } }
    }
    if (points.size < 2) return false
    val prefix = o.optString("prefix")
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.optString("title", "Chart"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${money(prefix, min)} – ${money(prefix, max)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val range = (max - min).takeIf { it > 0 } ?: 1.0
                val pad = 8.dp.toPx()
                fun at(i: Int, v: Double) = Offset(
                    pad + (size.width - 2 * pad) * i / (points.size - 1),
                    // An unchanged price is a flat line through the middle, not along the bottom.
                    pad + (size.height - 2 * pad) * (if (max > min) (1 - ((v - min) / range)).toFloat() else 0.5f)
                )
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = pad + (size.height - 2 * pad) * f
                    drawLine(grid, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.dp.toPx())
                }
                val path = Path()
                points.forEachIndexed { i, (_, v) -> at(i, v).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
                points.forEachIndexed { i, (_, v) -> drawCircle(line, radius = 4.dp.toPx(), center = at(i, v)) }
            }
            Row {
                Text(points.first().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(points.last().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    return true
}
