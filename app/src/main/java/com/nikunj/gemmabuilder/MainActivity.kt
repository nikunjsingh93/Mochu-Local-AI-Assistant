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

private const val SAMPLE_PLACEHOLDER = "Build anything..."
private val ORDERED_LIST_PREFIX = Regex("""^\d+\.\s+""")
private val OnDeviceLightColors = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8ECFF),
    onPrimaryContainer = Color(0xFF002A4D),
    secondary = Color(0xFF2D6FB8),
    secondaryContainer = Color(0xFFDCEBFF)
)
private val OnDeviceDarkColors = darkColorScheme(
    primary = Color(0xFF3AA0FF),
    onPrimary = Color(0xFF002846),
    primaryContainer = Color(0xFF003C69),
    onPrimaryContainer = Color(0xFFD5ECFF),
    secondary = Color(0xFF8DC2FF),
    secondaryContainer = Color(0xFF204A74)
)
private val audioTranscriptCache = mutableMapOf<String, Pair<Long, String>>()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(true)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        setContent { BuilderRoot() }
    }
}

@Composable
fun BuilderRoot() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    LaunchedEffect(configuration.orientation) {
        val window = (context as? Activity)?.window
        window?.setSoftInputMode(
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
            }
        )
    }

    var themeMode by rememberSaveable {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("themeMode", "system") ?: "system"
        )
    }
    val setThemeMode: (String) -> Unit = { mode ->
        themeMode = mode
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("themeMode", mode)
            .apply()
    }
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    SideEffect {
        val window = (context as? Activity)?.window
        val barColor = if (useDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        window?.statusBarColor = barColor
        window?.navigationBarColor = barColor
        window?.decorView?.systemUiVisibility = if (useDarkTheme) {
            0
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    MaterialTheme(colorScheme = if (useDarkTheme) OnDeviceDarkColors else OnDeviceLightColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BuilderApp(themeMode = themeMode, onThemeModeChange = setThemeMode)
        }
    }
}

@Composable
fun BuilderApp(
    vm: BuilderViewModel = viewModel(),
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.prepare(context.applicationContext) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) vm.importModel(context.applicationContext, uri) }
    )

    val fileImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> if (uris.isNotEmpty()) vm.importFiles(context.applicationContext, uris) }
    )

    var pendingExportPath by remember { mutableStateOf<String?>(null) }
    var settingsScreenOpen by remember { mutableStateOf(false) }
    var modelsScreenOpen by remember { mutableStateOf(false) }
    val fileSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html"),
        onResult = { uri ->
            val path = pendingExportPath
            if (uri != null && path != null) vm.exportFile(context.applicationContext, path, uri)
            pendingExportPath = null
        }
    )

    val zipSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri -> if (uri != null) vm.exportProjectZip(context.applicationContext, uri) }
    )
    val pwaZipSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri -> if (uri != null) vm.exportPwaReadyProjectZip(context.applicationContext, uri) }
    )

    BackHandler(enabled = state.previewFullscreen) { vm.togglePreviewFullscreen() }
    BackHandler(enabled = state.sidebarOpen && !state.previewFullscreen) { vm.toggleSidebar() }

    if (state.previewFullscreen) {
        FullscreenPreview(state = state)
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val parentMaxWidth = maxWidth
        val compact = parentMaxWidth < 840.dp
        Box(Modifier.fillMaxSize()) {
            MainBuilderContent(
                state = state,
                compact = compact,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onOpenSettings = { settingsScreenOpen = true },
                onOpenModels = {
                    vm.refreshModelDownloads(context.applicationContext)
                    modelsScreenOpen = true
                },
                onToggleSidebar = { vm.toggleSidebar() },
                onImportModel = { modelPicker.launch(arrayOf("*/*")) },
                onImportFiles = { fileImporter.launch(arrayOf("*/*")) },
                onPromptChange = vm::setPrompt,
                onGenerate = { vm.generate(context.applicationContext) },
                onOpenImagePreview = { relativePath -> vm.openImagePreview(context.applicationContext, relativePath) },
                onTab = vm::setTab,
                onRefresh = { vm.reloadPreview() },
                onToggleFullscreen = { vm.togglePreviewFullscreen() },
                onToggleWorkPanelCollapsed = { vm.toggleWorkPanelCollapsed() },
                onOpenFileInCode = { relativePath -> vm.openFileInCode(context.applicationContext, relativePath) },
                onDeleteFile = { relativePath -> vm.deleteFile(context.applicationContext, relativePath) },
                onSaveFile = { relativePath ->
                    pendingExportPath = relativePath
                    fileSaver.launch(relativePath.substringAfterLast('/'))
                },
                onSaveZip = { zipSaver.launch("OnDeviceStudioProject.zip") },
                onSavePwaZip = { pwaZipSaver.launch("OnDeviceStudioPWA.zip") }
            )
            if (settingsScreenOpen) {
                SettingsScreen(
                    chatFontScale = state.chatFontScale,
                    codeFontScale = state.codeFontScale,
                    contextSizeChars = state.contextSizeChars,
                    backendPreference = state.backendPreference,
                    speculativeDecodingEnabled = state.speculativeDecodingEnabled,
                    onChatFontScale = { vm.setChatFontScale(context.applicationContext, it) },
                    onCodeFontScale = { vm.setCodeFontScale(context.applicationContext, it) },
                    onContextSizeChange = { vm.setContextSizeChars(context.applicationContext, it) },
                    onBackendPreferenceChange = { vm.setBackendPreference(context.applicationContext, it) },
                    onSpeculativeDecodingChange = { vm.setSpeculativeDecodingEnabled(context.applicationContext, it) },
                    onDismiss = { settingsScreenOpen = false }
                )
            }
            if (modelsScreenOpen) {
                ModelDownloadsScreen(
                    state = state,
                    onDownload = { vm.downloadModel(context.applicationContext, it) },
                    onCancel = { vm.cancelModelDownload(context.applicationContext, it) },
                    onUse = { vm.useDownloadedModel(context.applicationContext, it) },
                    onDelete = { vm.deleteDownloadedModel(context.applicationContext, it) },
                    onAllowMobileDataChange = { vm.setAllowMobileDataDownloads(context.applicationContext, it) },
                    onImportFile = {
                        modelsScreenOpen = false
                        modelPicker.launch(arrayOf("*/*"))
                    },
                    onDismiss = { modelsScreenOpen = false }
                )
            }

            if (state.sidebarOpen) {
                Row(Modifier.fillMaxSize()) {
                    val sidebarWidth: Dp = if (compact) (parentMaxWidth.value * 0.84f).dp else 320.dp
                    ConversationsSidebar(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight(),
                        state = state,
                        onNewChat = { vm.newConversation(context.applicationContext) },
                        onSelect = { id -> vm.selectConversation(context.applicationContext, id) },
                        onDelete = { id -> vm.deleteConversation(context.applicationContext, id) },
                        onClose = { vm.toggleSidebar() }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = if (compact) 0.18f else 0.28f))
                            .clickable { vm.toggleSidebar() }
                    )
                }
            }
            state.previewImagePath?.let { imagePath ->
                ImagePreviewOverlay(
                    imagePath = imagePath,
                    onClose = vm::closeImagePreview
                )
            }
        }
    }
}

