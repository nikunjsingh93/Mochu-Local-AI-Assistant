package com.nikunj.gemmabuilder

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private enum class AppTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Chat("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble),
    Browser("Browser", Icons.Outlined.Language, Icons.Rounded.Language),
    Studio("Studio", Icons.Outlined.Widgets, Icons.Rounded.Widgets),
    Models("Models", Icons.Outlined.Memory, Icons.Rounded.Memory)
}

/** Top-level layout: Chat / Browser / Studio / Models tabs, a history drawer, and full-screen overlays. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnDeviceApp(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    vm: BuilderViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.prepare(app) }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(app, uri)
    }
    val fileImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(app, uris)
    }
    var pendingExportPath by remember { mutableStateOf<String?>(null) }
    val fileSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val path = pendingExportPath
        if (uri != null && path != null) vm.exportFile(app, path, uri)
        pendingExportPath = null
    }
    val zipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportProjectZip(app, uri)
    }
    val pwaZipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportPwaReadyProjectZip(app, uri)
    }

    // Browser and Models are standalone tabs; otherwise the tab follows the active chat's space.
    var pinnedTab by rememberSaveable { mutableStateOf<AppTab?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val tab = pinnedTab ?: if (state.activeKind == CHAT_KIND_STUDIO) AppTab.Studio else AppTab.Chat
    LaunchedEffect(state.browserUrl) { if (state.browserUrl != null) pinnedTab = AppTab.Browser }
    val browser = rememberSaveable(saver = BrowserSession.Saver) { BrowserSession() }
    DisposableEffect(Unit) { onDispose { browser.destroy() } }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val openModels: () -> Unit = {
        vm.refreshModelDownloads(app)
        pinnedTab = AppTab.Models
    }

    BackHandler(enabled = state.previewFullscreen) { vm.togglePreviewFullscreen() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = pinnedTab != null && !settingsOpen && tab != AppTab.Browser) { pinnedTab = null }

    if (state.previewFullscreen) {
        FullscreenPreview(state = state)
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Opens only from the menu button; a swipe can still close it.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            HistoryDrawer(
                state = state,
                kind = if (tab == AppTab.Studio) CHAT_KIND_STUDIO else CHAT_KIND_ASSISTANT,
                onNew = { kind ->
                    pinnedTab = null
                    vm.newConversation(app, kind)
                    scope.launch { drawerState.close() }
                },
                onSelect = { id ->
                    pinnedTab = null
                    vm.selectConversation(app, id)
                    scope.launch { drawerState.close() }
                },
                onDelete = { id -> vm.deleteConversation(app, id) },
                onOpenSettings = {
                    settingsOpen = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        val keyboardOpen = WindowInsets.isImeVisible
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Hidden while typing so the composer sits directly on the keyboard.
                if (!keyboardOpen) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = {
                                    if (item != AppTab.Browser) browser.releaseFocus()
                                    when (item) {
                                        AppTab.Models -> openModels()
                                        AppTab.Browser -> pinnedTab = AppTab.Browser
                                        AppTab.Chat -> { pinnedTab = null; vm.openSpace(app, CHAT_KIND_ASSISTANT) }
                                        AppTab.Studio -> { pinnedTab = null; vm.openSpace(app, CHAT_KIND_STUDIO) }
                                    }
                                },
                                icon = { Icon(if (tab == item) item.selectedIcon else item.icon, contentDescription = null) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    AppTab.Chat -> AssistantChatScreen(
                        state = state,
                        onOpenDrawer = openDrawer,
                        onNewChat = { vm.newConversation(app, CHAT_KIND_ASSISTANT) },
                        onOpenModels = openModels,
                        onDownloadModel = { id ->
                            vm.downloadModel(app, id)
                            openModels()
                        },
                        onPromptChange = vm::setPrompt,
                        onSend = { vm.generate(app) },
                        onBuildInStudio = { prompt ->
                            vm.newConversation(app, CHAT_KIND_STUDIO)
                            vm.setPrompt(prompt)
                            vm.generate(app)
                        },
                        onToggleWeb = { vm.setWebSearchEnabled(app, it) },
                        onOpenLink = { url -> vm.openInBrowserTab(url) },
                        onStop = vm::stopGeneration
                    )
                    AppTab.Browser -> BrowserScreen(
                        session = browser,
                        requestedUrl = state.browserUrl,
                        onRequestedUrlOpened = vm::consumeBrowserUrl,
                        canAsk = state.modelReady && !state.isBusy,
                        onAskAboutPage = { page ->
                            pinnedTab = null
                            vm.askAboutPage(app, page)
                        }
                    )
                    AppTab.Studio -> StudioScreen(
                        state = state,
                        compact = true,
                        onOpenDrawer = openDrawer,
                        onNewProject = { vm.newConversation(app, CHAT_KIND_STUDIO) },
                        onImportFiles = { fileImporter.launch(arrayOf("*/*")) },
                        onPromptChange = vm::setPrompt,
                        onGenerate = { vm.generate(app) },
                        onOpenImagePreview = { path -> vm.openImagePreview(app, path) },
                        onTab = vm::setTab,
                        onRefresh = { vm.reloadPreview() },
                        onToggleFullscreen = { vm.togglePreviewFullscreen() },
                        onToggleWorkPanelCollapsed = { vm.toggleWorkPanelCollapsed() },
                        onOpenFileInCode = { path -> vm.openFileInCode(app, path) },
                        onDeleteFile = { path -> vm.deleteFile(app, path) },
                        onSaveFile = { path ->
                            pendingExportPath = path
                            fileSaver.launch(path.substringAfterLast('/'))
                        },
                        onSaveZip = { zipSaver.launch("OnDeviceStudioProject.zip") },
                        onSavePwaZip = { pwaZipSaver.launch("OnDeviceStudioPWA.zip") },
                        onStop = vm::stopGeneration
                    )
                    AppTab.Models -> ModelDownloadsScreen(
                        state = state,
                        onDownload = { vm.downloadModel(app, it) },
                        onCancel = { vm.cancelModelDownload(app, it) },
                        onUse = { vm.useDownloadedModel(app, it) },
                        onDelete = { vm.deleteDownloadedModel(app, it) },
                        onAllowMobileDataChange = { vm.setAllowMobileDataDownloads(app, it) },
                        onImportFile = { modelPicker.launch(arrayOf("*/*")) },
                        onDismiss = null
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        SettingsScreen(
            chatFontScale = state.chatFontScale,
            codeFontScale = state.codeFontScale,
            contextTokens = state.contextTokens,
            activeContextTokens = state.contextWindowTokens,
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            backendPreference = state.backendPreference,
            speculativeDecodingEnabled = state.speculativeDecodingEnabled,
            thinkingEnabled = state.thinkingEnabled,
            onChatFontScale = { vm.setChatFontScale(app, it) },
            onCodeFontScale = { vm.setCodeFontScale(app, it) },
            onContextTokensChange = { vm.setContextTokens(app, it) },
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onBackendPreferenceChange = { vm.setBackendPreference(app, it) },
            onSpeculativeDecodingChange = { vm.setSpeculativeDecodingEnabled(app, it) },
            onThinkingChange = { vm.setThinkingEnabled(app, it) },
            onDismiss = { settingsOpen = false }
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
    if (state.webConsentPending) {
        AlertDialog(
            onDismissRequest = { vm.answerWebConsent(app, false) },
            icon = { Icon(Icons.Rounded.Language, contentDescription = null) },
            title = { Text("Allow web search?") },
            text = {
                Text(
                    "When you search, your search words go to DuckDuckGo and the app loads the top pages from the internet. " +
                        "Your chats and the model stay on this phone, and web pages can only be read, never submitted to. " +
                        "Web search only runs when you turn it on or ask to search."
                )
            },
            confirmButton = { Button(onClick = { vm.answerWebConsent(app, true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { vm.answerWebConsent(app, false) }) { Text("Not now") } }
        )
    }
    state.previewImagePath?.let { path -> ImagePreviewOverlay(imagePath = path, onClose = vm::closeImagePreview) }
}

/** Chat history for the current space, with new-chat and settings entries. */
@Composable
private fun HistoryDrawer(
    state: BuilderUiState,
    kind: String,
    onNew: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val studio = kind == CHAT_KIND_STUDIO
    var pendingDelete by remember { mutableStateOf<ConversationInfo?>(null) }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp).fillMaxSize()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "OnDevice Studio",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { onNew(kind) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (studio) "New project" else "New chat")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (studio) "Projects" else "Recent chats",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            LazyColumn(Modifier.weight(1f)) {
                items(state.conversations.filter { it.kind == kind }, key = { it.id }) { chat ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(
                                    if (chat.title == "New chat") (if (studio) "New project" else "New chat") else chat.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatChatTimestamp(chat.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        selected = chat.id == state.activeConversationId,
                        onClick = { onSelect(chat.id) },
                        badge = {
                            IconButton(onClick = { pendingDelete = chat }, enabled = !state.isBusy) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
    pendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (studio) "Delete project?" else "Delete chat?") },
            text = {
                Text(
                    if (studio) "\"${chat.title}\" and its files will be deleted. This can't be undone."
                    else "\"${chat.title}\" will be deleted. This can't be undone."
                )
            },
            confirmButton = { DangerButton("Delete") { onDelete(chat.id); pendingDelete = null } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}
