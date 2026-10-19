package com.nikunj.gemmabuilder

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onBuildInStudio: (String) -> Unit
) {
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
                WelcomeState(state, onOpenModels, onPromptChange, onSend, onBuildInStudio)
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
                placeholder = if (state.modelReady) "Message" else "Download a model to start",
                onTextChange = onPromptChange,
                onSend = onSend
            )
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
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onBuildInStudio: (String) -> Unit
) {
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
                    Text("Recommended: Gemma 4 E2B", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "2.6 GB · fast on most phones · runs on the GPU",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Button(onClick = onOpenModels) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Get a model")
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
    LaunchedEffect(itemCount, state.streamingReply?.length) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1, scrollOffset = Int.MAX_VALUE / 2)
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
                if (partial.isBlank()) ThinkingDots() else RichMarkdown(partial, state.chatFontScale / 1.2f)
            }
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessage, fontScale: Float) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 320.dp)
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
    leading: (@Composable () -> Unit)? = null
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
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(44.dp)
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send")
            }
        }
    }
}