@Composable
fun MainBuilderContent(
    state: BuilderUiState,
    compact: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onToggleSidebar: () -> Unit,
    onImportModel: () -> Unit,
    onImportFiles: () -> Unit,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onOpenImagePreview: (String) -> Unit,
    onTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleWorkPanelCollapsed: () -> Unit,
    onOpenFileInCode: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onSaveFile: (String) -> Unit,
    onSaveZip: () -> Unit,
    onSavePwaZip: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    val cutoutStartInsetDp: Dp
    val cutoutEndInsetDp: Dp
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val cutout = view.rootWindowInsets?.displayCutout
        val safeLeft = cutout?.safeInsetLeft ?: 0
        val safeRight = cutout?.safeInsetRight ?: 0
        val startPx = if (layoutDirection == LayoutDirection.Ltr) safeLeft else safeRight
        val endPx = if (layoutDirection == LayoutDirection.Ltr) safeRight else safeLeft
        cutoutStartInsetDp = with(density) { startPx.toDp() }
        cutoutEndInsetDp = with(density) { endPx.toDp() }
    } else {
        cutoutStartInsetDp = 0.dp
        cutoutEndInsetDp = 0.dp
    }
    BackHandler(enabled = compact && !state.workPanelCollapsed && !state.previewFullscreen && !state.sidebarOpen) {
        onToggleWorkPanelCollapsed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = cutoutStartInsetDp, end = cutoutEndInsetDp)
    ) {
        Header(
            state = state,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenSettings = onOpenSettings,
            onOpenModels = onOpenModels,
            onToggleSidebar = onToggleSidebar,
            onImportModel = onImportModel
        )
        Spacer(Modifier.height(10.dp))
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChatPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (state.workPanelCollapsed) 1f else 0.48f),
                    state = state,
                    onPromptChange = onPromptChange,
                    onGenerate = onGenerate,
                    onAddFiles = onImportFiles,
                    onOpenImagePreview = onOpenImagePreview,
                    chatFontScale = state.chatFontScale,
                    liftInputForKeyboard = state.workPanelCollapsed
                )
                WorkPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (state.workPanelCollapsed) Modifier.height(64.dp) else Modifier.weight(0.52f)),
                    state = state,
                    onTab = { index ->
                        if (state.workPanelCollapsed) onToggleWorkPanelCollapsed()
                        onTab(index)
                    },
                    onRefresh = onRefresh,
                    onToggleFullscreen = onToggleFullscreen,
                    onToggleWorkPanelCollapsed = onToggleWorkPanelCollapsed,
                    onImportFiles = onImportFiles,
                    onOpenFileInCode = onOpenFileInCode,
                    onOpenImagePreview = onOpenImagePreview,
                    onDeleteFile = onDeleteFile,
                    onSaveFile = onSaveFile,
                    onSaveZip = onSaveZip,
                    onSavePwaZip = onSavePwaZip
                )
            }
        } else {
            if (state.workPanelCollapsed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ChatPanel(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(),
                            state = state,
                            onPromptChange = onPromptChange,
                            onGenerate = onGenerate,
                            onAddFiles = onImportFiles,
                            onOpenImagePreview = onOpenImagePreview,
                            chatFontScale = state.chatFontScale,
                            liftInputForKeyboard = false
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .clickable { onToggleWorkPanelCollapsed() },
                        contentAlignment = Alignment.Center
                    ) { Text("◀", fontWeight = FontWeight.Bold) }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChatPanel(
                        modifier = Modifier
                            .width(420.dp)
                            .fillMaxHeight(),
                        state = state,
                        onPromptChange = onPromptChange,
                        onGenerate = onGenerate,
                        onAddFiles = onImportFiles,
                        onOpenImagePreview = onOpenImagePreview,
                        chatFontScale = state.chatFontScale,
                        liftInputForKeyboard = false
                    )
                    WorkPanel(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        onTab = { index ->
                            if (state.workPanelCollapsed) onToggleWorkPanelCollapsed()
                            onTab(index)
                        },
                        onRefresh = onRefresh,
                        onToggleFullscreen = onToggleFullscreen,
                        onToggleWorkPanelCollapsed = onToggleWorkPanelCollapsed,
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
fun Header(
    state: BuilderUiState,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onToggleSidebar: () -> Unit,
    onImportModel: () -> Unit
) {
    var settingsOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onToggleSidebar, modifier = Modifier.height(38.dp)) { Text("☰") }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "OnDevice Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (state.messages.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${state.messages.size} msgs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = when {
                        state.isBusy -> state.status
                        state.gemmaLoaded -> "${state.modelName} ready"
                        state.modelReady -> "${state.modelName} imported - tap Send"
                        else -> "Get a model from the ⋮ menu"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { settingsOpen = true },
                    contentAlignment = Alignment.Center
                ) { Text("︙", fontWeight = FontWeight.Bold) }
                DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { settingsOpen = false; onOpenSettings() }
                    )
                    DropdownMenuItem(
                        text = { Text("Download models") },
                        onClick = { settingsOpen = false; onOpenModels() }
                    )
                    DropdownMenuItem(
                        text = { Text("Import model file") },
                        onClick = { settingsOpen = false; onImportModel() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (themeMode == "system") "✓ System theme" else "System theme") },
                        onClick = { onThemeModeChange("system"); settingsOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (themeMode == "light") "✓ Light theme" else "Light theme") },
                        onClick = { onThemeModeChange("light"); settingsOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (themeMode == "dark") "✓ Dark theme" else "Dark theme") },
                        onClick = { onThemeModeChange("dark"); settingsOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationsSidebar(
    modifier: Modifier = Modifier,
    state: BuilderUiState,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ConversationInfo?>(null) }

    pendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("Delete \"${chat.title}\" and its generated files? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        onDelete(chat.id)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClose) { Text("×") }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("New chat") }
            Spacer(Modifier.height(12.dp))
            Text("History", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.conversations.forEach { chat ->
                    val active = chat.id == state.activeConversationId
                    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, MaterialTheme.shapes.medium)
                            .clickable { onSelect(chat.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${chat.messageCount} messages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🗑",
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                                    .clickable { pendingDelete = chat }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatChatTimestamp(chat.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatPanel(
    modifier: Modifier = Modifier,
    state: BuilderUiState,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onAddFiles: () -> Unit,
    onOpenImagePreview: (String) -> Unit,
    chatFontScale: Float,
    liftInputForKeyboard: Boolean
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shouldLiftInput =
        isLandscape || (liftInputForKeyboard && configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.then(if (shouldLiftInput) Modifier.imePadding() else Modifier)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            val chatScrollState = rememberScrollState()
            LaunchedEffect(state.messages.size, state.isBusy, state.previewFullscreen) {
                delay(60)
                chatScrollState.animateScrollTo(chatScrollState.maxValue)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(chatScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.messages.isEmpty()) {
                    val emptyStateHint = if (state.modelReady) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("Start by describing the web app you want. The result will appear in the preview below.")
                        }
                    } else {
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("Get a model first: open the ⋮ menu and choose ")
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append("Download models")
                            pop()
                            append(". Recommended: ")
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append("Gemma 4 E2B")
                            pop()
                            append(". You can also import your own .litertlm or .gguf file.")
                        }
                    }
                    Text(
                        text = emptyStateHint,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * chatFontScale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(12.dp)
                    )
                } else {
                    state.messages.forEach { msg -> MessageBubble(msg, chatFontScale, onOpenImagePreview) }
                    if (state.isBusy) {
                        ProcessingBubble(state.status, chatFontScale)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.pendingAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.pendingAttachments.forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            var addMenuOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    IconButton(
                        onClick = { addMenuOpen = true },
                        enabled = !state.isBusy
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add attachments")
                    }
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Add files") },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                            onClick = { addMenuOpen = false; onAddFiles() }
                        )
                    }
                }
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { if (state.messages.isEmpty()) Text(SAMPLE_PLACEHOLDER) },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!state.isBusy && state.prompt.isNotBlank() && state.modelReady) onGenerate() })
                )
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.size(54.dp),
                    enabled = state.prompt.isNotBlank() && !state.isBusy && state.modelReady
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "➤",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = (-2).dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessage, chatFontScale: Float, onOpenImagePreview: (String) -> Unit) {
    val mine = message.role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val dark = ColorUtils.calculateLuminance(MaterialTheme.colorScheme.background.toArgb()) < 0.5
    val userBubbleColor = if (dark) Color(0xFF2E3137) else Color(0xFFE8E8E8)
    val userTextColor = if (dark) Color(0xFFF3F4F6) else Color(0xFF121417)
    val assistantTextColor = if (dark) Color(0xFFE5E7EB) else Color(0xFF17191C)
    val metaColor = if (dark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val bubbleModifier = Modifier
        .fillMaxWidth(if (mine) 0.60f else 1f)
        .then(
            if (mine) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboard.setText(AnnotatedString(message.text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Modifier
            }
        )
    if (!mine) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            MarkdownMessageText(
                text = message.text,
                fontScale = chatFontScale,
                textColor = assistantTextColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatMessageMeta(message),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor
            )
        }
        return
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = bubbleModifier
                .background(userBubbleColor, MaterialTheme.shapes.medium)
                .padding(10.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatMessageMeta(message),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            message.text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * chatFontScale),
            color = userTextColor
        )
        if (message.attachments.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.attachments.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = userTextColor,
                        modifier = Modifier
                            .background(userBubbleColor.copy(alpha = if (dark) 0.85f else 0.95f), MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(enabled = isPreviewableImageFile(name)) { onOpenImagePreview(name) }
                    )
                }
            }
        }
        }
    }
}

