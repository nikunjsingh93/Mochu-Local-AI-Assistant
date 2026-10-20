package com.nikunj.gemmabuilder

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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


// Studio work area: live preview, code view, and fullscreen overlays.

@Composable
fun FullscreenPreview(state: BuilderUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(if (state.tab == 0) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        when (state.tab) {
            1 -> FullscreenCodePane(state)
            else -> PreviewPane(state)
        }
    }
}

@Composable
fun ImagePreviewOverlay(
    imagePath: String,
    onClose: () -> Unit
) {
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable { onClose() }
            .padding(16.dp)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.35f), MaterialTheme.shapes.small)
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Close image", tint = Color.White)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected image preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 42.dp)
            )
        } else {
            Text(
                text = "Could not open image preview.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun FullscreenCodePane(state: BuilderUiState) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = if (state.isBusy && state.streamingCode.isNotBlank()) "Streaming generated code..." else "Code",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        AutoScrollingCodeText(
            text = codeTextForDisplay(state),
            fontScale = state.codeFontScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
fun AutoScrollingMonospaceText(text: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(text) {
        delay(40)
        scrollState.scrollTo(scrollState.maxValue)
    }
    SelectionContainer {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(12.dp)
        )
    }
}

@Composable
fun AutoScrollingCodeText(text: String, fontScale: Float = 1f, modifier: Modifier = Modifier) {
    val dark = ColorUtils.calculateLuminance(MaterialTheme.colorScheme.background.toArgb()) < 0.5
    val codeBg = if (dark) Color(0xFF1F2228) else Color(0xFFF7F7F8)
    val scrollState = rememberScrollState()
    LaunchedEffect(text) {
        delay(40)
        scrollState.scrollTo(scrollState.maxValue)
    }
    SelectionContainer {
        Text(
            text = syntaxHighlightCode(text, dark),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * fontScale),
            modifier = modifier
                .verticalScroll(scrollState)
                .background(codeBg, MaterialTheme.shapes.medium)
                .padding(12.dp)
        )
    }
}

fun syntaxHighlightCode(text: String, dark: Boolean): AnnotatedString {
    val keywordColor = if (dark) Color(0xFF4FC3F7) else Color(0xFF005A9C)
    val tagColor = if (dark) Color(0xFF81C784) else Color(0xFF1B5E20)
    val attrColor = if (dark) Color(0xFFFFB74D) else Color(0xFF8D4E00)
    val stringColor = if (dark) Color(0xFFE57373) else Color(0xFFB71C1C)
    val commentColor = if (dark) Color(0xFF9E9E9E) else Color(0xFF616161)
    val cssSelectorColor = if (dark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
    val cssPropertyColor = if (dark) Color(0xFF80CBC4) else Color(0xFF00695C)
    val numberColor = if (dark) Color(0xFFCE93D8) else Color(0xFF6A1B9A)

    val out = Builder(text)
    fun apply(regex: Regex, style: SpanStyle) {
        regex.findAll(text).forEach { m ->
            out.addStyle(style, m.range.first, m.range.last + 1)
        }
    }
    apply(Regex("""<!--[\s\S]*?-->"""), SpanStyle(color = commentColor))
    apply(Regex("""/\*[\s\S]*?\*/"""), SpanStyle(color = commentColor))
    apply(Regex("""(?m)//.*$"""), SpanStyle(color = commentColor))
    apply(Regex("""</?[A-Za-z][^>\s/]*"""), SpanStyle(color = tagColor))
    apply(Regex("""\s[A-Za-z_:][-A-Za-z0-9_:.]*(?=\=)"""), SpanStyle(color = attrColor))
    apply(Regex(""""([^"\\\\]|\\\\.)*"|'([^'\\\\]|\\\\.)*'"""), SpanStyle(color = stringColor))
    apply(Regex("""(?m)^[ \t]*([.#]?[A-Za-z][A-Za-z0-9_\-]*(?:\s*[>+~]\s*[.#]?[A-Za-z][A-Za-z0-9_\-]*)*)\s*\{"""), SpanStyle(color = cssSelectorColor, fontWeight = FontWeight.Medium))
    apply(Regex("""(?m)\b([a-zA-Z-]+)\s*:"""), SpanStyle(color = cssPropertyColor))
    apply(Regex("""#[0-9a-fA-F]{3,8}\b|\b\d+(\.\d+)?(px|em|rem|vh|vw|%|ms|s|deg)?\b"""), SpanStyle(color = numberColor))
    apply(Regex("""\b(function|const|let|var|return|if|else|for|while|class|new|import|export|from|async|await|true|false|null|undefined)\b"""), SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold))
    return out.toAnnotatedString()
}

