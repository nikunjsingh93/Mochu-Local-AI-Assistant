package com.nikunj.gemmabuilder

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Name shown for the selected model, e.g. "Gemma 4 E2B" for gemma-4-E2B-it.litertlm. */
fun modelDisplayName(fileName: String): String =
    MODEL_CATALOG.firstOrNull { it.fileName == fileName }?.name ?: fileName.takeUnless { it.contains('.') && it.substringAfterLast('.') in setOf("gguf", "litertlm", "task") }
        ?: fileName.substringBeforeLast('.').ifBlank { "No model" }

/** The Chat space: a general assistant conversation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatScreen(
    state: BuilderUiState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenTasks: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onBuildInStudio: (String) -> Unit,
    onToggleWeb: (Boolean) -> Unit,
    onThinkingLevel: (Int) -> Unit,
    onAttachPhoto: () -> Unit,
    onVoiceSend: (String) -> Unit,
    onToggleSpeak: (ChatMessage) -> Unit,
    onStopSpeaking: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onAttachDocument: () -> Unit,
    onRemoveDocument: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onStop: () -> Unit
) {
    // Links in replies (e.g. sources) open in the in-app Browser instead of leaving the app.
    val inAppLinks = remember(onOpenLink) {
        object : UriHandler {
            override fun openUri(uri: String) = onOpenLink(uri)
        }
    }
    val context = LocalContext.current
    val mic = rememberVoiceMic(onPartial = onPromptChange, onFinal = onVoiceSend, onStart = onStopSpeaking)
    val listening = mic.listening
    val micLevel = mic.level
    val onMic = mic.onMic
    CompositionLocalProvider(LocalUriHandler provides inAppLinks) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Rounded.Menu, contentDescription = "Chats") }
            },
            title = { ModelChip(state, onOpenModels) },
            actions = {
                IconButton(onClick = onOpenTasks) {
                    Icon(Icons.Rounded.Schedule, contentDescription = "Tasks, timers and reminders")
                }
                IconButton(onClick = onNewChat, enabled = !state.isBusy) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "New chat")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty() && state.streamingReply == null) {
                WelcomeState(state, onOpenModels, onDownloadModel, onPromptChange, onSend, onBuildInStudio)
            } else {
                // "Ask more" chips on cards send their question like a typed message.
                androidx.compose.runtime.CompositionLocalProvider(LocalAskAction provides { question -> onPromptChange(question); onSend() }) {
                    MessageList(state, onToggleSpeak)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Photo previews sit above the context meter, which stays right on top of the message box.
            if (state.chatImages.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.chatImages.forEach { path ->
                        Box {
                            PhotoThumbnail(path, Modifier.size(64.dp))
                            Surface(
                                onClick = { onRemovePhoto(path) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.inverseSurface,
                                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove photo",
                                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (state.chatDocs.isNotEmpty() || state.readingDocument) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.chatDocs.forEach { path -> DocumentChip(chatDocumentName(path), onRemove = { onRemoveDocument(path) }) }
                    if (state.readingDocument) DocumentChip("Reading…", onRemove = null)
                }
            }
            ChatClocks(state.activeConversationId, Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            if (state.messages.isNotEmpty()) ContextMeter(state, Modifier.padding(horizontal = 8.dp))
            Composer(
                text = state.prompt,
                enabled = !state.isBusy,
                canSend = (state.prompt.isNotBlank() || state.chatImages.isNotEmpty() || state.chatDocs.isNotEmpty()) && !state.isBusy && !state.readingDocument && state.modelReady,
                placeholder = when {
                    !state.modelReady -> "Download a model to start"
                    listening -> "Listening…"
                    state.chatDocs.isNotEmpty() -> "Ask about the ${if (state.chatDocs.size == 1) "document" else "documents"}"
                    state.chatImages.isNotEmpty() -> "Ask about the photo"
                    // Web search shows on the globe button; the box always reads as a chat box.
                    else -> "Message Mochu…"
                },
                onTextChange = onPromptChange,
                onSend = onSend,
                busy = state.isBusy,
                onStop = onStop,
                onMic = if (state.modelReady) onMic else null,
                listening = listening,
                micLevel = micLevel,
                leading = {
                    Row {
                        IconButton(onClick = { onToggleWeb(!state.webSearchEnabled) }, enabled = !state.isBusy) {
                            Icon(
                                Icons.Rounded.Language,
                                contentDescription = if (state.webSearchEnabled) "Turn off web search" else "Search the web",
                                tint = if (state.webSearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ThinkingButton(state, onThinkingLevel)
                        AttachMenu(state, onAttachPhoto, onAttachDocument)
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun ModelChip(state: BuilderUiState, onClick: () -> Unit) {
    val backend = if (state.gemmaLoaded) state.engineLabel.substringAfter(" · ", "").takeIf { it.isNotBlank() } else null
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Memory,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (state.modelReady) modelDisplayName(state.modelName) else "Choose a model",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            backend?.let {
                Text(
                    " · $it",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private data class Suggestion(val title: String, val detail: String, val prompt: String, val studio: Boolean = false)

private val SUGGESTIONS = listOf(
    Suggestion("Explain", "how on-device AI works", "Explain in simple terms how an AI model can run entirely on a phone."),
    Suggestion("Write", "a friendly thank-you note", "Write a short, friendly thank-you note to a coworker who helped me this week."),
    Suggestion("Plan", "a weekend in Tokyo", "Plan a relaxed two-day weekend in Tokyo with food recommendations."),
    Suggestion("Build", "a tip calculator app", "Build a tip calculator app with a clean, modern design.", studio = true)
)

@Composable
private fun WelcomeState(
    state: BuilderUiState,
    onOpenModels: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onBuildInStudio: (String) -> Unit
) {
    val context = LocalContext.current
    val recommended = remember { recommendedModel(context) }
    // Until saved state is read, show nothing rather than the "download a model" card.
    if (!state.appReady) return
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        MochuLogo(64.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.modelReady) "Hi, I'm Mochu! What can I help with?" else "Hi, I'm Mochu!",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.modelReady) {
                "Your private AI assistant. I run entirely on your phone, so nothing you type leaves the device."
            } else {
                "Your private AI assistant that runs entirely on your phone. Download a model to get started."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        if (!state.modelReady) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recommended for your phone: ${recommended.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatBytes(recommended.sizeBytes)} · ${recommended.summary}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDownloadModel(recommended.id) }) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Download")
                        }
                        TextButton(onClick = onOpenModels) { Text("Other models") }
                    }
                }
            }
            return
        }
        SUGGESTIONS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { suggestion ->
                    Surface(
                        onClick = {
                            if (suggestion.studio) {
                                onBuildInStudio(suggestion.prompt)
                            } else {
                                onPromptChange(suggestion.prompt)
                                onSend()
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                suggestion.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MessageList(state: BuilderUiState, onToggleSpeak: (ChatMessage) -> Unit) {
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.streamingReply != null) 1 else 0
    // Follow new text only while the user is at the bottom; scrolling up pauses it until they return.
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, canGoDown) ->
            if (scrolling && listState.lastScrolledBackward) followBottom = false
            if (!canGoDown) followBottom = true
        }
    }
    LaunchedEffect(itemCount) { followBottom = true }
    // Anything that grows the reply being written: its text, reasoning, web steps, or the loading row.
    LaunchedEffect(
        itemCount,
        state.streamingReply?.length,
        state.streamingThinking?.length,
        state.liveActivity.size,
        state.modelLoading
    ) {
        if (itemCount > 0 && followBottom) listState.scrollToItem(itemCount - 1, scrollOffset = Int.MAX_VALUE / 2)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(state.messages, key = { "${it.timestamp}-${it.role}" }) { message ->
            if (message.role == "user") {
                UserMessage(message, state.chatFontScale / 1.2f)
            } else {
                AssistantMessage(message, state.chatFontScale / 1.2f, speaking = state.speakingMessageId == message.timestamp, onToggleSpeak = { onToggleSpeak(message) })
            }
        }
        state.streamingReply?.let { partial ->
            item(key = "streaming") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.liveActivity.isNotEmpty()) ActivityLog(state.liveActivity, live = partial.isBlank())
                    state.streamingThinking?.let { thinking ->
                        ThinkingSection(thinking, state.streamingThinkingSeconds, live = partial.isBlank(), fontScale = state.chatFontScale / 1.2f)
                    }
                    when {
                        state.modelLoading -> LoadingModelRow(modelDisplayName(state.modelName))
                        partial.isNotBlank() -> RichMarkdown(partial, state.chatFontScale / 1.2f)
                        state.streamingThinking == null -> ThinkingDots()
                    }
                    if (state.imageGenerating) ImagePlaceholder()
                }
            }
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessage, fontScale: Float) {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                // Press and hold to copy the message.
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(message.text))
                    },
                    onLongClickLabel = "Copy message"
                )
        ) {
            Column {
                val photos = message.attachments.filter { File(it).isFile && !isChatDocument(it) }
                val documents = message.attachments.filter(::isChatDocument)
                if (documents.isNotEmpty()) {
                    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        documents.forEach { DocumentChip(chatDocumentName(it), onRemove = null) }
                    }
                }
                if (photos.isNotEmpty()) {
                    Row(
                        Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        photos.forEach { PhotoThumbnail(it, Modifier.size(if (photos.size == 1) 200.dp else 96.dp)) }
                    }
                }
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * fontScale) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                )
            }
        }
    }
}

/** A photo from app storage, decoded off the main thread at about the size it's shown. */
@Composable
private fun PhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                val options = BitmapFactory.Options().apply { inSampleSize = (longest / 512).coerceAtLeast(1) }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        bitmap?.let {
            Image(it, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, fontScale: Float, speaking: Boolean, onToggleSpeak: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column {
        if (message.activity.isNotEmpty()) {
            ActivityLog(message.activity, live = false)
            Spacer(Modifier.height(10.dp))
        }
        if (message.thinking.isNotBlank()) {
            ThinkingSection(message.thinking, message.thinkingSeconds, live = false, fontScale = fontScale)
            Spacer(Modifier.height(10.dp))
        }
        message.attachments.filter { File(it).isFile }.forEach { path -> CreatedImage(path) }
        RichMarkdown(message.text, fontScale)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(message.text)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy reply",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleSpeak, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (speaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (speaking) "Stop reading" else "Read aloud",
                    modifier = Modifier.size(18.dp),
                    tint = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                listOfNotNull(formatChatTimestamp(message.timestamp), message.statsInline?.removePrefix("• "))
                    .joinToString(" · ")
                    .replace(" • ", " · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shown under the user's message while the model loads into memory. */
@Composable
internal fun LoadingModelRow(modelName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(10.dp))
            Text("Loading $modelName…", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "The first message after opening the app takes a few seconds while the model loads.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The model's reasoning. While it thinks, the latest lines stream in; afterwards it collapses to
 * "Thought for 12s" and opens on tap.
 */
@Composable
fun ThinkingSection(thinking: String, seconds: Int, live: Boolean, fontScale: Float) {
    // null follows the default: open while thinking, closed once the answer starts.
    var expandedChoice by remember { mutableStateOf<Boolean?>(null) }
    val expanded = expandedChoice ?: live
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expandedChoice = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (live) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    when {
                        live -> "Thinking…"
                        seconds < 2 -> "Thought for a moment"
                        seconds < 60 -> "Thought for ${seconds}s"
                        else -> "Thought for ${seconds / 60}m ${seconds % 60}s"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Hide thinking" else "Show thinking",
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded && thinking.isNotBlank()) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                if (live) {
                    // Only the latest lines while it streams, so the card doesn't keep growing.
                    RichMarkdown(
                        thinking.lines().filter { it.isNotBlank() }.takeLast(6).joinToString("\n"),
                        fontScale * 0.85f,
                        color = muted,
                        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                    )
                } else {
                    RichMarkdown(
                        thinking,
                        fontScale * 0.85f,
                        color = muted,
                        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = index * 150), RepeatMode.Reverse),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(alpha)
            ) { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize()) {} }
        }
    }
}

/** Rounded message box with a circular send button, shared by Chat and Studio. */
@Composable
fun Composer(
    text: String,
    enabled: Boolean,
    canSend: Boolean,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    // While busy, the send button becomes a Stop button when [onStop] is given.
    busy: Boolean = false,
    onStop: (() -> Unit)? = null,
    // With nothing to send, the send button becomes a mic (like ChatGPT); [listening] shows it live.
    onMic: (() -> Unit)? = null,
    listening: Boolean = false,
    micLevel: Float = 0f
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = if (leading != null) 4.dp else 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            leading?.let { Box(Modifier.padding(bottom = 4.dp)) { it() } }
            TextField(
                value = text,
                onValueChange = onTextChange,
                enabled = enabled,
                placeholder = { Text(placeholder) },
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
            val showStop = busy && onStop != null
            val showMic = !showStop && onMic != null && (listening || (!canSend && text.isBlank() && enabled))
            FilledIconButton(
                onClick = {
                    when {
                        showStop -> onStop?.invoke()
                        showMic -> onMic?.invoke()
                        else -> onSend()
                    }
                },
                enabled = canSend || showStop || showMic,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(44.dp)
                    // The button swells with your voice while listening.
                    .graphicsLayer {
                        val scale = if (listening) 1f + micLevel * 0.18f else 1f
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                if (showStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop")
                } else if (showMic) {
                    Icon(
                        if (listening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                        contentDescription = if (listening) "Stop listening" else "Talk"
                    )
                } else {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send")
                }
            }
        }
    }
}

/**
 * What the assistant did for a reply: searches, pages read, pages skipped. Collapsed to one line
 * once the reply is done; shown in full (with a spinner on the current step) while it runs.
 */
@Composable
fun ActivityLog(steps: List<ActivityStep>, live: Boolean) {
    var expanded by rememberSaveable(steps.size) { mutableStateOf(live) }
    val uriHandler = LocalUriHandler.current
    val reads = steps.count { it.kind == ACTIVITY_READ }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !live) { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    when {
                        live -> "Researching on the web…"
                        steps.none { it.kind == ACTIVITY_SEARCH } -> "Read ${if (reads == 1) "a page" else "$reads pages"} from the Browser"
                        reads == 0 -> "Searched the web"
                        else -> "Searched the web · $reads page${if (reads == 1) "" else "s"} read"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                if (!live) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Hide steps" else "Show steps",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (expanded || live) {
                steps.forEachIndexed { index, step ->
                    val running = live && index == steps.lastIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = step.url != null) { step.url?.let(uriHandler::openUri) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (running) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                when (step.kind) {
                                    ACTIVITY_SEARCH -> Icons.Rounded.Search
                                    ACTIVITY_READ -> Icons.AutoMirrored.Rounded.Article
                                    ACTIVITY_SKIP -> Icons.Rounded.Block
                                    else -> Icons.Rounded.Info
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            step.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (step.url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private val THINKING_LEVELS = listOf(
    Triple(0, "Off", "Answer right away"),
    Triple(1, "Quick", "Think briefly first"),
    Triple(2, "Balanced", "Think it through"),
    Triple(3, "Deep", "Think as long as it needs")
)

/** Composer button for thinking: off, or how long the model may think before it answers. */
@Composable
private fun ThinkingButton(state: BuilderUiState, onLevel: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = if (state.thinkingEnabled) state.thinkingLevel else 0
    Box {
        IconButton(onClick = { open = true }, enabled = !state.isBusy) {
            Icon(
                if (current > 0) Icons.Rounded.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = "Thinking: ${THINKING_LEVELS[current].second}",
                tint = if (current > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Thinking",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            THINKING_LEVELS.forEach { (level, name, detail) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(name, fontWeight = if (level == current) FontWeight.Bold else FontWeight.Normal)
                            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    trailingIcon = if (level == current) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = { onLevel(level); open = false }
                )
            }
        }
    }
}

/** An image Mochu created, shown full width with a button to save it to the phone's Photos. */
@Composable
private fun CreatedImage(path: String) {
    val context = LocalContext.current
    Column(Modifier.padding(bottom = 6.dp)) {
        val ratio = remember(path) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 1f
        }
        PhotoThumbnail(path, Modifier.fillMaxWidth().aspectRatio(ratio))
        TextButton(onClick = {
            val saved = saveImageToGallery(context, path)
            Toast.makeText(context, if (saved) "Saved to Pictures/Mochu" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save to Photos")
        }
    }
}

/** Where an image being created will appear: a softly pulsing square with a spinner. */
@Composable
private fun ImagePlaceholder() {
    val pulse = rememberInfiniteTransition(label = "image-placeholder")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "image-placeholder-alpha"
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text("Creating image…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The composer's + button: attach a photo or documents. */
@Composable
private fun AttachMenu(state: BuilderUiState, onPhoto: () -> Unit, onDocument: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !state.isBusy) {
            Icon(Icons.Rounded.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Photo") },
                leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null) },
                enabled = state.chatImages.size < 3,
                onClick = { open = false; onPhoto() }
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Documents")
                        Text("PDF, Word, text, CSV, code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                enabled = state.chatDocs.size < MAX_CHAT_DOCUMENTS,
                onClick = { open = false; onDocument() }
            )
        }
    }
}

/** A document's name in a small pill, with a remove button while it is still being written. */
@Composable
private fun DocumentChip(name: String, onRemove: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = if (onRemove == null) 12.dp else 2.dp)) {
            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp).padding(vertical = 8.dp))
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove $name", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
