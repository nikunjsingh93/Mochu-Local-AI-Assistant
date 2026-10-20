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


// Project files: safe paths, PWA export, and text extraction from imported PDFs, images, DOCX, and audio.

private val audioTranscriptCache = mutableMapOf<String, Pair<Long, String>>()

fun safeResolve(root: File, relativePath: String): File {
    val clean = relativePath
        .replace("\\", "/")
        .removePrefix("/")
        .split("/")
        .filter { it.isNotBlank() && it != ".." && it != "." }
        .joinToString("/")
    require(clean.isNotBlank()) { "File path cannot be empty." }

    val out = File(root, clean)
    val rootPath = root.canonicalPath
    val outPath = out.canonicalPath
    require(outPath.startsWith(rootPath)) { "Invalid path outside project root." }
    return out
}

fun ensurePwaFiles(root: File) {
    val manifest = File(root, "manifest.webmanifest")
    if (!manifest.exists()) {
        manifest.writeText(defaultPwaManifest())
    }

    val serviceWorker = File(root, "service-worker.js")
    if (!serviceWorker.exists()) {
        serviceWorker.writeText(defaultServiceWorker())
    }

    val index = File(root, "index.html")
    if (index.exists()) {
        var html = index.readText()
        if (!html.contains("manifest.webmanifest", ignoreCase = true)) {
            html = html.replaceFirst(
                Regex("(?i)</head>"),
                "  <link rel=\"manifest\" href=\"manifest.webmanifest\" />\n</head>"
            )
        }
        if (!html.contains("serviceWorker.register", ignoreCase = true)) {
            val registration = """
<script>
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('service-worker.js'));
}
</script>
""".trimIndent()
            html = html.replaceFirst(Regex("(?i)</body>"), "$registration\n</body>")
        }
        index.writeText(html)
    }
}

suspend fun buildProjectContextForModel(
    context: Context,
    root: File,
    preferredPaths: List<String> = emptyList(),
    onlyPreferredWhenProvided: Boolean = false,
    maxTotalChars: Int = 24000
): String {
    val files = root.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(root).path.replace('\\', '/') to it }
        .filter { (path, _) -> !path.equals("index.html", ignoreCase = true) }
        .toList()

    if (files.isEmpty()) return "(none)"

    val perFileLimit = 5000
    val out = StringBuilder()

    fun appendFile(path: String, content: String) {
        if (content.isBlank()) return
        if (out.length >= maxTotalChars) return
        val remaining = maxTotalChars - out.length
        val clipped = content.take(minOf(perFileLimit, remaining))
        out.appendLine("<<<FILE:$path>>>")
        out.appendLine(clipped)
        out.appendLine("<<<END_FILE>>>")
    }

    val preferredNormalized = preferredPaths
        .map { it.replace('\\', '/').trim().trimStart('/') }
        .filter { it.isNotBlank() }
    val preferredSet = preferredNormalized.toSet()
    val preferredFiles = preferredNormalized.mapNotNull { wanted ->
        files.firstOrNull { (path, _) -> path.equals(wanted, ignoreCase = true) }
    }
    val remainingFiles = files
        .filterNot { (path, _) -> preferredSet.any { it.equals(path, ignoreCase = true) } }
        .sortedBy { it.first }
    val orderedFiles = if (onlyPreferredWhenProvided && preferredFiles.isNotEmpty()) {
        preferredFiles
    } else {
        preferredFiles + remainingFiles
    }

    if (preferredFiles.isNotEmpty()) {
        appendFile(
            "_meta/current_attachments.txt",
            "Prioritize these current user attachments first:\n" + preferredFiles.joinToString("\n") { "- ${it.first}" }
        )
    }

    orderedFiles.forEach { (path, file) ->
        if (out.length >= maxTotalChars) return@forEach
        val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
        when {
            isTextExtension(ext) -> {
                val text = runCatching { file.readText() }.getOrDefault("")
                appendFile(path, text)
            }
            ext == "docx" -> {
                val text = extractTextFromDocx(file)
                appendFile(path, if (text.isNotBlank()) text else "(DOCX imported. Could not extract text.)")
            }
            ext == "pdf" -> {
                val text = extractTextFromPdf(file)
                appendFile(path, if (text.isNotBlank()) text else "(PDF imported. Could not extract text.)")
            }
            isImageExtension(ext) -> {
                val text = extractTextFromImageWithOcr(file)
                appendFile(path, if (text.isNotBlank()) text else "(Image imported. No readable text detected.)")
            }
            isAudioExtension(ext) -> {
                val text = extractTextFromAudio(context, file)
                appendFile(path, if (text.isNotBlank()) text else "(Audio imported. Could not transcribe on this device/format.)")
            }
            else -> {
                // Skip binary files without OCR/text extraction support.
            }
        }
    }

    return out.toString().ifBlank { "(none)" }
}