fun codeTextForDisplay(state: BuilderUiState): String {
    return when {
        state.isBusy && state.streamingCode.isNotBlank() -> state.streamingCode
        state.currentCode.isNotBlank() -> state.currentCode
        else -> "No generated code yet."
    }
}


@Composable
fun WorkPanel(
    modifier: Modifier = Modifier,
    state: BuilderUiState,
    onTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleWorkPanelCollapsed: () -> Unit,
    onImportFiles: () -> Unit,
    onOpenFileInCode: (String) -> Unit,
    onOpenImagePreview: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onSaveFile: (String) -> Unit,
    onSaveZip: () -> Unit,
    onSavePwaZip: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val consoleErrors = PreviewConsole.entries.collectAsState().value.count { it.level == "error" }
                val tabs = listOf("Preview", "Code", "Files", if (consoleErrors > 0) "Console ($consoleErrors)" else "Console")
                // Compact pills sized to their labels, so four tabs and the buttons fit a phone.
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = state.tab == index && !state.workPanelCollapsed
                        Surface(
                            onClick = { onTab(index) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(title, maxLines = 1, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload preview")
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = "Full screen preview")
                }
                IconButton(onClick = onToggleWorkPanelCollapsed) {
                    Icon(
                        when {
                            isLandscape && state.workPanelCollapsed -> Icons.Rounded.ChevronLeft
                            isLandscape -> Icons.Rounded.ChevronRight
                            state.workPanelCollapsed -> Icons.Rounded.ExpandLess
                            else -> Icons.Rounded.ExpandMore
                        },
                        contentDescription = if (state.workPanelCollapsed) "Show panel" else "Hide panel"
                    )
                }
            }

            if (!state.workPanelCollapsed) {
                when (state.tab) {
                    0 -> PreviewPane(state)
                    1 -> CodePane(state)
                    3 -> ConsolePane()
                    else -> FilesPane(
                        state,
                        onImportFiles = onImportFiles,
                        onOpenFileInCode = onOpenFileInCode,
                        onOpenImagePreview = onOpenImagePreview,
                        onDeleteFile = onDeleteFile,
                        onSaveFile = onSaveFile,
                        onSaveZip = onSaveZip,
                        onSavePwaZip = onSavePwaZip
                    )
                }
            }
        }
    }
}

