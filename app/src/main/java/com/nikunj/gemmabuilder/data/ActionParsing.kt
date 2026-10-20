package com.nikunj.gemmabuilder

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.graphics.pdf.PdfRenderer
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewAssetLoader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.coroutines.resume


// Parsing the model's XML actions (write_file / reply) and repairing incomplete output.

fun extractStreamingCode(text: String): String {
    val entries = mutableListOf<WriteFileAction>()
    val actionStartRegex = Regex("""<action\s+name=["']write_file["']>""", RegexOption.IGNORE_CASE)
    val actionMatches = actionStartRegex.findAll(text).toList()
    for (i in actionMatches.indices) {
        val start = actionMatches[i].range.first
        val endExclusive = if (i + 1 < actionMatches.size) actionMatches[i + 1].range.first else text.length
        val chunk = text.substring(start, endExclusive)
        val pathMatch = Regex("<path>(.*?)</path>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(chunk)
        val rawPath = pathMatch?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { "index.html" }
        val contentStart = chunk.indexOf("<content>", ignoreCase = true)
        if (contentStart < 0) continue
        val payloadStart = contentStart + "<content>".length
        val contentEnd = chunk.indexOf("</content>", startIndex = payloadStart, ignoreCase = true)
        val payload = if (contentEnd >= 0) chunk.substring(payloadStart, contentEnd) else chunk.substring(payloadStart)
        entries.add(WriteFileAction(path = rawPath, content = payload.trimEnd()))
    }

    if (entries.isNotEmpty()) {
        return entries.joinToString("\n\n") { action ->
            "=== ${action.path} ===\n${action.content.trimStart()}"
        }.ifBlank { text.trim() }
    }
    return text.trim()
}


fun parseWriteFileActions(text: String): List<WriteFileAction> {
    val regex = Regex(
        pattern = """<action\s+name=["']write_file["']>\s*<path>(.*?)</path>\s*<content>(.*?)</content>\s*</action>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    return regex.findAll(text).map { match ->
        WriteFileAction(
            path = match.groupValues[1].trim().ifBlank { "index.html" },
            content = cleanGeneratedFileContent(match.groupValues[2])
        )
    }.toList()
}

fun recoverWriteFileActions(text: String): List<WriteFileAction> {
    val completeActions = parseWriteFileActions(text)
    if (completeActions.isNotEmpty()) return completeActions

    recoverContentBlock(text)?.let { html ->
        return listOf(WriteFileAction("index.html", html))
    }

    recoverHtmlDocument(text)?.let { html ->
        return listOf(WriteFileAction("index.html", html))
    }

    recoverHtmlFence(text)?.let { html ->
        return listOf(WriteFileAction("index.html", html))
    }

    return emptyList()
}

fun recoverAssistantReply(text: String): String? {
    return recoverAssistantReplyContent(text)?.text
}

data class AssistantReplyContent(val text: String, val complete: Boolean)

fun recoverAssistantReplyContent(text: String): AssistantReplyContent? {
    val completeReply = Regex(
        pattern = """<action\s+name=["']reply["']>\s*<content>(.*?)</content>\s*</action>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    ).find(text)?.groupValues?.getOrNull(1)?.trim()
    if (!completeReply.isNullOrBlank()) {
        return AssistantReplyContent(cleanGeneratedFileContent(completeReply), complete = true)
    }

    val openReply = Regex(
        pattern = """<action\s+name=["']reply["']>\s*<content>(.*)""",
        options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    ).find(text)?.groupValues?.getOrNull(1)?.trim()
    if (!openReply.isNullOrBlank()) {
        val contentEnd = openReply.indexOf("</content>", ignoreCase = true)
        val partial = if (contentEnd >= 0) openReply.take(contentEnd) else openReply
        val cleaned = cleanGeneratedFileContent(partial)
        if (cleaned.isNotBlank()) return AssistantReplyContent(cleaned, complete = contentEnd >= 0)
    }

    val stripped = text
        .replace(Regex("""<action\s+name=["']write_file["'][\s\S]*?</action>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<action\s+name=["']reply["'][\s\S]*?</action>""", RegexOption.IGNORE_CASE), "")
        .trim()
    return stripped
        .takeIf { it.isNotBlank() && !it.startsWith("<action", ignoreCase = true) }
        ?.let { AssistantReplyContent(it, complete = true) }
}


fun recoverContentBlock(text: String): String? {
    val contentTag = Regex("<content>", setOf(RegexOption.IGNORE_CASE)).find(text) ?: return null
    val start = contentTag.range.last + 1
    val explicitEnd = text.indexOf("</content>", startIndex = start, ignoreCase = true)
    val end = when {
        explicitEnd >= 0 -> explicitEnd
        text.indexOf("</html>", startIndex = start, ignoreCase = true) >= 0 -> text.indexOf("</html>", startIndex = start, ignoreCase = true) + "</html>".length
        else -> text.length
    }
    val content = cleanGeneratedFileContent(text.substring(start, end))
    return content.takeIf { it.contains("<html", ignoreCase = true) || it.contains("<!doctype", ignoreCase = true) }
}

fun recoverHtmlDocument(text: String): String? {
    val doctype = text.indexOf("<!doctype", ignoreCase = true)
    val html = text.indexOf("<html", ignoreCase = true)
    val start = listOf(doctype, html).filter { it >= 0 }.minOrNull() ?: return null
    val htmlEnd = text.indexOf("</html>", startIndex = start, ignoreCase = true)
    val end = if (htmlEnd >= 0) htmlEnd + "</html>".length else text.length
    return cleanGeneratedFileContent(text.substring(start, end))
}

fun recoverHtmlFence(text: String): String? {
    val regex = Regex("""```(?:html)?\s*(.*?)```""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val match = regex.find(text) ?: return null
    val content = cleanGeneratedFileContent(match.groupValues[1])
    return content.takeIf { it.contains("<html", ignoreCase = true) || it.contains("<!doctype", ignoreCase = true) }
}

fun cleanGeneratedFileContent(raw: String): String {
    var content = raw.trim()
        .removePrefix("<![CDATA[")
        .removeSuffix("]]>")
        .trim()
    if (content.startsWith("```")) {
        content = content.lines()
            .drop(1)
            .dropLastWhile { it.trim() == "```" }
            .joinToString("\n")
            .trim()
    }
    return content
}

fun normalizeWriteActions(actions: List<WriteFileAction>): List<WriteFileAction> {
    if (actions.isEmpty()) return actions

    val normalized = actions.map { a ->
        var normalizedPath = a.path.trim().replace('\\', '/').trimStart('/').ifBlank { "index.html" }
        if (normalizedPath.equals("index.htm", ignoreCase = true)) {
            normalizedPath = "index.html"
        }
        a.copy(path = normalizedPath)
    }
    return consolidateSingleFileDefault(repairCssJsMixup(normalized))
}

private fun repairCssJsMixup(actions: List<WriteFileAction>): List<WriteFileAction> {
    val mutable = actions.toMutableList()
    val cssIndex = mutable.indexOfFirst { it.path.equals("styles.css", ignoreCase = true) }
    if (cssIndex < 0) return mutable

    val css = mutable[cssIndex]
    val lines = css.content.lines()
    val jsStart = lines.indexOfFirst { line ->
        val t = line.trim()
        t.startsWith("const ") ||
            t.startsWith("let ") ||
            t.startsWith("var ") ||
            t.startsWith("function ") ||
            t.contains("=>") ||
            t.contains("document.") ||
            t.contains("window.") ||
            t.contains("addEventListener(") ||
            t.contains("querySelector(") ||
            t.contains("querySelectorAll(")
    }
    if (jsStart <= 0) return mutable

    val cssPart = lines.take(jsStart).joinToString("\n").trimEnd()
    val jsPart = lines.drop(jsStart).joinToString("\n").trim()
    if (jsPart.isBlank()) return mutable

    mutable[cssIndex] = css.copy(content = cssPart)

    val jsIndex = mutable.indexOfFirst { it.path.equals("app.js", ignoreCase = true) }
    if (jsIndex >= 0) {
        val existing = mutable[jsIndex].content.trim()
        val merged = if (existing.isBlank()) jsPart else "$existing\n\n$jsPart"
        mutable[jsIndex] = mutable[jsIndex].copy(content = merged.trim())
    } else {
        mutable.add(WriteFileAction(path = "app.js", content = jsPart))
    }

    val htmlIndex = mutable.indexOfFirst { it.path.equals("index.html", ignoreCase = true) }
    if (htmlIndex >= 0) {
        val html = mutable[htmlIndex].content
        val needsScriptRef = html.contains("styles.css", ignoreCase = true) &&
            !html.contains("app.js", ignoreCase = true)
        if (needsScriptRef) {
            val patched = html.replaceFirst(
                Regex("(?i)</body>"),
                """  <script src="app.js"></script>
</body>"""
            )
            mutable[htmlIndex] = mutable[htmlIndex].copy(content = patched)
        }
    }

    return mutable
}

private fun consolidateSingleFileDefault(actions: List<WriteFileAction>): List<WriteFileAction> {
    val indexAction = actions.firstOrNull { it.path.equals("index.html", ignoreCase = true) } ?: return actions
    val cssAction = actions.firstOrNull { it.path.equals("styles.css", ignoreCase = true) }
    val jsAction = actions.firstOrNull { it.path.equals("app.js", ignoreCase = true) }
    if (cssAction == null && jsAction == null) return actions

    var html = indexAction.content
    val lower = html.lowercase(Locale.US)
    val explicitSplitRequested = lower.contains("split files") ||
        lower.contains("separate files") ||
        lower.contains("styles.css") && lower.contains("app.js") && lower.contains("requested")
    if (explicitSplitRequested) return actions

    cssAction?.let { css ->
        val cssText = css.content.trim()
        if (cssText.isNotBlank()) {
            val styleTag = "<style>\n$cssText\n</style>"
            html = if (Regex("(?is)<head[^>]*>").containsMatchIn(html)) {
                html.replaceFirst(Regex("(?is)</head>"), "$styleTag\n</head>")
            } else {
                "$styleTag\n$html"
            }
        }
        html = html.replace(Regex("(?is)<link[^>]+href=[\"']styles\\.css[\"'][^>]*>"), "")
    }

    jsAction?.let { js ->
        val jsText = js.content.trim()
        if (jsText.isNotBlank()) {
            val scriptTag = "<script>\n$jsText\n</script>"
            html = if (Regex("(?is)</body>").containsMatchIn(html)) {
                html.replaceFirst(Regex("(?is)</body>"), "$scriptTag\n</body>")
            } else {
                "$html\n$scriptTag"
            }
        }
        html = html.replace(Regex("(?is)<script[^>]+src=[\"']app\\.js[\"'][^>]*></script>"), "")
    }

    val out = mutableListOf(indexAction.copy(content = html.trim()))
    actions.forEach { action ->
        val isIndex = action.path.equals("index.html", ignoreCase = true)
        val isCss = action.path.equals("styles.css", ignoreCase = true)
        val isJs = action.path.equals("app.js", ignoreCase = true)
        if (!isIndex && !isCss && !isJs) out.add(action)
    }
    return out
}

fun isCompleteEnoughForWriting(action: WriteFileAction): Boolean {
    val path = action.path.lowercase(Locale.US)
    if (Regex("""\.(tsx|ts|jsx)$""").containsMatchIn(path)) {
        // A file cut off part-way leaves braces open.
        val text = action.content
        return text.isNotBlank() && text.count { it == '{' } <= text.count { it == '}' } && !text.trimEnd().endsWith("(")
    }
    if (!path.endsWith(".html")) return true
    val html = action.content.trim()
    val lower = html.lowercase(Locale.US)
    if (!(lower.contains("<html") || lower.contains("<!doctype"))) return false
    if (!lower.contains("</html>")) return false
    if (lower.contains("<script") && !lower.contains("</script>")) return false
    if (lower.contains("<style") && !lower.contains("</style>")) return false
    if (html.endsWith("<") || html.endsWith("{") || html.endsWith("(")) return false
    return true
}