private fun isTextExtension(ext: String): Boolean = ext in setOf(
    "txt", "md", "json", "js", "mjs", "ts", "tsx", "jsx", "css", "scss", "sass", "py",
    "html", "htm", "xml", "csv", "tsv", "yaml", "yml", "toml", "ini", "log"
)

private fun isImageExtension(ext: String): Boolean = ext in setOf(
    "png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif"
)

fun isAudioExtension(ext: String): Boolean = ext in setOf(
    "mp3", "wav", "m4a", "aac", "ogg", "opus", "flac", "3gp", "amr"
)

private suspend fun extractTextFromAudio(context: Context, file: File): String {
    val cacheKey = file.absolutePath
    val lastModified = file.lastModified()
    audioTranscriptCache[cacheKey]?.let { (cachedMod, cachedText) ->
        if (cachedMod == lastModified && cachedText.isNotBlank()) return cachedText
    }

    val transcript = transcribeAudioWithSpeechRecognizer(context, file)
    val normalized = if (transcript.isBlank()) {
        ""
    } else {
        "Audio transcript (${file.name}):\n$transcript"
    }
    audioTranscriptCache[cacheKey] = lastModified to normalized
    return normalized
}

private suspend fun transcribeAudioWithSpeechRecognizer(context: Context, file: File): String {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        return ""
    }
    val ext = file.extension.lowercase(Locale.US)
    val strategies = mutableListOf<suspend () -> String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        strategies += strategy@{
            val uri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull() ?: return@strategy ""
            recognizeAudioIntent(
                context = context,
                intentBuilder = {
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        @Suppress("DEPRECATION")
                        putExtra(RecognizerIntent.EXTRA_AUDIO_INJECT_SOURCE, uri)
                        data = uri
                    }
                },
                pfd = null
            )
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        strategies += strategy@{
            val pfd = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
                ?: return@strategy ""
            val wavSpec = if (ext == "wav") readWavSpec(file) else null
            recognizeAudioIntent(
                context = context,
                intentBuilder = {
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                        putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                        if (ext == "wav") {
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, wavSpec?.channels ?: 1)
                            putExtra(
                                RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                                when (wavSpec?.bitsPerSample) {
                                    8 -> AudioFormat.ENCODING_PCM_8BIT
                                    24, 32 -> AudioFormat.ENCODING_PCM_FLOAT
                                    else -> AudioFormat.ENCODING_PCM_16BIT
                                }
                            )
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, wavSpec?.sampleRate ?: 16000)
                        }
                    }
                },
                pfd = pfd
            )
        }
    }

    for (strategy in strategies) {
        val result = runCatching { strategy() }.getOrDefault("").trim()
        if (result.isNotBlank()) return result
    }
    return ""
}

private data class WavSpec(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int
)

private fun readWavSpec(file: File): WavSpec? = runCatching {
    val bytes = file.inputStream().use { it.readNBytes(64) }
    if (bytes.size < 44) return@runCatching null
    val header = String(bytes, 0, 4)
    val wave = String(bytes, 8, 4)
    if (header != "RIFF" || wave != "WAVE") return@runCatching null

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    // Standard PCM fields for most WAV files.
    val channels = buf.getShort(22).toInt().coerceAtLeast(1)
    val sampleRate = buf.getInt(24).coerceAtLeast(8000)
    val bitsPerSample = buf.getShort(34).toInt().coerceAtLeast(8)
    WavSpec(channels = channels, sampleRate = sampleRate, bitsPerSample = bitsPerSample)
}.getOrNull()

private suspend fun recognizeAudioIntent(
    context: Context,
    intentBuilder: () -> Intent,
    pfd: ParcelFileDescriptor?
): String {
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val finish: (String) -> Unit = { text ->
                runCatching { pfd?.close() }
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
                if (cont.isActive) cont.resume(text.trim())
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onResults(results: Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    finish(matches.joinToString("\n"))
                }
                override fun onError(error: Int) = finish("")
            })

            cont.invokeOnCancellation {
                runCatching { pfd?.close() }
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }

            runCatching { recognizer.startListening(intentBuilder()) }
                .onFailure { finish("") }
        }
    }
}

private fun extractTextFromImageWithOcr(file: File): String {
    return runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val candidates = listOf(3200, 2400, 1600)
            .mapNotNull { maxSide -> decodeScaledBitmap(file.absolutePath, maxSide = maxSide) }

        var best = ""
        candidates.forEach { bitmap ->
            val direct = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text.trim()
            if (direct.length > best.length) best = direct

            // Fallback pass for screenshots where text contrast is low.
            val enhanced = enhanceBitmapForOcr(bitmap)
            val enhancedText = Tasks.await(recognizer.process(InputImage.fromBitmap(enhanced, 0))).text.trim()
            if (enhancedText.length > best.length) best = enhancedText
        }
        recognizer.close()
        if (best.isNotBlank()) {
            best
        } else {
            describeImageWithLabels(file)
        }
    }.getOrDefault("")
}