private fun formatMessageMeta(message: ChatMessage): String {
    val time = formatChatTimestamp(message.timestamp)
    val stats = message.statsInline?.trim().orEmpty()
    return if (stats.isBlank()) time else "$time $stats"
}

@Composable
fun ProcessingBubble(status: String, chatFontScale: Float) {
    val dark = ColorUtils.calculateLuminance(MaterialTheme.colorScheme.background.toArgb()) < 0.5
    val metaColor = if (dark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    Text(
        text = when {
            status.contains("Loading", ignoreCase = true) -> "Loading model..."
            status.contains("Thinking", ignoreCase = true) || status.contains("Generating", ignoreCase = true) -> "Thinking..."
            else -> "Processing..."
        },
        style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * chatFontScale),
        color = metaColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
    )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) { Text("↻", fontWeight = FontWeight.Bold) }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .clickable { onToggleFullscreen() },
                    contentAlignment = Alignment.Center
                ) { Text("⛶", fontWeight = FontWeight.Bold) }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .clickable { onToggleWorkPanelCollapsed() },
                    contentAlignment = Alignment.Center
                ) { Text(if (isLandscape) (if (state.workPanelCollapsed) "◀" else "▶") else (if (state.workPanelCollapsed) "▴" else "▾"), fontWeight = FontWeight.Bold) }
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Preview", "Code", "Files").forEachIndexed { index, title ->
                            val selected = state.tab == index
                            val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            Text(
                                text = title,
                                modifier = Modifier
                                    .background(bg, MaterialTheme.shapes.small)
                                    .clickable { onTab(index) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (!state.workPanelCollapsed) {
                when (state.tab) {
                    0 -> PreviewPane(state)
                    1 -> CodePane(state)
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
                    .build()

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
                            return request?.url?.let { assetLoader.shouldInterceptRequest(it) }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return !(url.startsWith("https://appassets.androidplatform.net/projects/") || url.startsWith("about:blank"))
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
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
                    val targetKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:${state.previewVersion}:direct"
                    if (webView.tag != targetKey) {
                        webView.tag = targetKey
                        val targetUrl = "https://appassets.androidplatform.net/projects/$encodedRelative?v=${file.lastModified()}_${file.length()}_${state.previewVersion}"
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

class LiteRtGemmaEngine(
    private val context: Context,
    private val modelPath: String,
    private val backendPreference: String,
    private val speculativeDecodingEnabled: Boolean,
    private val maxNumTokens: Int
) : BuilderEngine {
    private data class LoadAttempt(val backend: String, val speculativeDecoding: Boolean, val maxNumTokens: Int?)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationKey: String? = null
    private var tokenLimit: Int? = null

    /** Backend the model actually loaded on, e.g. "GPU" or "CPU". */
    var activeBackend: String = backendPreference.uppercase(Locale.US)
        private set

    /** Why the preferred setup was not used, or null when it loaded as configured. */
    var fallbackReason: String? = null
        private set

    @OptIn(ExperimentalApi::class)
    suspend fun load() = withContext(Dispatchers.IO) {
        val preferred = backendPreference.lowercase(Locale.US)
        // Try the requested setup first, then progressively safer ones: without speculative decoding,
        // with the model's default context window, and finally on CPU.
        val attempts = buildList {
            add(LoadAttempt(preferred, speculativeDecodingEnabled, maxNumTokens))
            add(LoadAttempt(preferred, false, maxNumTokens))
            add(LoadAttempt(preferred, false, null))
            if (preferred != "cpu") {
                add(LoadAttempt("cpu", false, maxNumTokens))
                add(LoadAttempt("cpu", false, null))
            }
        }.distinct()

        var firstError: Throwable? = null
        for (attempt in attempts) {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = when (attempt.backend) {
                    "gpu" -> Backend.GPU()
                    "npu" -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
                    else -> Backend.CPU()
                },
                maxNumTokens = attempt.maxNumTokens,
                cacheDir = context.cacheDir.absolutePath
            )
            val candidate = Engine(config)
            val result = runCatching {
                try {
                    ExperimentalFlags.enableSpeculativeDecoding = attempt.speculativeDecoding
                    candidate.initialize()
                } finally {
                    ExperimentalFlags.enableSpeculativeDecoding = null
                }
            }
            if (result.isSuccess) {
                engine = candidate
                tokenLimit = attempt.maxNumTokens
                activeBackend = attempt.backend.uppercase(Locale.US)
                fallbackReason = if (attempt == attempts.first()) null else firstError?.message ?: "unknown error"
                return@withContext
            }
            if (firstError == null) firstError = result.exceptionOrNull()
            runCatching { if (candidate.isInitialized()) candidate.close() }
        }
        throw firstError ?: IllegalStateException("Model load failed.")
    }

    private fun newConversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(BUILD_SYSTEM_PROMPT),
        samplerConfig = SamplerConfig(
            topK = 20,
            topP = 0.9,
            temperature = 0.35
        ),
        // Leave the other half of the window for the prompt and earlier turns.
        maxOutputToken = tokenLimit?.let { it / 2 },
        // Thinking models (e.g. Qwen3) otherwise spend the output budget in a hidden reasoning
        // channel the app never shows, which looks like a stuck "0 chars" generation.
        thinkingConfig = ThinkingConfig(enableThinking = false)
    )

    override fun generate(sessionKey: String, message: String): Flow<String> = flow {
        val loadedEngine = engine ?: run {
            emit("Model is not loaded.")
            return@flow
        }
        val active = conversation?.takeIf { conversationKey == sessionKey && it.isAlive } ?: run {
            resetSession()
            loadedEngine.createConversation(newConversationConfig()).also {
                conversation = it
                conversationKey = sessionKey
            }
        }
        var finished = false
        try {
            active.sendMessageAsync(message).collect { token ->
                emit(token.toString())
            }
            finished = true
        } finally {
            // A failed or cancelled turn leaves a partial reply in the KV cache, so start over next time.
            if (!finished) resetSession()
        }
    }

    override fun remainingTokens(sessionKey: String): Int? {
        val limit = tokenLimit ?: return null
        val active = conversation?.takeIf { conversationKey == sessionKey && it.isAlive } ?: return null
        return runCatching { limit - active.getTokenCount() }.getOrNull()
    }

    override fun resetSession() {
        conversation?.let { open ->
            runCatching { open.cancelProcess() }
            runCatching { open.close() }
        }
        conversation = null
        conversationKey = null
    }

    override fun close() {
        resetSession()
        runCatching { engine?.close() }
        engine = null
    }
}

class MediaPipeMultimodalEngine(
    private val context: Context,
    private val modelPath: String
) {
    fun load() = Unit
    fun isReady(): Boolean = false
    fun generateResponse(prompt: String, root: File, attachmentPaths: List<String>): String = ""
    fun close() = Unit
}

class DemoBuilderEngine : BuilderEngine {
    override fun generate(sessionKey: String, message: String): Flow<String> = flow {
        val lower = message.lowercase(Locale.US)
        val html = when {
            "calculator" in lower -> calculatorHtml()
            "racing" in lower || "ps1" in lower || "game" in lower -> racingMenuHtml()
            else -> genericAppHtml(message)
        }
        emit(
            """
<action name="write_file">
<path>index.html</path>
<content>
$html
</content>
</action>
            """.trimIndent()
        )
    }
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

@Composable
fun MarkdownMessageText(text: String, fontScale: Float, textColor: Color) {
    val dark = ColorUtils.calculateLuminance(MaterialTheme.colorScheme.background.toArgb()) < 0.5
    val codeBg = if (dark) Color(0xFF2B2F37) else Color(0xFFEFF1F4)
    val inlineCodeBg = if (dark) Color(0xFF3A3F48) else Color(0xFFE8EAEE)
    val baseStyle = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * fontScale)
    val codeStyle = baseStyle.copy(fontFamily = FontFamily.Monospace)

    val sections = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEachIndexed { index, segment ->
            if (index % 2 == 1) {
                val codeBody = segment.lineSequence().drop(1).joinToString("\n").ifBlank { segment }.trim()
                if (codeBody.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = codeBody,
                            style = codeStyle,
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(codeBg, MaterialTheme.shapes.small)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                                .padding(10.dp)
                        )
                    }
                }
            } else {
                segment.lines().forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    if (line.isBlank()) {
                        Spacer(Modifier.height(2.dp))
                    } else {
                        val normalized = when {
                            line.startsWith("- ") || line.startsWith("* ") -> "• ${line.drop(2)}"
                            ORDERED_LIST_PREFIX.containsMatchIn(line) -> "• ${ORDERED_LIST_PREFIX.replace(line, "")}"
                            else -> line
                        }
                        Text(
                            text = parseInlineMarkdown(normalized, baseStyle, textColor, inlineCodeBg),
                            style = baseStyle,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

fun parseInlineMarkdown(
    text: String,
    baseStyle: androidx.compose.ui.text.TextStyle,
    textColor: Color,
    inlineCodeBg: Color
): AnnotatedString {
    val out = Builder()
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    out.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor))
                    out.append(text.substring(i + 2, end))
                    out.pop()
                    i = end + 2
                } else {
                    out.append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    out.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = textColor
                        )
                    )
                    out.append(text.substring(i + 1, end))
                    out.pop()
                    i = end + 1
                } else {
                    out.append(text[i]); i++
                }
            }
            else -> {
                out.append(text[i]); i++
            }
        }
    }
    return out.toAnnotatedString()
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
    "txt", "md", "json", "js", "mjs", "ts", "tsx", "jsx", "css", "scss", "sass",
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

