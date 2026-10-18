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


// Chat transcript, message bubbles, composer, and markdown rendering.

private const val SAMPLE_PLACEHOLDER = "Build anything..."
private val ORDERED_LIST_PREFIX = Regex("""^\d+\.\s+""")

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
            ContextMeter(state)
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


