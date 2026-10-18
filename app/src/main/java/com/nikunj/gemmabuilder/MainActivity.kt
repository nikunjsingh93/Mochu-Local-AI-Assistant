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
                onOpenSettings = { settingsScreenOpen = true },
                onOpenModels = {
                    vm.refreshModelDownloads(context.applicationContext)
                    modelsScreenOpen = true
                },
                onToggleSidebar = { vm.toggleSidebar() },
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
                    contextTokens = state.contextTokens,
                    activeContextTokens = state.contextWindowTokens,
                    themeMode = themeMode,
                    backendPreference = state.backendPreference,
                    speculativeDecodingEnabled = state.speculativeDecodingEnabled,
                    onChatFontScale = { vm.setChatFontScale(context.applicationContext, it) },
                    onCodeFontScale = { vm.setCodeFontScale(context.applicationContext, it) },
                    onContextTokensChange = { vm.setContextTokens(context.applicationContext, it) },
                    onThemeModeChange = onThemeModeChange,
                    onBackendPreferenceChange = { vm.setBackendPreference(context.applicationContext, it) },
                    onSpeculativeDecodingChange = { vm.setSpeculativeDecodingEnabled(context.applicationContext, it) },
                    onDismiss = { settingsScreenOpen = false }
                )
            }
            state.memoryNotice?.let { notice ->
                AlertDialog(
                    onDismissRequest = { vm.dismissMemoryNotice() },
                    title = { Text("Ran out of memory") },
                    text = { Text(notice) },
                    confirmButton = { Button(onClick = { vm.dismissMemoryNotice() }) { Text("OK") } }
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
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onToggleSidebar: () -> Unit,
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
            onOpenSettings = onOpenSettings,
            onOpenModels = onOpenModels,
            onToggleSidebar = onToggleSidebar
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
fun Header(
    state: BuilderUiState,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onToggleSidebar: () -> Unit
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
                        state.modelReady -> "${state.modelName} selected - tap Send"
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
                DangerButton("Delete") {
                    pendingDelete = null
                    onDelete(chat.id)
                }
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