fun htmlContextForModel(html: String): String {
    if (html.isBlank()) return ""
    val compact = html
        .replace(Regex("(?is)<script[^>]+src=[\"'][^\"']+[\"'][^>]*></script>"), "")
        .replace(Regex("(?is)<link[^>]+rel=[\"']stylesheet[\"'][^>]*>"), "")
        .replace(Regex("(?is)<!--.*?-->"), "")
        .replace(Regex("[ \t]{2,}"), " ")
        .trim()

    val maxChars = 12000
    if (compact.length <= maxChars) return compact

    val head = compact.take(8000)
    val tail = compact.takeLast(3000)
    return """
$head

<!-- GEMMA BUILDER NOTE: The imported HTML was very large, so the middle was omitted before sending to the local model. Preserve the intent of the page and rewrite a complete, self-contained index.html. -->

$tail
    """.trimIndent()
}

const val BUILD_SYSTEM_PROMPT = """
You are an offline Android coding agent.

You build small web apps using HTML, CSS, and JavaScript.

You MUST respond only with XML actions.

The host app may include extracted local file contents in the prompt using markers like:
<<<FILE:path>>>
...content...
<<<END_FILE>>>
Treat that as authoritative local file content (including text the app extracted from PDF/image/DOCX/audio). Do not claim you cannot read local files.

The first message of a chat contains a project snapshot. Later messages may contain only the user's request; the project files are then exactly what your earlier write_file actions produced.

Allowed action:

<action name="write_file">
<path>relative/path/from/project/root</path>
<content>
FULL FILE CONTENT HERE
</content>
</action>

<action name="reply">
<content>
ASSISTANT REPLY HERE
</content>
</action>

Rules:
- You may return one or more write_file actions.
- Use reply action when the user is asking a normal question and no file changes are required; do not modify files then.
- For edits, update only the needed files and keep paths relative to the project root.
- If the user asks for a different app, replace the current app completely. Do not keep the previous UI unless the user asks.
- By default, generate a single self-contained index.html that includes CSS in <style> and JavaScript in <script>.
- Only split into separate files (for example styles.css/app.js) when the user explicitly asks for separate files.
- If index.html references local files (for example styles.css or app.js), write each referenced file in the same response.
- Never place JavaScript in styles.css.
- No external links.
- No CDN.
- No remote images.
- Make it work offline.
- Make it touch-friendly for Android.
- Use responsive layout for phone screens.
- Center the main app content by default unless the user asks for a top/header layout.
- Use min-height:100vh and grid/flex centering for standalone generated apps.
- Use width:min(92vw, ...px) or similar responsive sizing instead of fixed wide layouts.
- Keep the code simple, complete, and compact for mobile: no unnecessary comments, huge CSS blocks, or repeated code.
- Never reference missing functions or variables.
- Do not write markdown or explanations outside XML actions.
- Never stop inside an XML action. The XML must be complete and must end with </content> and </action>.
"""

