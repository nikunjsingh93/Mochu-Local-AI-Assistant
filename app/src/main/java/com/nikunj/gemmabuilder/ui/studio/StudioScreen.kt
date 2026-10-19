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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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


// Studio space: a web-app project with chat on one side and preview/code/files on the other.

@Composable
fun StudioScreen(
    state: BuilderUiState,
    compact: Boolean,
    onOpenDrawer: () -> Unit,
    onNewProject: () -> Unit,
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
    onSavePwaZip: () -> Unit,
    onStop: () -> Unit = {}
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
            .imePadding()
    ) {
        StudioTopBar(state, onOpenDrawer, onNewProject)
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
                    liftInputForKeyboard = state.workPanelCollapsed,
                    onStop = onStop
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
                            liftInputForKeyboard = false,
                            onStop = onStop
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
                        liftInputForKeyboard = false,
                        onStop = onStop
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioTopBar(state: BuilderUiState, onOpenDrawer: () -> Unit, onNewProject: () -> Unit) {
    val title = state.conversations.firstOrNull { it.id == state.activeConversationId }?.title ?: "New project"
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Rounded.Menu, contentDescription = "Projects") }
        },
        title = {
            Column {
                Text(
                    if (title == "New chat") "New project" else title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        state.isBusy -> state.status
                        state.gemmaLoaded -> "${modelDisplayName(state.modelName)} ready"
                        state.modelReady -> "${modelDisplayName(state.modelName)} · loads on send"
                        else -> "No model yet: open Models"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            IconButton(onClick = onNewProject, enabled = !state.isBusy) {
                Icon(Icons.Rounded.Add, contentDescription = "New project")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}