private fun enhanceBitmapForOcr(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    for (i in pixels.indices) {
        val c = pixels[i]
        val r = android.graphics.Color.red(c)
        val g = android.graphics.Color.green(c)
        val b = android.graphics.Color.blue(c)
        val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        val boosted = ((luma - 128) * 1.5f + 128).toInt().coerceIn(0, 255)
        val mono = if (boosted >= 140) 255 else 0
        pixels[i] = android.graphics.Color.argb(255, mono, mono, mono)
    }

    out.setPixels(pixels, 0, width, 0, 0, width, height)
    return out
}

private fun describeImageWithLabels(file: File): String {
    return runCatching {
        val bitmap = decodeScaledBitmap(file.absolutePath, maxSide = 1600) ?: return@runCatching ""
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.15f)
            .build()
        val labeler = ImageLabeling.getClient(options)
        val labels = Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0)))
        labeler.close()
        if (labels.isEmpty()) return@runCatching ""
        val ranked = labels.sortedByDescending { it.confidence }
        val summary = ranked
            .take(12)
            .joinToString(", ") { label ->
                "${label.text} (${(label.confidence * 100).toInt()}%)"
            }

        val lowered = ranked.map { it.text.lowercase(Locale.US) }
        val likelySubject = when {
            lowered.any { it.contains("pancake") } -> "Likely subject: pancake."
            lowered.any { it.contains("waffle") } -> "Likely subject: waffle."
            lowered.any { it.contains("crepe") } -> "Likely subject: crepe."
            lowered.any { it.contains("food") || it.contains("meal") || it.contains("cuisine") } &&
                lowered.any { it.contains("tableware") || it.contains("dishware") || it.contains("plate") } ->
                "Likely subject: a plated food item, possibly a pancake or similar flat breakfast dish."
            else -> "Likely subject: ${ranked.first().text}."
        }
        "No readable text detected. Visual analysis labels: $summary. $likelySubject"
    }.getOrDefault("")
}

private fun extractTextFromPdf(file: File): String {
    val native = extractTextFromPdfNative(file)
    if (native.isNotBlank()) return native
    return extractTextFromPdfWithOcr(file)
}

private fun extractTextFromPdfNative(file: File): String {
    return runCatching {
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                startPage = 1
                endPage = minOf(doc.numberOfPages, 25)
            }
            stripper.getText(doc).trim()
        }
    }.getOrDefault("")
}

private fun extractTextFromPdfWithOcr(file: File): String {
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val sb = StringBuilder()
                val maxPages = minOf(renderer.pageCount, 4)
                for (i in 0 until maxPages) {
                    renderer.openPage(i).use { page ->
                        val width = max(1, page.width * 2)
                        val height = max(1, page.height * 2)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val result = Tasks.await(recognizer.process(image))
                        val text = result.text.trim()
                        if (text.isNotBlank()) {
                            sb.appendLine("Page ${i + 1}:")
                            sb.appendLine(text)
                            sb.appendLine()
                        }
                    }
                }
                recognizer.close()
                sb.toString().trim()
            }
        }
    }.getOrDefault("")
}

private fun extractTextFromDocx(file: File): String {
    return runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use ""
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            xmlToPlainText(xml).trim()
        }
    }.getOrDefault("")
}

private fun xmlToPlainText(xml: String): String {
    val withBreaks = xml
        .replace(Regex("(?i)</w:p>"), "\n")
        .replace(Regex("(?i)<w:tab\\b[^>]*/>"), "\t")
        .replace(Regex("(?i)<w:br\\b[^>]*/>"), "\n")
    return withBreaks
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
}

private fun decodeScaledBitmap(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    var sample = 1
    while (srcW / sample > maxSide || srcH / sample > maxSide) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    return BitmapFactory.decodeFile(path, opts)
}

fun htmlContextForModel(html: String, maxChars: Int = 12000): String {
    if (html.isBlank()) return ""
    val compact = html
        .replace(Regex("(?is)<script[^>]+src=[\"'][^\"']+[\"'][^>]*></script>"), "")
        .replace(Regex("(?is)<link[^>]+rel=[\"']stylesheet[\"'][^>]*>"), "")
        .replace(Regex("(?is)<!--.*?-->"), "")
        .replace(Regex("[ \t]{2,}"), " ")
        .trim()

    if (compact.length <= maxChars) return compact

    // Keep the start (head, styles, layout) and the end (scripts) of the page.
    val head = compact.take(maxChars * 2 / 3)
    val tail = compact.takeLast(maxChars / 4)
    return """
$head

<!-- GEMMA BUILDER NOTE: The imported HTML was very large, so the middle was omitted before sending to the local model. Preserve the intent of the page and rewrite a complete, self-contained index.html. -->

$tail
    """.trimIndent()
}


