package com.nikunj.gemmabuilder

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
    MODEL_CATALOG.firstOrNull { it.fileName == fileName }?.name
        ?: fileName.substringBeforeLast('.').ifBlank { "No model" }

/** The Chat space: a general assistant conversation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatScreen(
    state: BuilderUiState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenModels: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onBuildInStudio: (String) -> Unit,
    onToggleWeb: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
    onStop: () -> Unit
) {
    // Links in replies (e.g. sources) open in the in-app Browser instead of leaving the app.
    val inAppLinks = remember(onOpenLink) {
        object : UriHandler {
            override fun openUri(uri: String) = onOpenLink(uri)
        }
    }
    CompositionLocalProvider(LocalUriHandler provides inAppLinks) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Rounded.Menu, contentDescription = "Chats") }
            },
            title = { ModelChip(state, onOpenModels) },
            actions = {
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
                MessageList(state)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (state.messages.isNotEmpty()) ContextMeter(state, Modifier.padding(horizontal = 8.dp))
            Composer(
                text = state.prompt,
                enabled = !state.isBusy,
                canSend = state.prompt.isNotBlank() && !state.isBusy && state.modelReady,
                placeholder = when {
                    !state.modelReady -> "Download a model to start"
                    state.webSearchEnabled -> "Search the web"
                    else -> "Message"
                },
                onTextChange = onPromptChange,
                onSend = onSend,
                busy = state.isBusy,
                onStop = onStop,
                leading = {
                    IconButton(onClick = { onToggleWeb(!state.webSearchEnabled) }, enabled = !state.isBusy) {
                        Icon(
                            Icons.Rounded.Language,
                            contentDescription = if (state.webSearchEnabled) "Turn off web search" else "Search the web",
                            tint = if (state.webSearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.modelReady) "What can I help with?" else "Welcome to OnDevice Studio",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.modelReady) {
                "Everything runs on your phone. Nothing you type leaves the device."
            } else {
                "A private AI assistant and app builder that runs entirely on your phone. Download a model to get started."
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
private fun MessageList(state: BuilderUiState) {
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
    LaunchedEffect(itemCount, state.streamingReply?.length, state.liveActivity.size) {
        if (itemCount > 0 && followBottom) listState.scrollToItem(itemCount - 1, scrollOffset = Int.MAX_VALUE / 2)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(state.messages, key = { "${it.timestamp}-${it.role}" }) { message ->
            if (message.role == "user") UserMessage(message, state.chatFontScale / 1.2f) else AssistantMessage(message, state.chatFontScale / 1.2f)
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
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * fontScale) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, fontScale: Float) {
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
private fun LoadingModelRow(modelName: String) {
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
private fun ThinkingSection(thinking: String, seconds: Int, live: Boolean, fontScale: Float) {
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
                    Text(
                        thinking.lines().filter { it.isNotBlank() }.takeLast(6).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
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
    onStop: (() -> Unit)? = null
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
            FilledIconButton(
                onClick = { if (showStop) onStop?.invoke() else onSend() },
                enabled = canSend || showStop,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(44.dp)
            ) {
                if (showStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop")
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
