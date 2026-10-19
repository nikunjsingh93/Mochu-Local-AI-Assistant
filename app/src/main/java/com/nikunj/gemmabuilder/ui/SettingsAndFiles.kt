package com.nikunj.gemmabuilder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    chatFontScale: Float,
    codeFontScale: Float,
    contextTokens: Int,
    activeContextTokens: Int?,
    themeMode: String,
    dynamicColor: Boolean,
    backendPreference: String,
    speculativeDecodingEnabled: Boolean,
    thinkingEnabled: Boolean,
    memories: List<String>,
    memoryEnabled: Boolean,
    speakVoiceReplies: Boolean,
    onChatFontScale: (Float) -> Unit,
    onCodeFontScale: (Float) -> Unit,
    onContextTokensChange: (Int) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBackendPreferenceChange: (String) -> Unit,
    onSpeculativeDecodingChange: (Boolean) -> Unit,
    onThinkingChange: (Boolean) -> Unit,
    onMemoryEnabledChange: (Boolean) -> Unit,
    onSpeakVoiceRepliesChange: (Boolean) -> Unit,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var settingsSubPage by rememberSaveable { mutableStateOf("root") }
    val context = LocalContext.current
    val appVersionLabel = remember(context) {
        val pm = context.packageManager
        val pkg = context.packageName
        val packageInfo = pm.getPackageInfo(pkg, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        "v$versionName"
    }
    val uriHandler = LocalUriHandler.current

    BackHandler(onBack = {
        if (settingsSubPage != "root") settingsSubPage = "root" else onDismiss()
    })

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (settingsSubPage != "root") settingsSubPage = "root" else onDismiss()
                }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = when (settingsSubPage) {
                        "privacy" -> "Privacy Policy"
                        "licenses" -> "Third-Party Licenses"
                        "memory" -> "Memory"
                        else -> "Settings"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (settingsSubPage == "memory") {
                MemoryPage(memories, memoryEnabled, onMemoryEnabledChange, onAddMemory, onDeleteMemory, Modifier.weight(1f))
            } else if (settingsSubPage == "privacy") {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "This app stores settings and chat/project files locally on your device. Imported files and model files stay on-device. We do not send your content to a remote server from this app.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "You can delete chats, project files, and imported assets inside the app at any time.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "This app uses third-party open source components. License and attribution details are available in Settings > Third-Party Licenses.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (settingsSubPage == "licenses") {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Open source components used in this app:", fontWeight = FontWeight.SemiBold)
                    Text("• Kotlin — Apache-2.0")
                    Text("• Jetpack Compose / AndroidX — Apache-2.0")
                    Text("• Kotlin Coroutines — Apache-2.0")
                    Text("• AndroidX WebKit — Apache-2.0")
                    Text("• Google ML Kit — Google SDK Terms")
                    Text("• LiteRT / LiteRT-LM Android — Google terms and licenses")
                    Text("• llama.cpp / ggml — MIT")
                    Text("• Inter typeface — SIL Open Font License 1.1")
                    Text("• PDFBox-Android — Apache-2.0")
                    Text(
                        "See each upstream project/release for full license text and notices.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Theme")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                            OutlinedButton(
                                onClick = { onThemeModeChange(mode) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (themeMode == mode) "✓ $label" else label)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Wallpaper colors")
                            Text(
                                "Match the app's accent to your wallpaper (Material You).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }
                    HorizontalDivider()
                    val contextOptions = CONTEXT_TOKEN_OPTIONS
                    // Follow the thumb while dragging; save only when it's released.
                    var draggingIndex by remember { mutableStateOf<Int?>(null) }
                    val savedIndex = contextOptions.indexOf(contextTokens).let { if (it >= 0) it else 2 }
                    val shownTokens = contextOptions[draggingIndex ?: savedIndex]
                    Text("Context window: ${String.format(Locale.US, "%,d", shownTokens)} tokens")
                    Text(
                        if (activeContextTokens != null && activeContextTokens < shownTokens) {
                            "The loaded model is using ${String.format(Locale.US, "%,d", activeContextTokens)} tokens so it fits in this phone's memory."
                        } else {
                            "How much of your project and chat the model can see at once. Bigger windows use more memory."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ThumbOnlySlider(
                        value = (draggingIndex ?: savedIndex).toFloat(),
                        onValueChange = { raw -> draggingIndex = raw.roundToInt().coerceIn(0, contextOptions.lastIndex) },
                        onValueChangeFinished = {
                            draggingIndex?.let { onContextTokensChange(contextOptions[it]) }
                            draggingIndex = null
                        },
                        valueRange = 0f..contextOptions.lastIndex.toFloat(),
                        steps = contextOptions.size - 2
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        contextOptions.forEach { value ->
                            Text(
                                text = "${value / 1024}K",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (value == shownTokens) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Text("Chat font size: ${"%.2f".format(Locale.US, chatFontScale)}x")
                    ThumbOnlySlider(value = chatFontScale, onValueChange = onChatFontScale, valueRange = 0.8f..1.8f)
                    Text("Code font size: ${"%.2f".format(Locale.US, codeFontScale)}x")
                    ThumbOnlySlider(value = codeFontScale, onValueChange = onCodeFontScale, valueRange = 0.8f..1.8f)
                    Text("Backend")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("cpu", "gpu", "npu").forEach { option ->
                            val selected = backendPreference == option
                            OutlinedButton(
                                onClick = { onBackendPreferenceChange(option) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (selected) "✓ ${option.uppercase(Locale.US)}" else option.uppercase(Locale.US))
                            }
                        }
                    }
                    Text(
                        "GPU is fastest on most phones. If the selected backend can't load, the app falls back to CPU.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speculative decoding / MTP")
                        Switch(
                            checked = speculativeDecodingEnabled,
                            onCheckedChange = onSpeculativeDecodingChange
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Think before answering")
                            Text(
                                "Models that support it reason first in Chat. Better answers, slower replies.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = thinkingEnabled, onCheckedChange = onThinkingChange)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Read replies aloud after voice input")
                            Text(
                                "When you talk with the mic, the answer is spoken back. The speaker button on any reply works either way.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = speakVoiceReplies, onCheckedChange = onSpeakVoiceRepliesChange)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Memory")
                            Text(
                                if (!memoryEnabled) "Off" else if (memories.isEmpty()) "Nothing saved yet" else "${memories.size} saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { settingsSubPage = "memory" }) { Text("Manage") }
                    }
                    HorizontalDivider()
                    OutlinedButton(onClick = { settingsSubPage = "privacy" }) {
                        Text("Open Privacy Policy")
                    }
                    OutlinedButton(onClick = { settingsSubPage = "licenses" }) {
                        Text("Open Third-Party Licenses")
                    }
                    OutlinedButton(onClick = { uriHandler.openUri("https://github.com/nikunjsingh93/ondevice-studio") }) {
                        Text("Check for updates on GitHub")
                    }
                }
            }

            Text(
                text = "Mochu AI $appVersionLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilesPane(
    state: BuilderUiState,
    onImportFiles: () -> Unit,
    onOpenFileInCode: (String) -> Unit,
    onOpenImagePreview: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onSaveFile: (String) -> Unit,
    onSaveZip: () -> Unit,
    onSavePwaZip: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var toolsMenuOpen by remember { mutableStateOf(false) }
    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete file?") },
            text = { Text("Delete \"$file\" from this chat workspace?") },
            confirmButton = {
                DangerButton("Delete") {
                    pendingDelete = null
                    onDeleteFile(file)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Project files", fontWeight = FontWeight.Bold)
                Text(
                    "Tap a file to open code. Use ⋮ for import/export tools.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { toolsMenuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Files tools")
                }
                DropdownMenu(expanded = toolsMenuOpen, onDismissRequest = { toolsMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Import files") },
                        onClick = { toolsMenuOpen = false; onImportFiles() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save ZIP") },
                        enabled = state.files.isNotEmpty(),
                        onClick = { toolsMenuOpen = false; onSaveZip() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save PWA-ready zip") },
                        enabled = state.files.isNotEmpty(),
                        onClick = { toolsMenuOpen = false; onSavePwaZip() }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            state.files.forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        file,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = isCodeFile(file) || isPreviewableImageFile(file)) {
                                when {
                                    isCodeFile(file) -> onOpenFileInCode(file)
                                    isPreviewableImageFile(file) -> onOpenImagePreview(file)
                                }
                            }
                    )
                    if (isCodeFile(file)) {
                        IconButton(onClick = { onOpenFileInCode(file) }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = "Open file")
                        }
                    }
                    IconButton(onClick = { onSaveFile(file) }) {
                        Icon(Icons.Outlined.Download, contentDescription = "Download file")
                    }
                    IconButton(onClick = { pendingDelete = file }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete file")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun RawPane(state: BuilderUiState) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Raw model response", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        AutoScrollingMonospaceText(
            text = state.lastRawResponse.ifBlank { "No model response yet." },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** What Chat remembers about the user: on/off, the saved facts, and a way to add or delete them. */
@Composable
private fun MemoryPage(
    memories: List<String>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use memory in Chat")
                Text(
                    "Chat uses these facts in every conversation. Say \"remember that…\" or \"forget…\" in a chat to change them. They stay on this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Add something to remember") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (draft.isNotBlank()) onAdd(draft)
                draft = ""
            }),
            trailingIcon = {
                IconButton(onClick = { if (draft.isNotBlank()) onAdd(draft); draft = "" }, enabled = draft.isNotBlank()) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add memory")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (memories.isEmpty()) {
            Text("Nothing saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        memories.asReversed().forEach { memory ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(memory, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    IconButton(onClick = { onDelete(memory) }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