fun welcomeHtml(): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>OnDevice Studio</title>
<style>
  body { margin:0; min-height:100vh; font-family:system-ui,sans-serif; background:linear-gradient(135deg,#111827,#4338ca); color:white; display:grid; place-items:center; }
  .card { width:min(92vw,680px); background:rgba(255,255,255,.12); border:1px solid rgba(255,255,255,.18); border-radius:28px; padding:28px; box-shadow:0 30px 80px rgba(0,0,0,.35); }
  h1 { font-size:clamp(32px,8vw,64px); line-height:.95; margin:0 0 16px; }
  p { font-size:18px; line-height:1.5; color:#e5e7eb; }
  .pill { display:inline-block; padding:10px 14px; border-radius:999px; background:rgba(255,255,255,.16); margin-top:12px; }
</style>
</head>
<body>
  <main class="card">
    <h1>OnDevice Web App AI Builder</h1>
    <p>Ask the chat panel to build a calculator, game menu, landing page, or another tiny offline web app. The result will be written to index.html and previewed here.</p>
    <span class="pill">Ready for Preview</span>
  </main>
</body>
</html>
""".trimIndent()

fun defaultPwaManifest(): String = """
{
  "name": "OnDevice Studio App",
  "short_name": "OnDevice",
  "start_url": "./index.html",
  "display": "standalone",
  "background_color": "#0f172a",
  "theme_color": "#4338ca",
  "icons": []
}
""".trimIndent()

fun defaultServiceWorker(): String = """
const CACHE_NAME = "ondevice-studio-v1";
const APP_SHELL = [
  "./",
  "./index.html",
  "./manifest.webmanifest"
];

self.addEventListener("install", event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL))
  );
  self.skipWaiting();
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.map(key => key !== CACHE_NAME ? caches.delete(key) : null))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", event => {
  if (event.request.method !== "GET") return;
  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(networkResponse => {
        const copy = networkResponse.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, copy));
        return networkResponse;
      }).catch(() => caches.match("./index.html"));
    })
  );
});
""".trimIndent()

fun calculatorHtml(): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Touch Calculator</title>
<style>
  * { box-sizing:border-box; }
  body { margin:0; min-height:100vh; display:grid; place-items:center; background:#0b1020; color:white; font-family:system-ui,sans-serif; }
  .calc { width:min(94vw,420px); background:#151b2f; border-radius:30px; padding:20px; box-shadow:0 28px 70px rgba(0,0,0,.55); }
  .screen { min-height:96px; background:#050816; border-radius:22px; padding:18px; text-align:right; font-size:42px; overflow:hidden; margin-bottom:16px; }
  .keys { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; }
  button { height:72px; border:0; border-radius:20px; font-size:25px; font-weight:800; color:white; background:#283149; box-shadow:inset 0 -4px rgba(0,0,0,.25); }
  button:active { transform:translateY(2px); }
  .op { background:#4f46e5; }
  .danger { background:#dc2626; }
  .equals { background:#16a34a; grid-column:span 2; }
</style>
</head>
<body>
  <main class="calc">
    <div id="screen" class="screen">0</div>
    <div class="keys">
      <button class="danger" onclick="clearAll()">C</button><button onclick="press('(')">(</button><button onclick="press(')')">)</button><button class="op" onclick="press('/')">÷</button>
      <button onclick="press('7')">7</button><button onclick="press('8')">8</button><button onclick="press('9')">9</button><button class="op" onclick="press('*')">×</button>
      <button onclick="press('4')">4</button><button onclick="press('5')">5</button><button onclick="press('6')">6</button><button class="op" onclick="press('-')">−</button>
      <button onclick="press('1')">1</button><button onclick="press('2')">2</button><button onclick="press('3')">3</button><button class="op" onclick="press('+')">+</button>
      <button onclick="press('0')">0</button><button onclick="press('.')">.</button><button class="equals" onclick="calc()">=</button>
    </div>
  </main>
<script>
let expr = '';
const screen = document.getElementById('screen');
function render(){ screen.textContent = expr || '0'; }
function press(v){ expr += v; render(); }
function clearAll(){ expr = ''; render(); }
function calc(){
  try {
    if(!/^[0-9+\-*/().\s]+$/.test(expr)) throw new Error('bad input');
    expr = String(Function('return (' + expr + ')')());
  } catch(e) { expr = 'Error'; setTimeout(clearAll, 800); }
  render();
}
</script>
</body>
</html>
""".trimIndent()