@Composable
fun PreviewPane(state: BuilderUiState) {
    val indexPath = state.indexHtmlPath
    key(state.previewVersion, indexPath) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            factory = { context ->
                WebView.setWebContentsDebuggingEnabled(true)

                val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
                val assetLoader = WebViewAssetLoader.Builder()
                    .setDomain("appassets.androidplatform.net")
                    .addPathHandler(
                        "/projects/",
                        WebViewAssetLoader.InternalStoragePathHandler(context, projectsDir)
                    )
                    .addPathHandler("/runtime/", RuntimeAssetsHandler(context))
                    .build()
                val projectsPrefix = "https://appassets.androidplatform.net/projects/"

                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowContentAccess = true

                    // Preview resources are loaded from WebViewAssetLoader through an HTTPS-like
                    // appassets origin. This avoids file:// quirks while still allowing linked CSS,
                    // JS, images, and other relative assets to load from the generated project folder.
                    settings.allowFileAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.defaultTextEncodingName = "UTF-8"
                    settings.textZoom = 100
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    // Keep the preview as a browser-like surface. The generated HTML should decide
                    // its own colors; the Compose app's dark/light theme must not be visible through it.
                    setBackgroundColor(android.graphics.Color.WHITE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setForceDarkAllowed(false)
                    }
                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = true
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        settings.isAlgorithmicDarkeningAllowed = false
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        @Suppress("DEPRECATION")
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url ?: return null
                            val asText = url.toString().substringBefore('?')
                            if (asText.startsWith(projectsPrefix)) {
                                val relative = Uri.decode(asText.removePrefix(projectsPrefix))
                                val file = File(projectsDir, relative)
                                val projectDir = File(projectsDir, relative.substringBefore('/'))
                                // React projects: index.html runs through the bundled React runner.
                                if (file.name.equals("index.html", ignoreCase = true) && file.parentFile == projectDir &&
                                    projectKind(projectDir) == ProjectKind.React
                                ) {
                                    val html = reactPreviewHtml(file.readText())
                                    return WebResourceResponse("text/html", "utf-8", html.byteInputStream())
                                }
                                // Python projects: the list of files to copy into Python.
                                if (file.name == ".mochu-files.json") {
                                    return WebResourceResponse("application/json", "utf-8", pythonProjectFileList(projectDir).byteInputStream())
                                }
                            }
                            return assetLoader.shouldInterceptRequest(url)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return !(url.startsWith("https://appassets.androidplatform.net/projects/") ||
                                url.startsWith("https://appassets.androidplatform.net/runtime/") || url.startsWith("about:blank"))
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                        }
                    }
                    // Console output and uncaught errors feed the Console tab and automatic fixes.
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                            val level = when (message.messageLevel()) {
                                android.webkit.ConsoleMessage.MessageLevel.ERROR -> "error"
                                android.webkit.ConsoleMessage.MessageLevel.WARNING -> "warn"
                                else -> "log"
                            }
                            val source = message.sourceId().orEmpty().substringAfterLast('/').substringBefore('?')
                            PreviewConsole.add(ConsoleEntry(level, message.message(), if (source.isBlank()) "" else "$source:${message.lineNumber()}"))
                            return true
                        }
                    }
                }
            },
            update = { webView ->
                val path = indexPath
                if (path != null && File(path).exists()) {
                    val file = File(path)
                    val projectsRoot = File(webView.context.filesDir, "projects")
                    val relativeFromProjects = runCatching {
                        val projectPrefix = projectsRoot.canonicalPath.trimEnd(File.separatorChar) + File.separator
                        val fileCanonical = file.canonicalPath
                        if (fileCanonical.startsWith(projectPrefix)) {
                            fileCanonical.removePrefix(projectPrefix).replace(File.separatorChar, '/')
                        } else {
                            file.name
                        }
                    }.getOrElse { file.name }
                    val encodedRelative = relativeFromProjects
                        .split('/')
                        .filter { it.isNotBlank() }
                        .joinToString("/") { segment -> Uri.encode(segment) }
                    val projectDir = file.parentFile
                    val python = projectDir != null && projectKind(projectDir) == ProjectKind.Python
                    val targetKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:${state.previewVersion}:$python"
                    if (webView.tag != targetKey) {
                        webView.tag = targetKey
                        PreviewConsole.clear()
                        val stamp = "${file.lastModified()}_${file.length()}_${state.previewVersion}"
                        val targetUrl = if (python) {
                            "https://appassets.androidplatform.net/runtime/pyodide/terminal.html?project=${Uri.encode(projectDir!!.name)}&v=$stamp"
                        } else {
                            "https://appassets.androidplatform.net/projects/$encodedRelative?v=$stamp"
                        }
                        webView.stopLoading()
                        webView.clearCache(true)
                        webView.loadUrl(targetUrl)
                    }
                } else {
                    val emptyKey = "empty:${state.previewVersion}"
                    if (webView.tag != emptyKey) {
                        webView.tag = emptyKey
                        webView.loadDataWithBaseURL(
                            "https://appassets.androidplatform.net/",
                            "<html><body style='font-family:sans-serif;padding:24px'><h2>No index.html yet</h2><p>Ask the model to create an app.</p></body></html>",
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun CodePane(state: BuilderUiState) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            if (state.isBusy && state.streamingCode.isNotBlank()) {
                "Streaming generated code..."
            } else {
                "Code: ${state.selectedCodePath}"
            },
            fontWeight = FontWeight.Bold
        )
        if (state.lastWrittenPaths.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Last written: ${state.lastWrittenPaths.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        AutoScrollingCodeText(
            text = codeTextForDisplay(state),
            fontScale = state.codeFontScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}



/** The preview's console: logs, warnings and errors, with a button to have the model fix the errors. */
@Composable
fun ConsolePane() {
    val entries by PreviewConsole.entries.collectAsState()
    val actions = LocalCodeActions.current
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Console", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { PreviewConsole.clear() }, enabled = entries.isNotEmpty()) { Text("Clear") }
            Button(onClick = actions.fixErrors, enabled = entries.any { it.level == "error" }) { Text("Fix errors") }
        }
        if (entries.isEmpty()) {
            Text(
                "Messages from the running app (console.log, warnings, errors) show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val scroll = rememberScrollState()
        LaunchedEffect(entries.size) { scroll.animateScrollTo(scroll.maxValue) }
        Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEach { entry ->
                Text(
                    (if (entry.source.isNotBlank()) "[${entry.source}] " else "") + entry.message,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (entry.level) {
                        "error" -> MaterialTheme.colorScheme.error
                        "warn" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