fun racingMenuHtml(): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Retro Rally Menu</title>
<style>
  html,body { margin:0; height:100%; overflow:hidden; font-family:Impact, system-ui, sans-serif; background:#111; color:#fff; }
  body { background:radial-gradient(circle at 50% 25%, #f97316 0 8%, #7f1d1d 30%, #020617 78%); }
  .scan { position:fixed; inset:0; background:repeating-linear-gradient(to bottom, rgba(255,255,255,.08), rgba(255,255,255,.08) 1px, transparent 2px, transparent 5px); pointer-events:none; mix-blend-mode:overlay; }
  .road { position:fixed; left:50%; bottom:-12%; width:68vw; height:70vh; background:linear-gradient(#374151,#050505); transform:translateX(-50%) perspective(300px) rotateX(62deg); clip-path:polygon(38% 0,62% 0,100% 100%,0 100%); border:6px solid #facc15; }
  .car { position:fixed; left:50%; bottom:9%; transform:translateX(-50%); width:min(64vw,420px); height:120px; background:#ef4444; clip-path:polygon(10% 75%,25% 35%,45% 15%,75% 25%,92% 75%,82% 100%,18% 100%); box-shadow:0 30px #111; }
  .menu { position:relative; z-index:3; padding:28px; max-width:520px; }
  h1 { font-size:clamp(48px,14vw,96px); line-height:.8; margin:20px 0; text-shadow:6px 6px #000; color:#fde047; }
  button { display:block; width:min(86vw,360px); margin:14px 0; padding:18px; border:4px solid white; background:#111827cc; color:white; font-size:28px; text-align:left; text-transform:uppercase; box-shadow:6px 6px #000; }
  button:active { transform:translate(4px,4px); box-shadow:2px 2px #000; }
</style>
</head>
<body>
  <div class="road"></div><div class="car"></div><div class="scan"></div>
  <main class="menu">
    <h1>RALLY<br>1998</h1>
    <button onclick="flash('Arcade Race')">Start Race</button>
    <button onclick="flash('Garage')">Garage</button>
    <button onclick="flash('Options')">Options</button>
    <p id="msg">Touch a menu item.</p>
  </main>
<script>
function flash(txt){ document.getElementById('msg').textContent = txt + ' selected'; if(navigator.vibrate) navigator.vibrate(35); }
</script>
</body>
</html>
""".trimIndent()

fun genericAppHtml(prompt: String): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Generated App</title>
<style>
  body { margin:0; min-height:100vh; font-family:system-ui,sans-serif; background:#f3f4f6; color:#111827; display:grid; place-items:center; }
  .app { width:min(92vw,720px); background:white; border-radius:28px; padding:28px; box-shadow:0 24px 80px rgba(0,0,0,.12); }
  h1 { font-size:clamp(34px,8vw,64px); margin:0; }
  p { font-size:18px; line-height:1.5; color:#4b5563; }
  button { border:0; border-radius:18px; padding:18px 22px; font-size:18px; font-weight:800; background:#4f46e5; color:white; }
  .count { font-size:64px; font-weight:900; margin:18px 0; }
</style>
</head>
<body>
  <main class="app">
    <h1>Generated Offline App</h1>
    <p>This demo page was created from your request. Load Gemma to generate custom code with the local model.</p>
    <p><strong>Request:</strong> ${prompt.takeLast(500).replace("<", "&lt;").replace(">", "&gt;")}</p>
    <div id="count" class="count">0</div>
    <button onclick="add()">Tap me</button>
  </main>
<script>
let n=0; function add(){ n++; document.getElementById('count').textContent=n; }
</script>
</body>
</html>
""".trimIndent()
