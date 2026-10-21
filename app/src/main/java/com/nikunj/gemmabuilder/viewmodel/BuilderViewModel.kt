package com.nikunj.gemmabuilder

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Messages a fresh model session always sees verbatim; older ones are folded into the chat summary.
private const val RECENT_MESSAGES_KEPT = 6
private const val MAX_CHAT_IMAGES = 3
private const val CHAT_IMAGE_MAX_SIDE = 1024

class BuilderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState = _uiState.asStateFlow()

    // Shared with scheduled tasks so they reuse the loaded model instead of loading a second copy.
    private var engine: BuilderEngine = DemoBuilderEngine()
        set(value) {
            field = value
            EngineHolder.loaded = value.takeUnless { it is DemoBuilderEngine }
        }
    private var multimodalEngine: MediaPipeMultimodalEngine? = null
    private var speaker: Speaker? = null
    /** Set by [sendVoice] so the next Chat reply is read aloud. */
    private var voiceTurnPending = false
    /** A Chat message waiting for the web consent answer. */
    private var sendAfterConsent = false
    private var prepared = false
    private var conversations: MutableList<ChatConversation> = mutableListOf()
    private var appContext: Context? = null
    private var generationInProgress = false
        set(value) {
            val was = field
            field = value
            EngineHolder.foregroundBusy = value
            // Keep a reply going when the user leaves the app, and tell them when it's ready.
            appContext?.let { ctx ->
                if (value && !was) ReplyKeepAliveService.started(ctx)
                if (!value && was) ReplyKeepAliveService.finished(ctx, uiState.value.activeConversationId, uiState.value.messages.lastOrNull()?.takeIf { it.role == "assistant" }?.text)
            }
        }
    private var generationJob: Job? = null
    // Set by the Stop button; the streaming loop checks it between tokens.
    @Volatile private var stopRequested = false
    // "<chat id>|<workspace fingerprint>" the open model session was last in sync with.
    private var sessionFingerprint: String? = null
    // Context window the loaded model actually got (may be below the setting to fit in memory).
    private var activeTokenWindow = 8192
    // Decode tokens and time across every model call in the current turn (retries, continuations).
    private var turnOutputTokens = 0
    private var turnDecodeSeconds = 0.0

    fun prepare(context: Context) {
        if (prepared) return
        appContext = context.applicationContext
        prepared = true
        PDFBoxResourceLoader.init(context.applicationContext)

        conversations = loadConversations(context).toMutableList()
        if (conversations.isEmpty()) {
            val blank = newBlankConversation(CHAT_KIND_ASSISTANT)
            conversations.add(blank)
            saveConversationFile(context, blank)
        }
        // The app always opens in Chat, on the latest chat there.
        val active = conversations.filter { it.kind == CHAT_KIND_ASSISTANT }.maxByOrNull { it.updatedAt }
            ?: newBlankConversation(CHAT_KIND_ASSISTANT).also { conversations.add(it); saveConversationFile(context, it) }
        ensureProjectForConversation(context, active.projectKey, migrateLegacy = true)
        refreshWorkspace(context, active.id)
        _uiState.update {
            val hasModel = modelAvailable(context)
            it.copy(
                conversations = conversationInfos(),
                projectNames = savedProjectNames(context),
                activeConversationId = active.id,
                messages = active.messages,
                chatSummarized = active.summary.isNotBlank(),
                activeKind = active.kind,
                appReady = true,
                modelName = savedModelName(context),
                modelSupportsMultimodal = isLikelyMultimodalModel(savedModelName(context)),
                multimodalBackendReady = false,
                activeRoutingMode = "text",
                modelReady = hasModel,
                chatFontScale = savedChatFontScale(context),
                codeFontScale = savedCodeFontScale(context),
                contextTokens = savedModelContextTokens(context, savedModelName(context)),
                backendPreference = savedBackendPreference(context),
                speculativeDecodingEnabled = savedSpeculativeDecodingEnabled(context),
                thinkingEnabled = savedThinkingEnabled(context),
                thinkingLevel = savedThinkingLevel(context),
                kvCacheType = savedKvCacheType(context),
                memories = loadMemories(context),
                memoryEnabled = savedMemoryEnabled(context),
                webSearchEnabled = savedWebSearchEnabled(context),
                speakVoiceReplies = savedSpeakVoiceReplies(context),
                allowMobileDataDownloads = savedAllowMobileDataDownloads(context),
                status = if (hasModel) "${savedModelName(context)} found. Type a message and tap Send to auto-load." else "Download a model from the Models tab."
            )
        }
        cancelLegacySystemDownloads(context)
        learnFromLowMemoryKill(context)?.let { notice -> _uiState.update { it.copy(memoryNotice = notice) } }
        refreshModelDownloads(context)
        ensurePhotoSupport(context)
        refreshPredictedWindow(context)
        // Tasks: re-arm alarms (an app update clears them) and reload chats a scheduled task adds.
        TaskAlarms.rescheduleAll(context.applicationContext)
        viewModelScope.launch {
            AppEvents.chatAdded.collect { reloadConversations(context) }
        }
        // A scheduled task running for the open chat shows its steps and answer live.
        viewModelScope.launch {
            AppEvents.taskProgress.collect { progress ->
                if (generationInProgress) return@collect
                val forThisChat = progress != null && progress.chatId == uiState.value.activeConversationId
                _uiState.update {
                    if (forThisChat) it.copy(streamingReply = progress!!.text, liveActivity = progress.steps, status = progress.status)
                    else if (it.streamingReply != null && !it.isBusy) it.copy(streamingReply = null, liveActivity = emptyList())
                    else it
                }
            }
        }
        // The download service lives outside this ViewModel; mirror its progress into the UI state.
        viewModelScope.launch {
            ModelDownloadTracker.states.collect { refreshModelDownloads(context) }
        }
        viewModelScope.launch {
            ModelDownloadTracker.completed.collect { id ->
                refreshModelDownloads(context)
                if (generationInProgress || uiState.value.isBusy) return@collect
                // An image model is used from Chat when asked; it never replaces the chat model.
                if (MODEL_CATALOG.firstOrNull { it.id == id }?.isImage == true) return@collect
                val owner = MODEL_CATALOG.firstOrNull { it.visionId == id }
                if (owner != null) {
                    // Photo support finished: reload the model that uses it so it can see photos.
                    if (owner.fileName == savedModelName(context)) useDownloadedModel(context, owner.id)
                } else {
                    useDownloadedModel(context, id)
                    ensurePhotoSupport(context)
                }
            }
        }
    }

    fun dismissMemoryNotice() {
        _uiState.update { it.copy(memoryNotice = null) }
    }

    fun setAllowMobileDataDownloads(context: Context, allow: Boolean) {
        saveAllowMobileDataDownloads(context, allow)
        _uiState.update { it.copy(allowMobileDataDownloads = allow) }
    }

    fun downloadModel(context: Context, id: String) {
        val appContext = context.applicationContext
        val model = MODEL_CATALOG.firstOrNull { it.id == id } ?: return
        val needed = model.sizeBytes - partialBytes(appContext, model) + 200L * 1024 * 1024
        val free = runCatching { freeBytesForDownloads(appContext) }.getOrDefault(0L)
        if (free < needed) {
            ModelDownloadTracker.set(
                id,
                ModelDownloadState(DownloadPhase.Failed, message = "Not enough storage: needs ${formatBytes(needed)}, ${formatBytes(free)} free.")
            )
            return
        }
        runCatching { ModelDownloadService.start(appContext, id) }.onFailure {
            ModelDownloadTracker.set(id, ModelDownloadState(DownloadPhase.Failed, message = "Could not start download: ${it.message ?: "unknown error"}"))
        }
        // GGUF models that can see photos fetch their photo-support file right after.
        ensurePhotoSupport(appContext)
    }

    /** Downloads the photo-support file of every downloaded (or downloading) GGUF model that lacks one. */
    private fun ensurePhotoSupport(context: Context) {
        val appContext = context.applicationContext
        val live = ModelDownloadTracker.states.value
        MODEL_CATALOG.forEach { model ->
            val vision = model.visionId?.let { id -> MODEL_CATALOG.firstOrNull { it.id == id } } ?: return@forEach
            val modelWanted = downloadedModelFile(appContext, model).exists() || live[model.id]?.phase in ACTIVE_DOWNLOAD_PHASES
            if (modelWanted && !downloadedModelFile(appContext, vision).exists() && live[vision.id]?.phase !in ACTIVE_DOWNLOAD_PHASES) {
                runCatching { ModelDownloadService.start(appContext, vision.id) }
            }
        }
    }

    fun cancelModelDownload(context: Context, id: String) {
        val appContext = context.applicationContext
        val model = MODEL_CATALOG.firstOrNull { it.id == id } ?: return
        if (ModelDownloadTracker.states.value[id]?.phase in ACTIVE_DOWNLOAD_PHASES) {
            ModelDownloadService.cancel(appContext, id)
        } else {
            // Nothing running: just discard the paused or failed partial file.
            partialModelFile(appContext, model).delete()
            ModelDownloadTracker.set(id, null)
        }
        refreshModelDownloads(appContext)
    }

    fun useDownloadedModel(context: Context, id: String) {
        val appContext = context.applicationContext
        val model = MODEL_CATALOG.firstOrNull { it.id == id } ?: return
        if (model.isImage) {
            // Image models don't replace the chat model; this picks which one Chat draws with.
            saveActiveImageModel(appContext, model.id)
            refreshModelDownloads(appContext)
            return
        }
        val file = downloadedModelFile(appContext, model)
        if (!file.exists()) return
        if (generationInProgress || uiState.value.isBusy) {
            _uiState.update { it.copy(status = "Wait for the model to finish before switching models.") }
            return
        }
        engine.close()
        engine = DemoBuilderEngine()
        refreshContextUsage()
        sessionFingerprint = null
        saveModelName(appContext, model.fileName)
        saveActiveModelPath(appContext, file.absolutePath)
        setRemoteModelActive(appContext, false)
        _uiState.update {
            it.copy(
                modelName = model.fileName,
                modelSupportsMultimodal = isLikelyMultimodalModel(model.fileName),
                multimodalBackendReady = false,
                modelReady = true,
                gemmaLoaded = false,
                engineLabel = "Not loaded",
                activeModelPath = file.absolutePath,
                status = "${model.name} selected. Send a message to load it."
            )
        }
        refreshPredictedWindow(appContext)
    }

    fun deleteDownloadedModel(context: Context, id: String) {
        val appContext = context.applicationContext
        val model = MODEL_CATALOG.firstOrNull { it.id == id } ?: return
        val file = downloadedModelFile(appContext, model)
        val wasActive = savedActiveModelPath(appContext) == file.absolutePath
        if (wasActive) {
            if (generationInProgress || uiState.value.isBusy) {
                _uiState.update { it.copy(status = "Wait for the model to finish before deleting it.") }
                return
            }
            engine.close()
            engine = DemoBuilderEngine()
            refreshContextUsage()
            sessionFingerprint = null
            saveActiveModelPath(appContext, null)
            saveModelName(appContext, "")
        }
        file.delete()
        // Its photo-support file is no use without it.
        model.visionId?.let { visionId -> MODEL_CATALOG.firstOrNull { it.id == visionId }?.let { downloadedModelFile(appContext, it).delete() } }
        if (wasActive) {
            _uiState.update {
                it.copy(
                    modelName = savedModelName(appContext),
                    modelReady = modelAvailable(appContext),
                    gemmaLoaded = false,
                    engineLabel = "Not loaded",
                    status = "Deleted ${model.name}."
                )
            }
        }
        refreshModelDownloads(appContext)
        refreshPredictedWindow(appContext)
    }

    fun refreshModelDownloads(context: Context) {
        val appContext = context.applicationContext
        val live = ModelDownloadTracker.states.value
        val states = MODEL_CATALOG.associate { model ->
            model.id to (live[model.id] ?: storedDownloadState(appContext, model))
        }
        val contexts = MODEL_CATALOG.associate { it.fileName to savedModelContextTokens(appContext, it.fileName) } +
            listOfNotNull(savedImportedModelName(appContext)).associateWith { savedModelContextTokens(appContext, it) }
        val imageSettings = MODEL_CATALOG.filter { it.isImage }.associate { it.fileName to savedImageSettings(appContext, it) }
        _uiState.update {
            it.copy(
                modelDownloads = states, activeModelPath = if (remoteModelActive(appContext)) null else savedActiveModelPath(appContext), modelContextTokens = contexts,
                remoteModel = savedRemoteModelConfig(appContext)?.takeIf { remoteModelActive(appContext) },
                importedModelName = savedImportedModelName(appContext),
                activeImageModelId = activeImageModel(appContext)?.id, imageSettings = imageSettings
            )
        }
    }

    /** State for a model with no download in progress, from what is on disk. */
    private fun storedDownloadState(context: Context, model: CatalogModel): ModelDownloadState {
        if (downloadedModelFile(context, model).exists()) return ModelDownloadState(DownloadPhase.Downloaded, 1f)
        val partial = partialBytes(context, model)
        if (partial <= 0) return ModelDownloadState()
        return ModelDownloadState(
            phase = DownloadPhase.NotDownloaded,
            progress = (partial.toFloat() / model.sizeBytes).coerceIn(0f, 1f),
            message = "Paused · ${formatBytes(partial)} of ${formatBytes(model.sizeBytes)}",
            partialBytes = partial
        )
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(sidebarOpen = !it.sidebarOpen) }
    }

    fun setChatFontScale(context: Context, scale: Float) {
        saveChatFontScale(context, scale)
        _uiState.update { it.copy(chatFontScale = scale) }
    }

    fun setCodeFontScale(context: Context, scale: Float) {
        saveCodeFontScale(context, scale)
        _uiState.update { it.copy(codeFontScale = scale) }
    }

    /** The Models page's context slider for one model. */
    fun setModelContextTokens(context: Context, modelFileName: String, tokens: Int) {
        saveModelContextTokens(context, modelFileName, tokens)
        _uiState.update { it.copy(modelContextTokens = it.modelContextTokens + (modelFileName to savedModelContextTokens(context, modelFileName))) }
        if (modelFileName == savedModelName(context)) setContextTokens(context, savedModelContextTokens(context, modelFileName))
    }

    fun setContextTokens(context: Context, tokens: Int) {
        if (tokens == uiState.value.contextTokens) return
        saveModelContextTokens(context, savedModelName(context), tokens)
        val saved = savedModelContextTokens(context, savedModelName(context))
        // The window is fixed when the model loads, so a loaded model has to be reloaded.
        val needsReload = uiState.value.gemmaLoaded
        _uiState.update {
            if (needsReload) {
                it.copy(
                    contextTokens = saved,
                    gemmaLoaded = false,
                    engineLabel = "Not loaded",
                    contextWindowTokens = null,
                    contextUsedTokens = null,
                    status = "Context window updated. Send a message to reload model."
                )
            } else {
                it.copy(contextTokens = saved)
            }
        }
        refreshWorkspace(context)
        refreshPredictedWindow(context)
    }

    fun setBackendPreference(context: Context, backendPreference: String) {
        saveBackendPreference(context, backendPreference)
        _uiState.update {
            it.copy(
                backendPreference = savedBackendPreference(context),
                gemmaLoaded = false,
                engineLabel = "Not loaded",
                status = "Backend updated. Send a message to reload model."
            )
        }
    }

    fun setThinkingEnabled(context: Context, enabled: Boolean) {
        saveThinkingEnabled(context, enabled)
        _uiState.update { it.copy(thinkingEnabled = enabled) }
    }

    /** Chat's thinking button: 0 turns thinking off, 1–3 turn it on at that level. */
    fun setThinkingLevel(context: Context, level: Int) {
        if (level <= 0) return setThinkingEnabled(context, false)
        saveThinkingLevel(context, level)
        saveThinkingEnabled(context, true)
        _uiState.update { it.copy(thinkingEnabled = true, thinkingLevel = savedThinkingLevel(context)) }
    }

    /** KV cache type for GGUF models; a loaded GGUF model reloads on the next message. */
    fun setKvCacheType(context: Context, type: KvCacheType) {
        if (type == uiState.value.kvCacheType) return
        saveKvCacheType(context, type)
        val reload = uiState.value.gemmaLoaded && isGgufModel(savedModelName(context))
        _uiState.update {
            if (reload) it.copy(kvCacheType = type, gemmaLoaded = false, engineLabel = "Not loaded", contextWindowTokens = null, contextUsedTokens = null,
                status = "KV cache updated. Send a message to reload the model.")
            else it.copy(kvCacheType = type)
        }
    }

    fun setSpeculativeDecodingEnabled(context: Context, enabled: Boolean) {
        saveSpeculativeDecodingEnabled(context, enabled)
        _uiState.update {
            it.copy(
                speculativeDecodingEnabled = savedSpeculativeDecodingEnabled(context),
                gemmaLoaded = false,
                engineLabel = "Not loaded",
                status = "Speculative decoding setting updated. Send a message to reload model."
            )
        }
    }

    /** Switches between the Chat and Studio spaces, reopening that space's most recent chat. */
    fun openSpace(context: Context, kind: String) {
        if (uiState.value.activeKind == kind) return
        val recent = conversations.filter { it.kind == kind }.maxByOrNull { it.updatedAt }
        if (recent != null) selectConversation(context, recent.id) else newConversation(context, kind)
    }

    /** Starts a chat; in Code, [projectKey] puts it in an existing project (sharing its files). */
    fun newConversation(context: Context, kind: String = uiState.value.activeKind, projectKey: String? = null) {
        val conv = newBlankConversation(kind).let { if (projectKey != null) it.copy(projectId = projectKey) else it }
        conversations.add(0, conv)
        saveConversationFile(context, conv)
        ensureProjectForConversation(context, conv.projectKey, migrateLegacy = false)
        refreshWorkspace(context, conv.id)
        _uiState.update {
            it.copy(
                activeConversationId = conv.id,
                messages = emptyList(),
                chatSummarized = false,
                activeKind = conv.kind,
                contextUsedTokens = it.contextWindowTokens?.let { 0 },
                prompt = "",
                conversations = conversationInfos(),
                sidebarOpen = false,
                tab = 0,
                previewVersion = it.previewVersion + 1,
                lastWrittenPaths = emptyList(),
                lastRawResponse = "",
                status = "New chat started."
            )
        }
    }

    fun selectConversation(context: Context, id: String) {
        val conv = conversations.firstOrNull { it.id == id } ?: return
        ensureProjectForConversation(context, conv.projectKey, migrateLegacy = false)
        refreshWorkspace(context, conv.id)
        _uiState.update {
            it.copy(
                activeConversationId = conv.id,
                messages = conv.messages,
                chatSummarized = conv.summary.isNotBlank(),
                activeKind = conv.kind,
                contextUsedTokens = it.contextWindowTokens?.let { 0 },
                prompt = "",
                conversations = conversationInfos(),
                sidebarOpen = false,
                tab = 0,
                previewVersion = it.previewVersion + 1,
                status = "Opened ${conv.title}."
            )
        }
        refreshContextUsage()
    }

    fun deleteConversation(context: Context, id: String) {
        if (generationInProgress || uiState.value.isBusy) {
            _uiState.update { it.copy(status = "Wait for the model to finish before deleting a chat.") }
            return
        }
        val deleted = conversations.firstOrNull { it.id == id } ?: return
        conversations = conversations.filterNot { it.id == id }.toMutableList()
        File(chatsRoot(context), "$id.chat").delete()
        // A project's files go only with its last chat.
        if (conversations.none { it.projectKey == deleted.projectKey }) File(projectsRoot(context), deleted.projectKey).deleteRecursively()
        // Stay in the same space: fall back to that space's newest chat, or a fresh one.
        if (conversations.none { it.kind == deleted.kind }) {
            val fresh = newBlankConversation(deleted.kind)
            conversations.add(fresh)
            saveConversationFile(context, fresh)
            ensureProjectForConversation(context, fresh.projectKey, migrateLegacy = false)
        }
        val next = if (uiState.value.activeConversationId == id) {
            conversations.filter { it.kind == deleted.kind }.maxByOrNull { it.updatedAt } ?: conversations.first()
        } else {
            conversations.firstOrNull { it.id == uiState.value.activeConversationId } ?: conversations.maxByOrNull { it.updatedAt } ?: conversations.first()
        }
        ensureProjectForConversation(context, next.projectKey, migrateLegacy = false)
        refreshWorkspace(context, next.id)
        _uiState.update {
            it.copy(
                activeConversationId = next.id,
                messages = next.messages,
                chatSummarized = next.summary.isNotBlank(),
                activeKind = next.kind,
                contextUsedTokens = it.contextWindowTokens?.let { 0 },
                prompt = "",
                conversations = conversationInfos(),
                sidebarOpen = true,
                tab = 0,
                previewVersion = it.previewVersion + 1,
                status = "Deleted ${deleted.title}."
            )
        }
        refreshContextUsage()
    }

    fun setPrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun setTab(tab: Int) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun reloadPreview() {
        _uiState.update { it.copy(previewVersion = it.previewVersion + 1) }
    }

    fun togglePreviewFullscreen() {
        _uiState.update { it.copy(previewFullscreen = !it.previewFullscreen, previewVersion = it.previewVersion + 1) }
    }

    fun toggleWorkPanelCollapsed() {
        _uiState.update { it.copy(workPanelCollapsed = !it.workPanelCollapsed) }
    }

    fun openFileInCode(context: Context, relativePath: String) {
        val result = runCatching {
            val root = activeProjectRoot(context)
            val file = safeResolve(root, relativePath)
            require(file.exists()) { "File does not exist: $relativePath" }
            val text = file.readText()
            _uiState.update {
                it.copy(
                    selectedCodePath = relativePath,
                    currentCode = text,
                    tab = 1,
                    status = "Opened $relativePath"
                )
            }
        }
        if (result.isFailure) {
            _uiState.update { it.copy(status = "Open file failed: ${result.exceptionOrNull()?.message ?: "unknown error"}") }
        }
    }

    fun deleteFile(context: Context, relativePath: String) {
        val result = runCatching {
            val root = activeProjectRoot(context)
            val file = safeResolve(root, relativePath)
            require(file.exists()) { "File does not exist: $relativePath" }
            require(file.delete()) { "Could not delete file: $relativePath" }
            refreshWorkspace(context)
        }
        _uiState.update {
            it.copy(
                status = if (result.isSuccess) "Deleted $relativePath" else "Delete failed: ${result.exceptionOrNull()?.message ?: "unknown error"}",
                previewVersion = if (relativePath.equals("index.html", ignoreCase = true) && result.isSuccess) it.previewVersion + 1 else it.previewVersion
            )
        }
    }

    fun importModel(context: Context, uri: Uri) {
        viewModelScope.launch {
            val importedName = displayNameForUri(context.contentResolver, uri).ifBlank { "Gemma model" }
            val isSupported = importedName.lowercase(Locale.US).endsWith(".litertlm") || isGgufModel(importedName)
            if (!isSupported) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = "Only .litertlm and .gguf models are supported. Selected: $importedName",
                        messages = it.messages + ChatMessage("assistant", "Model import blocked: only .litertlm or .gguf files are allowed. Selected: $importedName")
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isBusy = true,
                    status = "Importing model into app storage...",
                    messages = it.messages + ChatMessage("assistant", "Importing model into app storage...")
                )
            }
            // The loaded engine may be memory-mapping the file we are about to overwrite or delete.
            engine.close()
            engine = DemoBuilderEngine()
            refreshContextUsage()
            sessionFingerprint = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    copyModel(context.contentResolver, uri, modelFileFor(context, importedName))
                    saveModelName(context, importedName)
                    saveImportedModelName(context, importedName)
                    saveActiveModelPath(context, null)
                    setRemoteModelActive(context, false)
                    deleteOtherModelFormats(context, importedName)
                }
            }
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isBusy = false,
                        modelName = importedName,
                        modelSupportsMultimodal = isLikelyMultimodalModel(importedName),
                        multimodalBackendReady = false,
                        activeRoutingMode = "text",
                        modelReady = true,
                        gemmaLoaded = false,
                        engineLabel = "Not loaded",
                        messages = it.messages + ChatMessage("assistant", "$importedName imported. Send a message to load it automatically."),
                        activeModelPath = null,
                        remoteModel = null,
                        importedModelName = importedName,
                        status = "$importedName imported. Type a message and tap Send to auto-load."
                    )
                } else {
                    it.copy(
                        isBusy = false,
                        messages = it.messages + ChatMessage("assistant", "Model import failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"),
                        status = "Model import failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    )
                }
            }
            refreshPredictedWindow(context)
        }
    }

    fun importFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, status = "Importing file(s) into this chat...") }
            val importedNames = uris.map { uri ->
                sanitizeImportedFileName(displayNameForUri(context.contentResolver, uri, fallback = "imported-file"))
            }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val root = activeProjectRoot(context)
                    root.mkdirs()
                    uris.forEachIndexed { index, uri ->
                        val name = importedNames.getOrElse(index) { "imported-file" }
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Could not open selected file: $name" }
                            safeResolve(root, name).outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
            refreshWorkspace(context)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isBusy = false,
                        status = "Imported ${uris.size} file(s) into this chat workspace.",
                        tab = 0,
                        previewVersion = it.previewVersion + 1,
                        lastWrittenPaths = importedNames,
                        pendingAttachments = (it.pendingAttachments + importedNames).distinct().takeLast(12)
                    )
                } else {
                    it.copy(
                        isBusy = false,
                        status = "File import failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    /** Copies a whole folder (and its subfolders) into the project, skipping build output and dependencies. */
    fun importFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, status = "Importing folder...") }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val root = activeProjectRoot(context)
                    // A starter page would sit on top of the imported project.
                    File(root, "index.html").takeIf { isStarterPage(it) }?.delete()
                    copyDocumentTree(context, treeUri, root)
                }
            }
            refreshWorkspace(context)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    status = result.fold({ n -> "Imported $n file(s) from the folder." }, { e -> "Folder import failed: ${e.message ?: "unknown error"}" }),
                    tab = 2,
                    previewVersion = it.previewVersion + 1
                )
            }
        }
    }

    fun openImagePreview(context: Context, relativePath: String) {
        viewModelScope.launch {
            val result = runCatching {
                val root = activeProjectRoot(context)
                val file = safeResolve(root, relativePath)
                require(file.exists()) { "Image not found: $relativePath" }
                require(isPreviewableImageFile(relativePath)) { "Not an image file: $relativePath" }
                file.absolutePath
            }
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(previewImagePath = result.getOrNull())
                } else {
                    it.copy(status = "Image preview failed: ${result.exceptionOrNull()?.message ?: "unknown error"}")
                }
            }
        }
    }

    fun closeImagePreview() {
        _uiState.update { it.copy(previewImagePath = null) }
    }

    fun loadGemma(context: Context) {
        viewModelScope.launch { loadGemmaInternal(context.applicationContext) }
    }

    /** Deletes the imported model; if it was in use, switches to a downloaded model when there is one. */
    fun deleteImportedModel(context: Context) {
        if (generationInProgress || uiState.value.isBusy) return
        val appContext = context.applicationContext
        val name = savedImportedModelName(appContext) ?: return
        val wasActive = savedModelName(appContext) == name && savedActiveModelPath(appContext) == null && !remoteModelActive(appContext)
        if (wasActive) {
            engine.close()
            engine = DemoBuilderEngine()
            sessionFingerprint = null
        }
        modelFileFor(appContext, name).delete()
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("importedModelName").apply()
        if (wasActive) {
            val next = MODEL_CATALOG.firstOrNull { !it.isImage && !it.hidden && downloadedModelFile(appContext, it).exists() }
            if (next != null) {
                useDownloadedModel(appContext, next.id)
            } else {
                _uiState.update { it.copy(modelReady = false, gemmaLoaded = false, engineLabel = "Not loaded", status = "Deleted $name. Download a model to keep chatting.") }
            }
        }
        _uiState.update { it.copy(importedModelName = null, status = "Deleted $name.") }
        refreshModelDownloads(appContext)
    }

    /** Switches back to the model imported from a file. */
    fun useImportedModel(context: Context) {
        if (generationInProgress || uiState.value.isBusy) return
        val appContext = context.applicationContext
        val name = savedImportedModelName(appContext) ?: return
        engine.close()
        engine = DemoBuilderEngine()
        sessionFingerprint = null
        refreshContextUsage()
        saveModelName(appContext, name)
        saveActiveModelPath(appContext, null)
        setRemoteModelActive(appContext, false)
        _uiState.update {
            it.copy(
                modelName = name, modelReady = true, gemmaLoaded = false, engineLabel = "Not loaded", activeModelPath = null, remoteModel = null,
                modelSupportsMultimodal = isLikelyMultimodalModel(name), status = "$name selected. Send a message to load it."
            )
        }
        refreshModelDownloads(appContext)
    }

    /** Switches Chat and Code to a model on the user's own server. */
    fun useRemoteModel(context: Context, config: RemoteModelConfig) {
        if (generationInProgress || uiState.value.isBusy) return
        val appContext = context.applicationContext
        saveRemoteModelConfig(appContext, config)
        setRemoteModelActive(appContext, true)
        engine.close()
        engine = DemoBuilderEngine()
        sessionFingerprint = null
        refreshContextUsage()
        _uiState.update {
            it.copy(
                modelName = config.model, modelReady = true, gemmaLoaded = false, engineLabel = "Not loaded",
                modelSupportsMultimodal = false, multimodalBackendReady = false, activeModelPath = null,
                remoteModel = config, status = "${config.model} on your server selected. Send a message to use it."
            )
        }
    }

    /** Back to the on-phone model from the user's own server. */
    fun stopUsingRemoteModel(context: Context) {
        if (generationInProgress || uiState.value.isBusy) return
        val appContext = context.applicationContext
        setRemoteModelActive(appContext, false)
        engine.close()
        engine = DemoBuilderEngine()
        sessionFingerprint = null
        _uiState.update {
            it.copy(
                modelName = savedModelName(appContext), modelReady = modelAvailable(appContext), gemmaLoaded = false, engineLabel = "Not loaded",
                activeModelPath = savedActiveModelPath(appContext), remoteModel = null, status = "Using the model on this phone."
            )
        }
    }

    private suspend fun loadGemmaInternal(context: Context): Boolean {
        // A model on the user's own server: nothing to load into memory here.
        savedRemoteModelConfig(context)?.takeIf { remoteModelActive(context) }?.let { config ->
            engine.close()
            engine = RemoteEngine(config)
            sessionFingerprint = null
            val window = withContext(Dispatchers.IO) { detectRemoteContext(config) } ?: 32_768
            activeTokenWindow = window
            _uiState.update {
                it.copy(
                    isBusy = false, gemmaLoaded = true, modelName = config.model, engineLabel = "${config.model} · your server",
                    contextWindowTokens = window, contextUsedTokens = estimatedHistoryTokens().coerceAtMost(window),
                    status = "Using ${config.model} on your server..."
                )
            }
            return true
        }
        val file = modelFile(context)
        if (!file.exists()) {
            _uiState.update { it.copy(isBusy = false, status = "Import a .litertlm or .gguf model from the ⋮ menu first.") }
            return false
        }
        _uiState.update { it.copy(isBusy = true, modelLoading = true, status = "Loading model. First load can take several seconds...") }
        // Free the previous model before loading the next so two copies never sit in RAM/GPU memory.
        engine.close()
        engine = DemoBuilderEngine()
        refreshContextUsage()
        sessionFingerprint = null
        val requestedTokens = savedModelContextTokens(context, savedModelName(context))
        val maxNumTokens = safeTokenWindow(context, file, requestedTokens)
        activeTokenWindow = maxNumTokens
        val windowNote = ""
        // Returns (where it runs, optional status note).
        val result = runCatching {
            if (isGgufModel(savedModelName(context))) {
                val newEngine = LlamaCppEngine(context.applicationContext, file.absolutePath, maxNumTokens, visionFileFor(context, savedModelName(context))?.absolutePath)
                newEngine.load()
                engine = newEngine
                "llama.cpp CPU" to " (${newEngine.description})"
            } else {
                val newEngine = LiteRtGemmaEngine(
                    context = context.applicationContext,
                    modelPath = file.absolutePath,
                    backendPreference = uiState.value.backendPreference,
                    speculativeDecodingEnabled = uiState.value.speculativeDecodingEnabled,
                    maxNumTokens = maxNumTokens
                )
                newEngine.load()
                engine = newEngine
                ensureMultimodalBackend(context)
                newEngine.activeBackend to newEngine.fallbackReason?.let { reason -> " (fallback: $reason)" }.orEmpty()
            }
        }
        if (result.isSuccess) recordLoadedModel(context, file, maxNumTokens)
        _uiState.update { it.copy(modelLoading = false) }
        _uiState.update {
            val loaded = result.getOrNull()
            if (loaded != null) {
                val (backendLabel, note) = loaded
                it.copy(
                    isBusy = false,
                    gemmaLoaded = true,
                    engineLabel = "${savedModelName(context)} · $backendLabel",
                    modelName = savedModelName(context),
                    modelSupportsMultimodal = isLikelyMultimodalModel(savedModelName(context)),
                    multimodalBackendReady = multimodalEngine?.isReady() == true,
                    activeRoutingMode = "text",
                    status = "${savedModelName(context)} loaded on $backendLabel$note$windowNote. Sending message...",
                    contextWindowTokens = maxNumTokens,
                    contextUsedTokens = estimatedHistoryTokens().coerceAtMost(maxNumTokens)
                )
            } else {
                it.copy(
                    isBusy = false,
                    gemmaLoaded = false,
                    engineLabel = "Not loaded",
                    status = "Model load failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                )
            }
        }
        return result.isSuccess
    }

    private fun ensureMultimodalBackend(context: Context) {
        if (!uiState.value.modelSupportsMultimodal) return
        if (multimodalEngine?.isReady() == true) return
        multimodalEngine = MediaPipeMultimodalEngine(context.applicationContext, modelFile(context).absolutePath).apply {
            load()
        }
    }

    /** Sends text spoken into the mic; the reply is read aloud when that setting is on. */
    fun sendVoice(context: Context, text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(prompt = text) }
        // Only Chat replies are read aloud; Code replies are mostly code.
        voiceTurnPending = uiState.value.activeKind == CHAT_KIND_ASSISTANT
        // Start the speech engine now so it is ready when the reply finishes.
        if (voiceTurnPending && speaker == null) attachSpeaker(Speaker(context))
        generate(context)
    }

    fun generate(context: Context) {
        speaker?.stop()
        // A request the user sends starts the automatic-fix count over; a fix the app sends counts up.
        if (!autoFixInFlight) autoFixRounds = 0
        autoFixInFlight = false
        if (EngineHolder.backgroundRunning) {
            _uiState.update { it.copy(status = "A scheduled task is using the model. Try again in a moment.") }
            Toast.makeText(context, "A scheduled task is running. Try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        val typed = uiState.value.prompt.trim()
        // In Chat a photo alone is enough to send.
        val chatWithPhoto = uiState.value.activeKind == CHAT_KIND_ASSISTANT && uiState.value.chatImages.isNotEmpty()
        val chatWithDocs = uiState.value.activeKind == CHAT_KIND_ASSISTANT && uiState.value.chatDocs.isNotEmpty()
        val prompt = typed.ifBlank { if (chatWithDocs) "Summarize ${if (uiState.value.chatDocs.size == 1) "this document" else "these documents"}." else if (chatWithPhoto) "What's in this photo?" else "" }
        if (prompt.isBlank() || uiState.value.isBusy || generationInProgress) return
        generationInProgress = true
        if (uiState.value.activeKind == CHAT_KIND_ASSISTANT) {
            // First web question: ask before anything goes online, then send.
            val wantsWeb = if (uiState.value.webSearchEnabled) chatNeedsWeb(prompt) else looksLikeSearchRequest(prompt)
            if (wantsWeb && uiState.value.chatImages.isEmpty() && !webConsentGiven(context)) {
                generationInProgress = false
                sendAfterConsent = true
                _uiState.update { it.copy(webConsentPending = true) }
                return
            }
            generateAssistantReply(context, prompt)
            return
        }

        generationJob = viewModelScope.launch {
            try {
                if (!uiState.value.modelReady) {
                    _uiState.update { it.copy(status = "Download a model from the Models tab first.") }
                    return@launch
                }
                val pendingAttachments = uiState.value.pendingAttachments
                val userMessage = ChatMessage("user", prompt, attachments = pendingAttachments)
                val hasImageOrAudioAttachment = pendingAttachments.any { path ->
                    isPreviewableImageFile(path) || isAudioFilePath(path)
                }
                val multimodalRoute = hasImageOrAudioAttachment &&
                    uiState.value.modelSupportsMultimodal &&
                    (multimodalEngine?.isReady() == true)
                val pendingMessages = uiState.value.messages + userMessage
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        prompt = "",
                        status = "Thinking...",
                        messages = pendingMessages,
                        streamingCode = "",
                        lastRawResponse = "",
                        tab = 1,
                        generationMetrics = null,
                        pendingAttachments = emptyList(),
                        activeRoutingMode = if (multimodalRoute) "multimodal" else "text"
                    )
                }
                persistActiveConversation(context, pendingMessages)
                // "Make a React app" / "write a Python script" on a new project starts from a working template.
                withContext(Dispatchers.IO) { seedProjectForRequest(activeProjectRoot(context), prompt) }?.let { kind ->
                    refreshWorkspace(context)
                    _uiState.update { it.copy(status = "Set up a ${if (kind == ProjectKind.React) "React + TypeScript" else "Python"} project…") }
                }
                // The request shows first, then "Loading model" under it, as in Chat.
                if (!uiState.value.gemmaLoaded) {
                    val loaded = loadGemmaInternal(context.applicationContext)
                    if (!loaded) {
                        _uiState.update { it.copy(isBusy = false) }
                        return@launch
                    }
                    _uiState.update { it.copy(isBusy = true, status = "Thinking...") }
                }
                turnOutputTokens = 0
                turnDecodeSeconds = 0.0
                val generationStartedAtMs = System.currentTimeMillis()
                var firstTokenAtMs: Long? = null

                if (multimodalRoute) {
                    val multimodalReply = withContext(Dispatchers.IO) {
                        multimodalEngine?.generateResponse(
                            prompt = prompt,
                            root = activeProjectRoot(context),
                            attachmentPaths = pendingAttachments
                        ).orEmpty()
                    }
                    if (multimodalReply.isNotBlank()) {
                        val metrics = buildGenerationMetrics(generationStartedAtMs, firstTokenAtMs)
                        val assistantMessage = ChatMessage("assistant", multimodalReply, statsInline = metrics)
                        val finalMessages = uiState.value.messages + assistantMessage
                        persistActiveConversation(context, finalMessages)
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                status = "Multimodal response ready.",
                                messages = finalMessages,
                                streamingCode = "",
                                lastRawResponse = multimodalReply,
                                tab = 0,
                                generationMetrics = metrics
                            )
                        }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(status = "Multimodal route unavailable for this input; falling back to text route.")
                    }
                }

                // Keep the chat's model session (and its KV cache) when nothing changed the workspace behind
                // the model's back; otherwise start over from a full project snapshot.
                val sessionKey = uiState.value.activeConversationId.orEmpty()
                val workspaceKey = withContext(Dispatchers.IO) {
                    "$sessionKey|${workspaceFingerprint(activeProjectRoot(context))}"
                }
                var cachedSnapshotPrompt: String? = null
                suspend fun snapshotPrompt(): String = cachedSnapshotPrompt ?: run {
                    // A fresh session only sees recent messages, so fold anything older into the summary first.
                    compactHistoryIfNeeded(context, pendingMessages)
                    withContext(Dispatchers.IO) { buildPrompt(context, prompt, pendingMessages, userMessage.attachments) }
                }.also { cachedSnapshotPrompt = it }
                val turnPrompt = buildTurnPrompt(prompt)
                val continuesSession = workspaceKey == sessionFingerprint && hasRoomForTurn(sessionKey, turnPrompt)
                if (!continuesSession) engine.resetSession()
                val basePrompt = if (continuesSession) turnPrompt else snapshotPrompt()
                var responseText = ""
                var actions: List<WriteFileAction> = emptyList()
                var assistantReply: String? = null
                var lastError: Throwable? = null
                val maxAttempts = 4
                var retryNote: String? = null
                var editedPaths = emptySet<String>()
                var editCount = 0
                // Reasoning models (Bonsai, Qwen3) think first; shown live and kept with the reply.
                var studioThinking = ""
                val window = uiState.value.contextWindowTokens
                val baseUsed = if (continuesSession) uiState.value.contextUsedTokens ?: 0 else 0
                // False when the app changed the model's files (e.g. merged them), so its history is out of date.
                var actionsMatchModel = true
                // Edited files already existed, so they skip the "is this a complete page" check.
                fun readyToWrite(action: WriteFileAction) = action.path in editedPaths || isCompleteEnoughForWriting(action)

                for (attempt in 1..maxAttempts) {
                    val output = StringBuilder()
                    var lastUiUpdate = 0L
                    val attemptPrompt = if (attempt == 1) {
                        basePrompt
                    } else {
                        // The failed attempt is still in the session's history; retry from a clean snapshot.
                        engine.resetSession()
                        """
${snapshotPrompt()}

RETRY ATTEMPT $attempt OF $maxAttempts:
${retryNote ?: "Your previous response was incomplete. If the user asked a question, return one complete XML reply action. If file changes are needed, return complete XML write_file or edit_file action(s). Close every opened tag. Do not stream explanations outside XML."}
                        """.trimIndent()
                    }

                    _uiState.update {
                        it.copy(
                            status = if (attempt == 1) "Thinking..." else "Trying again...",
                            streamingCode = if (attempt == 1) "" else it.streamingCode
                        )
                    }

                    val result = runCatching {
                        engine.generate(sessionKey, attemptPrompt).onCompletion { absorbGenerationStats() }.collect { chunk ->
                            if (firstTokenAtMs == null && chunk.isNotBlank()) {
                                firstTokenAtMs = System.currentTimeMillis()
                            }
                            output.append(chunk)
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 220L || output.length < 1000) {
                                lastUiUpdate = now
                                val streamed = output.toString()
                                val split = splitThinking(streamed)
                                _uiState.update {
                                    it.copy(
                                        // Name the file being written, e.g. "Writing src/App.tsx...".
                                        status = if (split.stillThinking) "Thinking..." else
                                            Regex("<path>\\s*([^<]+?)\\s*</path>").findAll(split.answer).lastOrNull()?.groupValues?.get(1)
                                                ?.let { path -> "Writing $path..." } ?: "Writing code...",
                                        lastRawResponse = streamed,
                                        streamingCode = extractStreamingCode(split.answer),
                                        streamingThinking = split.thinking.takeIf { text -> split.stillThinking || text.isNotEmpty() },
                                        // The meter rises while the model writes (~4 chars a token).
                                        contextUsedTokens = window?.let { w -> (baseUsed + (attemptPrompt.length + streamed.length) / 4).coerceAtMost(w) }
                                    )
                                }
                            }
                        }
                    }

                    if (result.isFailure) {
                        lastError = result.exceptionOrNull()
                        if (attempt < maxAttempts) continue
                    }

                    responseText = continueRawResponseIfNeeded(
                        sessionKey = sessionKey,
                        initialResponse = output.toString(),
                        firstTokenAtMs = { firstTokenAtMs },
                        setFirstTokenAtMs = { firstTokenAtMs = it }
                    )
                    // Parse only the answer: reasoning can mention tags like <action> that aren't actions.
                    splitThinking(responseText).let { split ->
                        if (split.thinking.isNotEmpty()) studioThinking = split.thinking
                        responseText = split.answer
                    }
                    _uiState.update {
                        it.copy(
                            status = "Checking generated file...",
                            lastRawResponse = responseText,
                            streamingCode = extractStreamingCode(responseText)
                        )
                    }
                    val editActions = parseEditFileActions(responseText)
                    // With edits present, take write_file actions literally rather than guessing at HTML.
                    val rawWrites = if (editActions.isEmpty()) recoverWriteFileActions(responseText) else parseWriteFileActions(responseText)
                    val writes = normalizeWriteActions(rawWrites)
                    val edited = withContext(Dispatchers.IO) { applyEditActions(activeProjectRoot(context), editActions, writes) }
                    if (edited.missing != null) {
                        val (path, snippet) = edited.missing
                        retryNote = "Your edit_file <find> text was not found in $path:\n${snippet.take(300)}\n" +
                            "Return write_file with the complete updated $path instead."
                        actions = emptyList()
                        assistantReply = null
                        if (attempt < maxAttempts) {
                            _uiState.update { it.copy(status = "Edit didn't match the file. Trying again...") }
                        }
                        continue
                    }
                    actions = writes.filterNot { w -> edited.writes.any { it.path == w.path } } + edited.writes
                    editedPaths = edited.writes.map { it.path }.toSet()
                    editCount = edited.editCount
                    actionsMatchModel = rawWrites == writes
                    val recoveredReply = if (actions.isEmpty()) recoverAssistantReplyContent(responseText) else null
                    assistantReply = if (recoveredReply != null) {
                        continueAssistantReplyIfNeeded(
                            sessionKey = sessionKey,
                            initialReply = recoveredReply,
                            fullResponse = responseText,
                            updateResponse = { responseText = it },
                            firstTokenAtMs = { firstTokenAtMs },
                            setFirstTokenAtMs = { firstTokenAtMs = it }
                        )
                    } else {
                        recoverAssistantReply(responseText)
                    }
                    if (actions.isNotEmpty() && actions.all { readyToWrite(it) }) {
                        break
                    }
                    if (!assistantReply.isNullOrBlank()) {
                        break
                    }
                    if (attempt < maxAttempts) {
                        _uiState.update { it.copy(status = "The file looked incomplete. Trying again...") }
                    }
                }

                if (actions.isEmpty() && !assistantReply.isNullOrBlank()) {
                    val metrics = buildGenerationMetrics(generationStartedAtMs, firstTokenAtMs)
                    val reply = assistantReply!!.trim()
                    val finalMessages = uiState.value.messages + ChatMessage("assistant", reply, statsInline = metrics, thinking = studioThinking)
                    persistActiveConversation(context, finalMessages)
                    sessionFingerprint = workspaceKey
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            status = "Answered.",
                            lastRawResponse = responseText,
                            streamingCode = "",
                            messages = finalMessages,
                            tab = 0,
                            generationMetrics = metrics
                        )
                    }
                    return@launch
                }

                if (actions.isEmpty() || actions.any { !readyToWrite(it) }) {
                    val metrics = buildGenerationMetrics(generationStartedAtMs, firstTokenAtMs)
                    val message = if (lastError != null) {
                        "Generation failed after $maxAttempts attempts. The previous working project was kept. Try a shorter request."
                    } else {
                        "The model stopped before finishing write actions after $maxAttempts attempts. The previous working project was kept. Try asking for a shorter complete rewrite."
                    }
                    val finalMessages = uiState.value.messages + ChatMessage("assistant", message, statsInline = metrics, thinking = studioThinking)
                    persistActiveConversation(context, finalMessages)
                    engine.resetSession()
                    sessionFingerprint = null
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            status = message,
                            lastRawResponse = responseText,
                            streamingCode = "",
                            messages = finalMessages,
                            tab = 1,
                            generationMetrics = metrics
                        )
                    }
                    return@launch
                }

                val written = runCatching {
                    withContext(Dispatchers.IO) {
                        val root = activeProjectRoot(context)
                        actions.forEach { writeAction(root, it) }
                    }
                }

                if (written.isSuccess) {
                    val metrics = buildGenerationMetrics(generationStartedAtMs, firstTokenAtMs)
                    refreshWorkspace(context)
                    // If the app rewrote the model's actions (e.g. merged files), its history no longer
                    // matches the workspace, so the next turn starts from a fresh snapshot.
                    sessionFingerprint = if (actionsMatchModel) {
                        withContext(Dispatchers.IO) { "$sessionKey|${workspaceFingerprint(activeProjectRoot(context))}" }
                    } else {
                        null
                    }
                    val assistantText = assistantReply?.takeIf { it.isNotBlank() }?.trim() ?: if (editCount > 0) "Edited ${editedPaths.joinToString()} ($editCount change${if (editCount == 1) "" else "s"})." else "Updated preview."
                    val assistantMessage = ChatMessage("assistant", assistantText, statsInline = metrics, thinking = studioThinking)
                    val finalMessages = uiState.value.messages + assistantMessage
                    val preferredPath = actions.firstOrNull { it.path.equals("index.html", ignoreCase = true) }?.path
                        ?: actions.firstOrNull()?.path
                        ?: uiState.value.selectedCodePath
                    persistActiveConversation(context, finalMessages)
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            status = "Wrote ${actions.size} file action(s) and refreshed preview.",
                            previewVersion = it.previewVersion + 1,
                            workPanelCollapsed = false,
                            tab = 0,
                            selectedCodePath = preferredPath,
                            lastWrittenPaths = actions.map { a -> a.path },
                            lastRawResponse = responseText,
                            streamingCode = "",
                            messages = finalMessages,
                            conversations = conversationInfos(),
                            generationMetrics = metrics
                        )
                    }
                    checkPreviewAfterWrite(context)
                } else {
                    val metrics = buildGenerationMetrics(generationStartedAtMs, firstTokenAtMs)
                    val finalMessages = uiState.value.messages + ChatMessage("assistant", "I generated a complete file, but the app could not save it.", statsInline = metrics)
                    persistActiveConversation(context, finalMessages)
                    engine.resetSession()
                    sessionFingerprint = null
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            status = "Could not write files: ${written.exceptionOrNull()?.message ?: "unknown error"}",
                            lastRawResponse = responseText,
                            streamingCode = "",
                            messages = finalMessages,
                            generationMetrics = metrics
                        )
                    }
                }
            } finally {
                generationInProgress = false
                generationJob = null
                _uiState.update { it.copy(isBusy = false, streamingThinking = null) }
                refreshContextUsage()
            }
        }
    }

    /**
     * Chat space: a plain conversation. The reply streams into [BuilderUiState.streamingReply] and is
     * saved as Markdown; no files, XML actions, or retries are involved.
     */
    /** Browser tab's "Ask about this page": opens Chat and answers [question] from the page's text. */
    fun askAboutPage(context: Context, page: PageText, question: String = "Summarize this page.") {
        if (uiState.value.isBusy || generationInProgress || !uiState.value.modelReady) return
        if (uiState.value.activeKind != CHAT_KIND_ASSISTANT) openSpace(context, CHAT_KIND_ASSISTANT)
        generationInProgress = true
        val title = page.title.ifBlank { hostOf(page.url) }
        val research = WebResearch(
            // Small models read "this page" as missing unless told which source it is.
            prompt = researchPrompt("$question (\"This page\" is source [1], \"$title\", which the user has open in the browser.)", listOf(page), emptyList()),
            pages = listOf(page),
            steps = listOf(ActivityStep(ACTIVITY_READ, "Read ${hostOf(page.url)} from the Browser", page.url))
        )
        generateAssistantReply(context, "$question ($title)", research)
    }

    private fun generateAssistantReply(context: Context, prompt: String, presetResearch: WebResearch? = null) {
        stopRequested = false
        val spokenTurn = voiceTurnPending
        voiceTurnPending = false
        generationJob = viewModelScope.launch {
            try {
                if (!uiState.value.modelReady) {
                    _uiState.update { it.copy(status = "Download a model from the Models tab first.") }
                    return@launch
                }
                val images = if (presetResearch == null) uiState.value.chatImages else emptyList()
                val attachedDocs = if (presetResearch == null) uiState.value.chatDocs else emptyList()
                val pendingMessages = uiState.value.messages + ChatMessage("user", prompt, attachments = images + attachedDocs)
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        prompt = "",
                        chatImages = if (presetResearch == null) emptyList() else it.chatImages,
                        chatDocs = if (presetResearch == null) emptyList() else it.chatDocs,
                        status = "Thinking...",
                        messages = pendingMessages,
                        streamingReply = "",
                        streamingThinking = null,
                        generationMetrics = null
                    )
                }
                persistActiveConversation(context, pendingMessages)
                // A GGUF model sees photos only with its photo-support file: say so before loading it.
                if (images.isNotEmpty() && isGgufModel(savedModelName(context)) && visionFileFor(context, savedModelName(context)) == null) {
                    val reply = if (hasVisionSupport(savedModelName(context))) {
                        ensurePhotoSupport(context)
                        "_Photo support for ${modelDisplayName(uiState.value.modelName)} is still downloading. Send the photo again once it finishes (see the Models page)._"
                    } else {
                        "_${modelDisplayName(uiState.value.modelName)} can't see photos. Gemma 4 E2B, Qwen3.5 4B and Bonsai 27B can: switch in the Models tab._"
                    }
                    val done = pendingMessages + ChatMessage("assistant", reply)
                    persistActiveConversation(context, done)
                    _uiState.update { it.copy(messages = done) }
                    return@launch
                }
                // "Remember that...", "forget...", "what do you remember about me?" are handled by the app.
                val memoryCommand = if (presetResearch == null && images.isEmpty() && attachedDocs.isEmpty()) memoryCommandFrom(prompt) else null
                if (memoryCommand != null) {
                    val done = pendingMessages + ChatMessage("assistant", applyMemoryCommand(context, memoryCommand))
                    persistActiveConversation(context, done)
                    _uiState.update { it.copy(messages = done) }
                    return@launch
                }
                // "Draw ...", "generate an image of ..." go to the image model.
                val imagePrompt = if (presetResearch == null && images.isEmpty() && attachedDocs.isEmpty()) imagePromptFrom(prompt) else null
                if (imagePrompt != null) {
                    val done = pendingMessages + createImageMessage(context, imagePrompt)
                    persistActiveConversation(context, done)
                    _uiState.update { it.copy(messages = done, streamingReply = null) }
                    return@launch
                }
                // Timers, reminders, the stopwatch and scheduled tasks are set by the app, not the model.
                val taskCommand = if (presetResearch == null && images.isEmpty() && attachedDocs.isEmpty()) taskCommandFrom(prompt) else null
                if (taskCommand != null) {
                    val done = pendingMessages + ChatMessage("assistant", applyTaskCommand(context.applicationContext, taskCommand))
                    persistActiveConversation(context, done)
                    _uiState.update { it.copy(messages = done) }
                    return@launch
                }
                // Load after the message is on screen, so the chat can show "Loading model" under it.
                if (!uiState.value.gemmaLoaded) {
                    if (!loadGemmaInternal(context.applicationContext)) {
                        val failed = pendingMessages + ChatMessage("assistant", "_${uiState.value.status}_")
                        persistActiveConversation(context, failed)
                        _uiState.update { it.copy(messages = failed) }
                        return@launch
                    }
                    _uiState.update { it.copy(isBusy = true, status = "Thinking...") }
                }
                if (images.isNotEmpty() && !engine.supportsImages) {
                    val reply = "_${modelDisplayName(uiState.value.modelName)} can't see photos. Gemma 4 E2B and E4B can: switch in the Models tab._"
                    val done = pendingMessages + ChatMessage("assistant", reply)
                    persistActiveConversation(context, done)
                    _uiState.update { it.copy(messages = done) }
                    return@launch
                }
                turnOutputTokens = 0
                turnDecodeSeconds = 0.0
                val startedAtMs = System.currentTimeMillis()
                var firstTokenAtMs: Long? = null

                val sessionKey = uiState.value.activeConversationId.orEmpty()
                // A follow-up question right after a web answer searches again rather than reusing old pages.
                val lastTurnUsedWeb = pendingMessages.dropLast(1).lastOrNull { it.role == "assistant" }?.activity?.any { it.kind == ACTIVITY_SEARCH } == true
                val chatDocuments = attachedDocs.ifEmpty {
                    pendingMessages.dropLast(1).lastOrNull { msg -> msg.role == "user" && msg.attachments.any(::isChatDocument) }
                        ?.attachments?.filter { File(it).exists() && isChatDocument(it) }.orEmpty()
                }
                val useWeb = chatDocuments.isEmpty() && presetResearch == null && webConsentGiven(context) &&
                    (if (uiState.value.webSearchEnabled) chatNeedsWeb(prompt) || (lastTurnUsedWeb && isWebFollowUp(prompt)) || isCorrection(prompt) else looksLikeSearchRequest(prompt))
                // A web turn starts a fresh session anyway; closing the old one first frees its memory
                // while pages load in the background reader.
                if (useWeb) engine.resetSession()
                // "Then search the web and tell me" searches for the question before it.
                val webRequest = resolveWebRequest(
                    prompt,
                    pendingMessages.dropLast(1).filter { it.role == "user" }.map { it.text },
                    pendingMessages.dropLast(1).lastOrNull { it.role == "assistant" && it.activity.isNotEmpty() }?.activity?.let(::lastSearchQuery)
                )
                val research = presetResearch ?: if (useWeb) runWebResearch(context, webRequest) else null
                if (research?.searchBlocked == true) {
                    val done = pendingMessages + ChatMessage("assistant", "_${SEARCH_BLOCKED_NOTE}_", activity = research.steps)
                    persistActiveConversation(context, done)
                    _uiState.update { it.copy(messages = done, liveActivity = emptyList()) }
                    return@launch
                }
                val message = when {
                    // Questions about attached documents: their text (or best passages) with the question.
                    chatDocuments.isNotEmpty() -> {
                        engine.resetSession()
                        compactHistoryIfNeeded(context, pendingMessages)
                        buildAssistantSnapshot(pendingMessages, finalUserText = documentQuestionPrompt(prompt, chatDocuments, snapshotCharBudget(activeTokenWindow) * 3 / 4))
                    }
                    // Web sources are large, so a web turn always starts a fresh session with short history.
                    research != null -> {
                        engine.resetSession()
                        compactHistoryIfNeeded(context, pendingMessages)
                        buildAssistantSnapshot(pendingMessages, finalUserText = research.prompt)
                    }
                    // Continue the open session when it still has room; otherwise rebuild from history.
                    // Each photo takes a few hundred tokens of the window.
                    hasRoomForTurn(sessionKey, prompt + " ".repeat(images.size * 3000)) -> prompt
                    else -> {
                        engine.resetSession()
                        compactHistoryIfNeeded(context, pendingMessages)
                        buildAssistantSnapshot(pendingMessages)
                    }
                }
                // The meter rises as the turn runs: the session's earlier tokens (if it continues),
                // this turn's prompt (web sources included), then the reply (~4 chars a token).
                val sessionContinues = engine.remainingTokens(sessionKey) != null
                val turnBaseTokens = (if (sessionContinues) uiState.value.contextUsedTokens ?: 0 else 0) + message.length / 4
                val window = uiState.value.contextWindowTokens
                _uiState.update { it.copy(status = "Writing...", contextUsedTokens = window?.let { w -> turnBaseTokens.coerceAtMost(w) }) }

                val output = StringBuilder()
                var lastUiUpdate = 0L
                // When reasoning started and ended, for the "Thought for 12s" label.
                var thinkingStartedAtMs = 0L
                var thinkingEndedAtMs = 0L
                val result = runCatching {
                    engine.generate(sessionKey, message, assistantSystemPrompt(), thinking = uiState.value.thinkingEnabled, images = images,
                        thinkingBudget = thinkingBudget(uiState.value.thinkingLevel))
                        .onCompletion { absorbGenerationStats() }
                        .collect { chunk ->
                            val now = System.currentTimeMillis()
                            if (firstTokenAtMs == null && chunk.isNotBlank()) firstTokenAtMs = now
                            if (thinkingStartedAtMs == 0L && "<think>" in chunk) thinkingStartedAtMs = now
                            if (thinkingEndedAtMs == 0L && "</think>" in chunk) {
                                thinkingEndedAtMs = now
                                if (thinkingStartedAtMs == 0L) thinkingStartedAtMs = startedAtMs
                            }
                            output.append(chunk)
                            if (stopRequested) throw StopGeneration("Stopped.")
                            degenerateRepetitionStart(output)?.let { start ->
                                output.setLength(start)
                                throw StopGeneration("Stopped because the model started repeating itself.")
                            }
                            if (now - lastUiUpdate > 80L) {
                                lastUiUpdate = now
                                val split = splitThinking(output.toString())
                                _uiState.update {
                                    it.copy(
                                        streamingReply = split.answer,
                                        streamingThinking = split.thinking.takeIf { text -> split.stillThinking || text.isNotEmpty() },
                                        streamingThinkingSeconds = if (thinkingEndedAtMs > 0) ((thinkingEndedAtMs - thinkingStartedAtMs) / 1000).toInt() else 0,
                                        contextUsedTokens = window?.let { w -> (turnBaseTokens + output.length / 4).coerceAtMost(w) },
                                        status = if (split.stillThinking) "Thinking..." else "Writing..."
                                    )
                                }
                            }
                        }
                }

                // Stopping early still keeps what was written; the session is reset by the engine.
                val stopNote = (result.exceptionOrNull() as? StopGeneration)?.reason
                val split = splitThinking(output.toString())
                val thinkingSeconds = when {
                    split.thinking.isEmpty() || thinkingStartedAtMs == 0L -> 0
                    else -> (((if (thinkingEndedAtMs > 0) thinkingEndedAtMs else System.currentTimeMillis()) - thinkingStartedAtMs) / 1000).toInt()
                }
                val reply = split.answer.trim().let { if (stopNote != null && it.isNotBlank()) "$it\n\n_${stopNote}_" else it }
                val text = when {
                    reply.isNotBlank() -> reply
                    stopNote != null -> "_${stopNote}_"
                    result.isFailure -> "Sorry, the model stopped with an error: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    else -> "The model returned an empty reply. Try asking again."
                }
                if (result.isFailure || reply.isBlank()) engine.resetSession()
                // Small models sometimes invent prices; flag any number that isn't in the sources.
                val unverified = if (research != null && reply.isNotBlank()) {
                    unverifiedNumbers(reply, research.pages.map { it.text })
                } else {
                    emptyList()
                }
                val checkedText = if (unverified.isEmpty()) text else {
                    text + "\n\n> ⚠️ These numbers weren't found in the sources and may be wrong: " +
                        unverified.take(6).joinToString(", ") + ". Check the source pages before relying on them."
                }
                // The app appends the links itself, so they're always correct even if the model garbles them.
                val withSources = if (research != null && research.pages.isNotEmpty() && reply.isNotBlank()) {
                    checkedText + "\n\n**Sources**\n" + research.pages.mapIndexed { i, page ->
                        // Encode brackets so URLs like .../Gemini_(language_model) don't end the link early.
                        val url = page.url.replace("(", "%28").replace(")", "%29").replace(" ", "%20")
                        "${i + 1}. [${page.title.ifBlank { hostOf(page.url) }.replace("[", "(").replace("]", ")")}]($url)"
                    }.joinToString("\n")
                } else {
                    checkedText
                }
                val metrics = buildGenerationMetrics(startedAtMs, firstTokenAtMs)
                val finalMessages = uiState.value.messages +
                    ChatMessage(
                        "assistant",
                        withSources,
                        statsInline = metrics,
                        activity = research?.steps.orEmpty(),
                        thinking = split.thinking,
                        thinkingSeconds = thinkingSeconds
                    )
                persistActiveConversation(context, finalMessages)
                if (spokenTurn && uiState.value.speakVoiceReplies && reply.isNotBlank()) {
                    val voice = speaker ?: Speaker(context).also { attachSpeaker(it) }
                    finalMessages.last().let { voice.speak(it.timestamp, it.text) }
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = "Answered.",
                        messages = finalMessages,
                        streamingReply = null,
                        streamingThinking = null,
                        liveActivity = emptyList(),
                        generationMetrics = metrics
                    )
                }
            } finally {
                generationInProgress = false
                generationJob = null
                _uiState.update { it.copy(isBusy = false, streamingReply = null, streamingThinking = null, liveActivity = emptyList()) }
                refreshContextUsage()
            }
        }
    }

    /**
     * Stop button. Assistant replies stop at the next token and keep what was written; Studio
     * generations (multi-step file writes) are cancelled outright so no half-written file is saved.
     */
    fun stopGeneration() {
        if (!generationInProgress) return
        stopRequested = true
        imageGenerator?.cancel()
        if (uiState.value.activeKind == CHAT_KIND_STUDIO) {
            generationJob?.cancel()
            _uiState.update { it.copy(status = "Stopped.") }
        }
    }

    // ---- Image generation (Chat space) ----

    @Volatile private var imageGenerator: ImageGenerator? = null

    /** The Models page's size and steps for an image model. */
    fun setImageSettings(context: Context, modelFileName: String, options: ImageOptions) {
        val model = MODEL_CATALOG.firstOrNull { it.fileName == modelFileName && it.isImage } ?: return
        saveImageSettings(context, model, options)
        _uiState.update { it.copy(imageSettings = it.imageSettings + (modelFileName to savedImageSettings(context, model))) }
    }

    /** Makes an image for [prompt] with the image model, freeing the chat model's memory first. */
    private suspend fun createImageMessage(context: Context, prompt: String): ChatMessage {
        val model = activeImageModel(context) ?: run {
            val suggested = MODEL_CATALOG.first { it.id == IMAGE_MODEL_ID }
            return ChatMessage("assistant", "_To create images, download an image model such as **${suggested.name}** (${formatBytes(suggested.sizeBytes)}) under **Image generation** on the Models page, then ask again._")
        }
        val options = savedImageSettings(context, model)
        val imageSteps = options.steps
        val (width, height) = options.dimensions
        // The image model and a chat model rarely fit in memory together.
        engine.close()
        engine = DemoBuilderEngine()
        sessionFingerprint = null
        refreshContextUsage()
        _uiState.update {
            it.copy(gemmaLoaded = false, engineLabel = "Not loaded", status = "Creating image…", imageGenerating = true, streamingReply = "_Creating an image of $prompt…_")
        }
        val generator = ImageGenerator(context.applicationContext).also { imageGenerator = it }
        val started = System.currentTimeMillis()
        val ticker = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(400)
                val (step, steps) = generator.progress()
                // After the drawing steps the image is decoded, which reports its own (much longer) progress.
                val label = if (steps == imageSteps && step > 0) "step ${step} of ${steps}" else if (step > 0) "finishing" else null
                if (label != null) _uiState.update { it.copy(streamingReply = "_Creating an image of $prompt… ${label}_") }
            }
        }
        val file = try {
            generator.generate(model, prompt, options)
        } finally {
            ticker.cancel()
            imageGenerator = null
            _uiState.update { it.copy(imageGenerating = false) }
        }
        if (file == null) {
            return ChatMessage("assistant", if (stopRequested) "_Stopped._" else "_The image couldn't be created. The phone may be low on memory: close other apps and try again._")
        }
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        return ChatMessage(
            "assistant",
            "Here's your image: *$prompt*",
            attachments = listOf(file.absolutePath),
            statsInline = String.format(java.util.Locale.US, "${model.name} · ${width}×${height} · $imageSteps step${if (imageSteps == 1) "" else "s"} · %.0fs", seconds)
        )
    }

    // ---- Web research (Chat space) ----

    private class WebResearch(
        val prompt: String,
        val pages: List<PageText>,
        val steps: List<ActivityStep>,
        /** Search was refused with a bot check and nothing was read: say so instead of asking the model. */
        val searchBlocked: Boolean = false
    )

    fun setWebSearchEnabled(context: Context, enabled: Boolean) {
        if (enabled && !webConsentGiven(context)) {
            _uiState.update { it.copy(webConsentPending = true) }
            return
        }
        saveWebSearchEnabled(context, enabled)
        _uiState.update { it.copy(webSearchEnabled = enabled) }
    }

    fun answerWebConsent(context: Context, allowed: Boolean) {
        if (allowed) {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("webConsent", true).apply()
        }
        saveWebSearchEnabled(context, allowed)
        _uiState.update { it.copy(webConsentPending = false, webSearchEnabled = allowed) }
        // The message that needed the web was held back for this answer; send it now.
        if (sendAfterConsent) {
            sendAfterConsent = false
            generate(context)
        }
    }

    private fun webConsentGiven(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("webConsent", false)

    fun openInBrowserTab(url: String) {
        if (WebPolicy.isAllowed(url)) _uiState.update { it.copy(browserUrl = url) }
    }

    fun consumeBrowserUrl() {
        _uiState.update { it.copy(browserUrl = null) }
    }

    /**
     * Search, then read the top results: a fixed recipe rather than letting the model drive, since
     * small models are reliable at single steps but not at long open-ended tool loops. Only
     * read-only actions happen here; every step is logged for the activity view.
     */
    private suspend fun runWebResearch(context: Context, request: String): WebResearch {
        val steps = mutableListOf<ActivityStep>()
        fun show(step: ActivityStep, replaceLast: Boolean = false) {
            if (replaceLast && steps.isNotEmpty()) steps[steps.lastIndex] = step else steps += step
            _uiState.update { it.copy(liveActivity = steps.toList(), status = step.text) }
        }

        val query = searchQueryFrom(request)
        show(ActivityStep(ACTIVITY_SEARCH, "Searching DuckDuckGo for \"$query\""))
        val search = withContext(Dispatchers.IO) { runCatching { searchDuckDuckGo(query, limit = 8) } }
        val blocked = search.exceptionOrNull() is SearchBlockedException
        val results = search.getOrDefault(emptyList()).filter { WebPolicy.isAllowed(it.url) }
        show(
            if (blocked) ActivityStep(ACTIVITY_NOTE, "DuckDuckGo asked for a bot check")
            else if (results.isEmpty()) ActivityStep(ACTIVITY_NOTE, "No search results (offline, or DuckDuckGo didn't answer)")
            else ActivityStep(ACTIVITY_SEARCH, "Searched \"$query\" · ${results.size} results"),
            replaceLast = true
        )

        val pages = mutableListOf<PageText>()
        val skippedHosts = mutableSetOf<String>()
        for (result in results) {
            if (pages.size >= MAX_WEB_PAGES || stopRequested) break
            val host = hostOf(result.url)
            if (pages.any { hostOf(it.url) == host } || host in skippedHosts) continue
            show(ActivityStep(ACTIVITY_READ, "Reading $host", result.url))
            // Direct fetch first (fast); fall back to a real browser engine for JS-heavy or picky sites.
            val direct = withContext(Dispatchers.IO) { fetchPageText(result.url) }
            val usable = direct?.takeIf { it.text.length >= 600 && !looksBlocked(it) }
            if (usable == null && tooLowOnMemoryForWebView(context)) {
                show(ActivityStep(ACTIVITY_SKIP, "Skipped $host to save memory", result.url), replaceLast = true)
                skippedHosts += host
                continue
            }
            val page = usable ?: readWithWebView(context, result.url)
            if (page == null || page.text.length < 200 || looksBlocked(page)) {
                show(ActivityStep(ACTIVITY_SKIP, "Couldn't read $host", result.url), replaceLast = true)
                continue
            }
            pages += page.copy(title = page.title.ifBlank { result.title }, url = result.url)
            show(ActivityStep(ACTIVITY_READ, "Read $host", result.url), replaceLast = true)
        }

        val unread = results.filterNot { r -> pages.any { it.url == r.url } }.take(4)
        return WebResearch(researchPrompt(request, pages, unread), pages, steps.toList(), searchBlocked = blocked && pages.isEmpty())
    }

    /** The summarizing prompt: numbered sources marked as untrusted, then the request. */
    private fun researchPrompt(request: String, pages: List<PageText>, unread: List<SearchResult>): String =
        webResearchPrompt(request, pages, unread, snapshotCharBudget(activeTokenWindow) * 3 / 4)

    /** Recent history (plus the running summary) as one message, for a chat whose session was reset. */
    private fun buildAssistantSnapshot(messages: List<ChatMessage>, finalUserText: String = messages.last().text): String {
        val conversation = conversations.firstOrNull { it.id == uiState.value.activeConversationId }
        val earlier = messages.dropLast(1)
        val summarized = conversation?.summarizedCount?.coerceAtMost(earlier.size) ?: 0
        val summary = conversation?.summary.orEmpty().takeIf { summarized > 0 }.orEmpty()
        val history = earlier.drop(summarized).takeLast(RECENT_MESSAGES_KEPT)
            .joinToString("\n\n") { message ->
                // Photos aren't resent to a fresh session; the history just notes them.
                val photos = if (message.attachments.isNotEmpty()) " [sent ${message.attachments.size} photo(s)]" else ""
                "${if (message.role == "user") "User" else "Assistant"}: ${message.text.take(2000)}$photos"
            }
            // Web turns carry large sources, so they keep a smaller share of history.
            .takeLast(snapshotCharBudget(activeTokenWindow) / (if (finalUserText == messages.last().text) 1 else 4) * 2 / 3)
        if (summary.isBlank() && history.isBlank()) return finalUserText
        return buildString {
            if (summary.isNotBlank()) append("Summary of the earlier conversation:\n$summary\n\n")
            if (history.isNotBlank()) append("Conversation so far:\n$history\n\n")
            if (finalUserText == messages.last().text) append("Reply to the user's new message:\n")
            append(finalUserText)
        }
    }

    // ---- Photos and memory (Chat space) ----

    /** Copies a picked photo into app storage, downscaled so the model encodes it quickly. */
    fun attachChatImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { saveChatImage(context, uri) }.getOrNull() }
            _uiState.update {
                if (path == null) it.copy(status = "Couldn't open that image.")
                else it.copy(chatImages = (it.chatImages + path).takeLast(MAX_CHAT_IMAGES))
            }
        }
    }

    /** Copies picked documents into app storage and reads their text, for questions about them. */
    fun attachChatDocuments(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(readingDocument = true, status = "Reading document...") }
            val failures = mutableListOf<String>()
            val saved = withContext(Dispatchers.IO) {
                uris.take(MAX_CHAT_DOCUMENTS).mapNotNull { uri ->
                    runCatching { saveChatDocument(context, uri) }.onFailure { failures += it.message ?: "Couldn't read a file." }.getOrNull()
                }
            }
            _uiState.update { it.copy(readingDocument = false, chatDocs = (it.chatDocs + saved).distinct().takeLast(MAX_CHAT_DOCUMENTS), status = "Ready.") }
            failures.firstOrNull()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }

    fun removeChatDocument(path: String) {
        _uiState.update { it.copy(chatDocs = it.chatDocs - path) }
    }

    fun removeChatImage(path: String) {
        _uiState.update { it.copy(chatImages = it.chatImages - path) }
    }

    private fun saveChatImage(context: Context, uri: Uri): String {
        val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder also applies the photo's EXIF rotation.
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > CHAT_IMAGE_MAX_SIDE) {
                val scale = CHAT_IMAGE_MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val file = File(dir, "${java.util.UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun assistantSystemPrompt(): String =
        ASSISTANT_SYSTEM_PROMPT + if (uiState.value.memoryEnabled) memoryPromptSection(uiState.value.memories) else ""

    private fun applyMemoryCommand(context: Context, command: MemoryCommand): String {
        val memories = uiState.value.memories
        if (!uiState.value.memoryEnabled) return "Memory is turned off. Turn it on in Settings → Memory."
        return when (command) {
            is MemoryCommand.Remember -> {
                val updated = withMemory(memories, command.fact)
                setMemories(context, updated)
                if (updated == memories) "I already remember that."
                else "Got it, I'll remember: **${updated.last()}**\n\nYou can see and edit what I remember in Settings → Memory."
            }
            is MemoryCommand.Forget -> {
                val matches = matchingMemories(memories, command.query)
                if (matches.isEmpty()) {
                    "I don't have anything saved about \"${command.query}\". Settings → Memory shows everything I remember."
                } else {
                    setMemories(context, memories - matches.toSet())
                    "Forgotten:\n" + matches.joinToString("\n") { "- $it" }
                }
            }
            MemoryCommand.ShowAll ->
                if (memories.isEmpty()) "I don't remember anything about you yet. Say \"remember that...\" to save something."
                else "Here's what I remember about you:\n" + memories.joinToString("\n") { "- $it" }
        }
    }

    private fun setMemories(context: Context, memories: List<String>) {
        saveMemories(context, memories)
        // Memories are part of the system prompt, so the open session is rebuilt on the next turn.
        _uiState.update { it.copy(memories = memories) }
    }

    fun addMemory(context: Context, fact: String) = setMemories(context, withMemory(uiState.value.memories, fact))

    fun deleteMemory(context: Context, fact: String) = setMemories(context, uiState.value.memories - fact)

    private fun applyTaskCommand(context: Context, command: TaskCommand): String {
        val seeTasks = "\n\nYou'll get a notification. Manage it under **Tasks** (the clock at the top of Chat)."
        return when (command) {
            is TaskCommand.Timer -> {
                val task = ScheduledTask(kind = TASK_TIMER, text = command.label, triggerAt = System.currentTimeMillis() + command.durationMs, durationMs = command.durationMs)
                TaskAlarms.schedule(context, task)
                "⏱️ ${if (command.label.isBlank()) "Timer" else "**${command.label}** timer"} set for **${formatDuration(command.durationMs)}**, until ${formatTaskTime(task.triggerAt).substringAfter(" at ")}." + seeTasks
            }
            is TaskCommand.Reminder -> {
                TaskAlarms.schedule(context, ScheduledTask(kind = TASK_REMINDER, text = command.text, triggerAt = command.at, repeatDaily = command.daily))
                "🔔 I'll remind you to **${command.text}** ${if (command.daily) "every day at " + formatTaskTime(command.at).substringAfter(" at ") else formatTaskTime(command.at)}." + seeTasks
            }
            is TaskCommand.Assistant -> {
                TaskAlarms.schedule(context, ScheduledTask(kind = TASK_AI, text = command.request, triggerAt = command.at, repeatDaily = command.daily))
                "🗓️ Scheduled: **${command.request}**, ${if (command.daily) "every day at " + formatTaskTime(command.at).substringAfter(" at ") else formatTaskTime(command.at)}. " +
                    "I'll run it on this phone and send the answer as a notification; it also appears as a new chat." +
                    "\n\nManage it under **Tasks** (the clock at the top of Chat)."
            }
            TaskCommand.StartStopwatch -> {
                val state = TaskRepository.stopwatch.value
                if (state.running) "The stopwatch is already running." else {
                    val started = state.copy(running = true, startedAt = System.currentTimeMillis())
                    TaskRepository.setStopwatch(context, started)
                    TaskNotifications.stopwatch(context, started)
                    "⏱️ Stopwatch started. It keeps running in your notifications; open **Tasks** to pause or reset it."
                }
            }
            TaskCommand.StopStopwatch -> {
                val state = TaskRepository.stopwatch.value
                if (!state.running) "The stopwatch isn't running." else {
                    val stopped = StopwatchState(running = false, accumulatedMs = state.elapsedMs())
                    TaskRepository.setStopwatch(context, stopped)
                    TaskNotifications.stopwatch(context, stopped)
                    "⏱️ Stopwatch paused at **${formatStopwatch(stopped.accumulatedMs)}**."
                }
            }
            TaskCommand.ResetStopwatch -> {
                TaskRepository.setStopwatch(context, StopwatchState())
                TaskNotifications.stopwatch(context, StopwatchState())
                "⏱️ Stopwatch reset."
            }
        }
    }

    /** Re-reads chats from storage after a scheduled task saved one. */
    private fun reloadConversations(context: Context) {
        conversations = loadConversations(context).toMutableList()
        // The open chat may be the one a task just wrote its answer to.
        val active = conversations.firstOrNull { it.id == uiState.value.activeConversationId }
        _uiState.update {
            if (active != null && !generationInProgress) it.copy(conversations = conversationInfos(), messages = active.messages)
            else it.copy(conversations = conversationInfos())
        }
    }

    /** Opens a chat a notification pointed to (a scheduled task's result). */
    fun openChatFromNotification(context: Context, id: String) {
        if (conversations.none { it.id == id }) reloadConversations(context)
        if (conversations.any { it.id == id }) selectConversation(context, id)
        // If its task is still running, show where it is right away.
        AppEvents.taskProgress.value?.takeIf { it.chatId == id }?.let { progress ->
            _uiState.update { it.copy(streamingReply = progress.text, liveActivity = progress.steps) }
        }
    }

    /** Speaker button on a reply: reads it aloud, or stops if it is already being read. */
    fun toggleSpeak(context: Context, message: ChatMessage) {
        val speaker = speaker ?: Speaker(context).also { attachSpeaker(it) }
        if (uiState.value.speakingMessageId == message.timestamp) speaker.stop() else speaker.speak(message.timestamp, message.text)
    }

    fun stopSpeaking() {
        speaker?.stop()
    }

    private fun attachSpeaker(created: Speaker) {
        speaker = created
        viewModelScope.launch { created.speakingId.collect { id -> _uiState.update { it.copy(speakingMessageId = id) } } }
    }

    fun setSpeakVoiceReplies(context: Context, enabled: Boolean) {
        saveSpeakVoiceReplies(context, enabled)
        _uiState.update { it.copy(speakVoiceReplies = enabled) }
    }

    fun setMemoryEnabled(context: Context, enabled: Boolean) {
        saveMemoryEnabled(context, enabled)
        _uiState.update { it.copy(memoryEnabled = enabled) }
    }

    fun openInBrowser(context: Context) {
        viewModelScope.launch {
            val result = runCatching {
                val index = File(activeProjectRoot(context), "index.html")
                require(index.exists()) { "No index.html to open." }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", index)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/html")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open HTML in browser").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            _uiState.update { it.copy(status = if (result.isSuccess) "Opening in browser..." else "Open in browser failed: ${result.exceptionOrNull()?.message ?: "unknown error"}") }
        }
    }

    fun exportFile(context: Context, relativePath: String, destination: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = safeResolve(activeProjectRoot(context), relativePath)
                    require(source.exists()) { "File does not exist: $relativePath" }
                    context.contentResolver.openOutputStream(destination).use { output ->
                        requireNotNull(output) { "Could not open destination file." }
                        source.inputStream().use { input -> input.copyTo(output) }
                    }
                }
            }
            _uiState.update { it.copy(status = if (result.isSuccess) "Saved $relativePath" else "Save failed: ${result.exceptionOrNull()?.message ?: "unknown error"}") }
        }
    }

    fun exportProjectZip(context: Context, destination: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val root = activeProjectRoot(context)
                    context.contentResolver.openOutputStream(destination).use { output ->
                        requireNotNull(output) { "Could not open destination ZIP." }
                        ZipOutputStream(output).use { zip ->
                            root.walkTopDown().filter { it.isFile }.forEach { file ->
                                val entryName = file.relativeTo(root).path.replace('\\', '/')
                                zip.putNextEntry(ZipEntry(entryName))
                                file.inputStream().use { input -> input.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
            _uiState.update { it.copy(status = if (result.isSuccess) "Saved project ZIP." else "ZIP save failed: ${result.exceptionOrNull()?.message ?: "unknown error"}") }
        }
    }

    // ---- Code: preview errors and automatic fixes ----

    private var autoFixRounds = 0
    private var autoFixInFlight = false

    /** After files are written, waits for the preview to run and sends any errors back to the model (twice at most). */
    private fun checkPreviewAfterWrite(context: Context) {
        viewModelScope.launch {
            val kind = withContext(Dispatchers.IO) { projectKind(activeProjectRoot(context)) }
            // Python has to start up before it runs main.py.
            kotlinx.coroutines.delay(if (kind == ProjectKind.Python) 12_000 else 4_000)
            val errors = PreviewConsole.errors()
            if (errors.isEmpty() || autoFixRounds >= 2 || uiState.value.isBusy || uiState.value.activeKind != CHAT_KIND_STUDIO) return@launch
            autoFixRounds++
            sendPreviewFix(context, errors, automatic = true)
        }
    }

    /** The Console tab's "Fix errors" button. */
    fun fixPreviewErrors(context: Context) {
        val errors = PreviewConsole.errors()
        if (errors.isEmpty() || uiState.value.isBusy) return
        sendPreviewFix(context, errors, automatic = false)
    }

    private fun sendPreviewFix(context: Context, errors: List<ConsoleEntry>, automatic: Boolean) {
        val details = errors.distinctBy { it.message }.take(4).joinToString("\n") { it.message.take(600) }
        val prompt = (if (automatic) "The preview reported an error after your change:" else "The preview shows these errors:") +
            "\n$details\nFix the code so it runs without errors."
        _uiState.update { it.copy(prompt = prompt) }
        autoFixInFlight = automatic
        generate(context)
    }

    /** The name an exported app gets: the project's name. */
    fun exportAppName(): String {
        val active = conversations.firstOrNull { it.id == uiState.value.activeConversationId }
        val name = active?.let { uiState.value.projectNames[it.projectKey] } ?: active?.title?.takeUnless { it == "New chat" }
        return name?.replace(Regex("[^A-Za-z0-9 _-]"), "")?.trim()?.take(40)?.ifBlank { null } ?: "Mochu App"
    }

    /** Builds and signs an installable APK of the project and saves it where the user picked. */
    fun exportAndroidApk(context: Context, destination: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = "Building the app…") }
            val name = exportAppName()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val active = conversations.first { it.id == uiState.value.activeConversationId }
                    val apk = File(context.cacheDir, "export.apk").apply { delete() }
                    exportProjectApk(context, activeProjectRoot(context), name, exportPackageName(name, active.projectKey), apk)
                    context.contentResolver.openOutputStream(destination).use { output ->
                        requireNotNull(output) { "Could not open the destination." }
                        apk.inputStream().use { it.copyTo(output) }
                    }
                    apk.delete()
                }
            }
            val message = if (result.isSuccess) "Saved $name.apk. Open it from your Files app to install it." else "Couldn't build the app: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            _uiState.update { it.copy(status = message) }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun exportCapacitorProjectZip(context: Context, destination: Uri) {
        viewModelScope.launch {
            val title = exportAppName()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination).use { output ->
                        requireNotNull(output) { "Could not open destination ZIP." }
                        writeCapacitorZip(activeProjectRoot(context), title.take(40), output)
                    }
                }
            }
            _uiState.update {
                it.copy(status = if (result.isSuccess) "Saved Capacitor project ZIP. See README-MOCHU.md inside for the Android steps." else "Capacitor ZIP failed: ${result.exceptionOrNull()?.message ?: "unknown error"}")
            }
        }
    }

    fun exportPwaReadyProjectZip(context: Context, destination: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val root = activeProjectRoot(context)
                    ensurePwaFiles(root)
                    context.contentResolver.openOutputStream(destination).use { output ->
                        requireNotNull(output) { "Could not open destination ZIP." }
                        ZipOutputStream(output).use { zip ->
                            root.walkTopDown().filter { it.isFile }.forEach { file ->
                                val entryName = file.relativeTo(root).path.replace('\\', '/')
                                zip.putNextEntry(ZipEntry(entryName))
                                file.inputStream().use { input -> input.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(
                    status = if (result.isSuccess) {
                        "Saved PWA-ready ZIP (with manifest.webmanifest and service-worker.js)."
                    } else {
                        "PWA ZIP save failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    }
                )
            }
            if (result.isSuccess) {
                refreshWorkspace(context)
            }
        }
    }

    private fun projectKeyOf(conversationId: String): String =
        conversations.firstOrNull { it.id == conversationId }?.projectKey ?: conversationId

    /** Renames a Code project; a blank name goes back to its first chat's title. */
    /** Renames a chat (drawer pencil button). */
    fun renameConversation(context: Context, id: String, title: String) {
        val name = title.trim().take(80).ifBlank { return }
        val updated = conversations.firstOrNull { it.id == id }?.copy(title = name) ?: return
        conversations = conversations.map { if (it.id == id) updated else it }.toMutableList()
        saveConversationFile(context, updated)
        _uiState.update { it.copy(conversations = conversationInfos()) }
    }

    fun renameProject(context: Context, projectKey: String, name: String) {
        saveProjectName(context, projectKey, name)
        _uiState.update { it.copy(projectNames = savedProjectNames(context)) }
    }

    /** Deletes a Code project: all its chats and its files. */
    fun deleteProject(context: Context, projectKey: String) {
        if (generationInProgress || uiState.value.isBusy) return
        val chats = conversations.filter { it.projectKey == projectKey }
        if (chats.isEmpty()) return
        val keepActive = uiState.value.activeConversationId.let { id -> chats.none { it.id == id } }
        chats.drop(1).forEach { chat ->
            conversations = conversations.filterNot { it.id == chat.id }.toMutableList()
            File(chatsRoot(context), "${chat.id}.chat").delete()
        }
        saveProjectName(context, projectKey, null)
        _uiState.update { it.copy(projectNames = it.projectNames - projectKey) }
        // The last one goes through deleteConversation, which also removes the files and picks what to show next.
        deleteConversation(context, chats.first().id)
        if (keepActive) _uiState.update { it.copy(sidebarOpen = false) }
    }

    private fun activeProjectRoot(context: Context): File {
        val id = uiState.value.activeConversationId ?: conversations.firstOrNull()?.id
        require(!id.isNullOrBlank()) { "No active conversation." }
        return ensureProjectForConversation(context, projectKeyOf(id), migrateLegacy = false)
    }

    private fun refreshWorkspace(context: Context, conversationId: String? = null) {
        val root = if (conversationId != null) {
            ensureProjectForConversation(context, projectKeyOf(conversationId), migrateLegacy = false)
        } else {
            activeProjectRoot(context)
        }
        removeUnusedStarterPage(root)
        val index = File(root, "index.html")
        val files = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }.sorted().toList()
        val selected = uiState.value.selectedCodePath
        val selectedFile = runCatching { safeResolve(root, selected) }.getOrNull()
        val mainFile = mainFileOf(root)
        // Switching projects opens that project's main file rather than one left over from the last.
        val effectivePath = when {
            conversationId == null && selectedFile?.exists() == true -> selected
            mainFile != null -> mainFile
            selectedFile?.exists() == true -> selected
            index.exists() -> "index.html"
            files.isNotEmpty() -> files.first()
            else -> "index.html"
        }
        val current = runCatching { safeResolve(root, effectivePath).readText() }.getOrDefault("")
        val modelNameNow = savedModelName(context)
        val estimatedFileContextChars = estimateFileContextChars(
            root = root,
            maxTotalChars = snapshotCharBudget(activeTokenWindow)
        )
        _uiState.update {
            it.copy(
                indexHtmlPath = index.absolutePath,
                selectedCodePath = effectivePath,
                currentCode = current,
                files = files,
                estimatedFileContextChars = estimatedFileContextChars,
                modelName = modelNameNow,
                modelSupportsMultimodal = isLikelyMultimodalModel(modelNameNow),
                multimodalBackendReady = multimodalEngine?.isReady() == true,
                modelReady = modelAvailable(context)
            )
        }
    }

    private fun estimateFileContextChars(root: File, maxTotalChars: Int): Int {
        val perFileLimit = 5000
        var total = 0
        val files = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path.replace('\\', '/') to it }
            .filter { (path, _) -> !path.equals("index.html", ignoreCase = true) }
            .toList()
        if (files.isEmpty()) return 0

        // Mirror buildProjectContextForModel framing overhead for a closer estimate.
        files.forEach { (path, file) ->
            if (total >= maxTotalChars) return@forEach
            val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
            val bodyChars = when {
                vmIsTextExtension(ext) -> minOf(file.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), perFileLimit)
                ext == "docx" || ext == "pdf" || vmIsImageExtension(ext) || vmIsAudioExtension(ext) -> 600
                else -> 0
            }
            if (bodyChars <= 0) return@forEach
            val wrapped = bodyChars + path.length + 28 // <<<FILE:path>>> + <<<END_FILE>>> + newlines
            val remaining = maxTotalChars - total
            total += minOf(wrapped, remaining)
        }
        return total.coerceAtMost(maxTotalChars)
    }

    private fun vmIsTextExtension(ext: String): Boolean = ext in setOf(
        "txt", "md", "json", "js", "mjs", "ts", "tsx", "jsx", "css", "scss", "sass",
        "html", "htm", "xml", "csv", "tsv", "yaml", "yml", "toml", "ini", "log"
    )

    private fun vmIsImageExtension(ext: String): Boolean = ext in setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif"
    )

    private fun vmIsAudioExtension(ext: String): Boolean = ext in setOf(
        "mp3", "wav", "m4a", "aac", "ogg", "opus", "flac", "3gp", "amr"
    )

    private suspend fun buildPrompt(
        context: Context,
        userRequest: String,
        messages: List<ChatMessage>,
        preferredAttachmentPaths: List<String> = emptyList()
    ): String {
        val root = activeProjectRoot(context)
        // Everything below has to share one budget so the snapshot always fits the loaded window.
        val budget = snapshotCharBudget(activeTokenWindow)
        val conversation = conversations.firstOrNull { it.id == uiState.value.activeConversationId }
        val summarized = conversation?.summarizedCount?.coerceAtMost(messages.size) ?: 0
        val summary = conversation?.summary.orEmpty().takeIf { summarized > 0 }.orEmpty()
        val recentMessages = messages.drop(summarized).takeLast(RECENT_MESSAGES_KEPT + 2)
        val recent = buildString {
            if (summary.isNotBlank()) append("Summary of the earlier conversation:\n$summary\n\nLatest messages:\n")
            append(recentMessages.joinToString("\n") { msg -> "${msg.role}: ${msg.text.take(1000)}" })
        }.takeLast(budget / 5)
        val current = htmlContextForModel(
            File(root, "index.html").takeIf { it.exists() }?.readText().orEmpty(),
            maxChars = minOf(12000, budget / 2)
        )
        val fileContext = buildProjectContextForModel(
            context = context,
            root = root,
            preferredPaths = preferredAttachmentPaths,
            onlyPreferredWhenProvided = preferredAttachmentPaths.isNotEmpty(),
            maxTotalChars = (budget - recent.length - current.length).coerceAtLeast(0)
        )
        val multimodalHint = if (uiState.value.modelSupportsMultimodal) {
            "Model capability routing: multimodal-capable model detected. Prioritize attached image/audio understanding first, then use extracted file text."
        } else {
            "Model capability routing: text-only model path. Use extracted OCR/transcript/file text provided by the app."
        }
        // BUILD_SYSTEM_PROMPT is already the conversation's system instruction, so it isn't repeated here.
        return """
Project snapshot (this replaces anything earlier in the chat):

Recent chat:
$recent

Current index.html (if present):
```html
$current
```

Additional imported/generated files context:
$fileContext

$multimodalHint

${buildTurnPrompt(userRequest)}
        """.trimIndent()
    }

    /** Prompt for a turn that continues an open session, where the model already holds the project in its history. */
    private fun buildTurnPrompt(userRequest: String): String = """
User request:
$userRequest

To change a file that already exists, use edit_file with <find>/<replace> for only the lines that change; don't rewrite the whole file. Use write_file only for new files or a complete rewrite.
Return the XML action(s) now.
    """.trimIndent()

    private fun hasRoomForTurn(sessionKey: String, turnPrompt: String): Boolean {
        val remaining = engine.remainingTokens(sessionKey) ?: return false
        val outputReserve = minOf(activeTokenWindow / 2, 4096)
        // ~3 chars per token is a conservative estimate for code-heavy prompts.
        return remaining - turnPrompt.length / 3 > outputReserve
    }

    /** Changes whenever a project file is added, removed, or modified. */
    private fun workspaceFingerprint(root: File): String = root.walkTopDown()
        .filter { it.isFile }
        .map { "${it.relativeTo(root).path}:${it.length()}:${it.lastModified()}" }
        .sorted()
        .joinToString("|")
        .hashCode()
        .toString()

    private suspend fun continueAssistantReplyIfNeeded(
        sessionKey: String,
        initialReply: AssistantReplyContent,
        fullResponse: String,
        updateResponse: (String) -> Unit,
        firstTokenAtMs: () -> Long?,
        setFirstTokenAtMs: (Long) -> Unit
    ): String {
        var reply = initialReply.text
        var combinedResponse = fullResponse
        var complete = initialReply.complete
        var continuation = 1
        val maxContinuations = 3

        while (!complete && continuation <= maxContinuations) {
            _uiState.update {
                it.copy(
                    status = "Continuing answer... ${continuation}/$maxContinuations",
                    streamingCode = ""
                )
            }
            val prompt = """
The previous reply was cut off before the XML action closed.

Previous reply content so far:
${reply.takeLast(4000)}

Continue exactly where that reply stopped. Return one reply action containing only the remaining continuation text. Do not repeat the previous content. Close with </content> and </action> when finished.
            """.trimIndent()
            val output = StringBuilder()
            var lastUiUpdate = 0L
            val result = runCatching {
                engine.generate(sessionKey, prompt).onCompletion { absorbGenerationStats() }.collect { chunk ->
                    if (firstTokenAtMs() == null && chunk.isNotBlank()) {
                        setFirstTokenAtMs(System.currentTimeMillis())
                    }
                    output.append(chunk)
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate > 220L || output.length < 1000) {
                        lastUiUpdate = now
                        val streamed = "$combinedResponse\n${output}"
                        _uiState.update {
                            it.copy(
                                status = "Continuing...",
                                lastRawResponse = streamed,
                                streamingCode = ""
                            )
                        }
                    }
                }
            }
            if (result.isFailure) break

            val continuationResponse = output.toString()
            if (continuationResponse.isBlank()) break
            combinedResponse = "$combinedResponse\n$continuationResponse"
            updateResponse(combinedResponse)

            val recovered = recoverAssistantReplyContent(continuationResponse)
            val continuationText = recovered?.text ?: cleanGeneratedFileContent(continuationResponse)
            val merged = appendReplyContinuation(reply, continuationText)
            if (merged == reply) break
            reply = merged
            complete = recovered?.complete ?: true
            continuation += 1
        }

        return reply
    }

    private fun appendReplyContinuation(current: String, continuation: String): String {
        val next = continuation.trim()
        if (next.isBlank()) return current
        if (current.contains(next)) return current

        val overlap = minOf(current.length, next.length, 500)
            .downTo(20)
            .firstOrNull { size -> current.takeLast(size) == next.take(size) }
            ?: 0
        val suffix = next.drop(overlap).trimStart()
        if (suffix.isBlank()) return current
        return current.trimEnd() + "\n" + suffix
    }

    private suspend fun continueRawResponseIfNeeded(
        sessionKey: String,
        initialResponse: String,
        firstTokenAtMs: () -> Long?,
        setFirstTokenAtMs: (Long) -> Unit
    ): String {
        var response = initialResponse
        var continuation = 1
        val maxContinuations = 6

        while (looksCutOff(response) && continuation <= maxContinuations) {
            _uiState.update {
                it.copy(
                    status = "Continuing...",
                    lastRawResponse = response,
                    streamingCode = extractStreamingCode(response)
                )
            }

            val prompt = """
Your previous response was cut off because the Android runtime stopped the generation.

Continue from the exact next character after this ending:
${response.takeLast(3500)}

Rules:
- Do not restart.
- Do not repeat previous text.
- If an XML action is open, continue inside that same action and close it when finished.
- Output only the missing continuation text.
            """.trimIndent()
            val output = StringBuilder()
            var lastUiUpdate = 0L
            val result = runCatching {
                engine.generate(sessionKey, prompt).onCompletion { absorbGenerationStats() }.collect { chunk ->
                    if (firstTokenAtMs() == null && chunk.isNotBlank()) {
                        setFirstTokenAtMs(System.currentTimeMillis())
                    }
                    output.append(chunk)
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate > 220L || output.length < 1000) {
                        lastUiUpdate = now
                        val preview = appendRawContinuation(response, output.toString())
                        _uiState.update {
                            it.copy(
                                status = "Continuing...",
                                lastRawResponse = preview,
                                streamingCode = extractStreamingCode(preview)
                            )
                        }
                    }
                }
            }
            if (result.isFailure) break

            val continued = appendRawContinuation(response, output.toString())
            if (continued.length <= response.length) break
            response = continued
            continuation += 1
        }

        return response
    }

    private fun looksCutOff(text: String): Boolean {
        val trimmed = text.trimEnd()
        if (trimmed.length < 5000) return false
        if (trimmed.endsWith("</action>", ignoreCase = true)) return false
        if (recoverAssistantReplyContent(trimmed)?.complete == false) return true
        if (trimmed.contains("<action", ignoreCase = true)) return true
        val recovered = recoverWriteFileActions(trimmed)
        return recovered.isNotEmpty() && recovered.any { !isCompleteEnoughForWriting(it) }
    }

    private fun appendRawContinuation(current: String, continuation: String): String {
        val cleaned = cleanRawContinuationForOpenAction(current, continuation).trimStart()
        if (cleaned.isBlank()) return current

        val overlap = minOf(current.length, cleaned.length, 1000)
            .downTo(20)
            .firstOrNull { size -> current.takeLast(size) == cleaned.take(size) }
            ?: 0
        val suffix = cleaned.drop(overlap)
        if (suffix.isBlank()) return current
        return current + suffix
    }

    private fun cleanRawContinuationForOpenAction(current: String, continuation: String): String {
        var next = continuation.trimStart()
        if (!current.contains("<action", ignoreCase = true) || current.trimEnd().endsWith("</action>", ignoreCase = true)) {
            return next
        }

        val contentMatch = Regex(
            pattern = """(?is)^<action\s+name=["'](?:write_file|reply)["']>\s*(?:<path>.*?</path>\s*)?<content>"""
        ).find(next)
        if (contentMatch != null) {
            next = next.substring(contentMatch.range.last + 1)
        }
        return next
    }

    private fun persistActiveConversation(context: Context, messages: List<ChatMessage>) {
        val id = uiState.value.activeConversationId ?: return
        val old = conversations.firstOrNull { it.id == id } ?: ChatConversation(id, "New chat", System.currentTimeMillis(), emptyList())
        val firstUser = messages.firstOrNull { it.role == "user" }?.text?.trim().orEmpty()
        val title = if (old.title == "New chat" && firstUser.isNotBlank()) firstUser.take(42) else old.title
        val updated = old.copy(title = title, updatedAt = System.currentTimeMillis(), messages = messages)
        conversations = (conversations.filterNot { it.id == id } + updated).sortedByDescending { it.updatedAt }.toMutableList()
        saveConversationFile(context, updated)
        _uiState.update { it.copy(conversations = conversationInfos(), activeConversationId = id, messages = messages) }
    }

    private fun buildGenerationMetrics(startedAtMs: Long, firstTokenAtMs: Long?): String {
        val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
        val elapsedSec = elapsedMs / 1000.0
        val ttftMs = (firstTokenAtMs?.minus(startedAtMs) ?: elapsedMs).coerceAtLeast(1L)
        val ttftSec = ttftMs / 1000.0
        val base = "• ${String.format(Locale.US, "%.2f", elapsedSec)}s • ~${String.format(Locale.US, "%.2f", ttftSec)}s TTFT"
        val tokensPerSecond = turnTokensPerSecond() ?: return base
        _uiState.update { it.copy(lastTokensPerSecond = tokensPerSecond) }
        return "$base • ${String.format(Locale.US, "%.1f", tokensPerSecond)} tok/s"
    }

    /**
     * Folds messages older than the last [RECENT_MESSAGES_KEPT] into the chat's running summary,
     * like ChatGPT's memory of earlier conversation. Runs a short extra model call; on failure the
     * chat just keeps its previous summary.
     */
    private suspend fun compactHistoryIfNeeded(context: Context, messages: List<ChatMessage>) {
        val id = uiState.value.activeConversationId ?: return
        val conversation = conversations.firstOrNull { it.id == id } ?: return
        val foldEnd = messages.size - RECENT_MESSAGES_KEPT
        if (foldEnd <= conversation.summarizedCount) return

        _uiState.update {
            it.copy(
                status = "Summarizing earlier messages…",
                streamingReply = if (it.activeKind == CHAT_KIND_ASSISTANT && it.streamingReply.isNullOrBlank()) "_Summarizing earlier messages to make room…_" else it.streamingReply
            )
        }
        val transcript = messages.subList(conversation.summarizedCount, foldEnd)
            .joinToString("\n") { "${it.role}: ${it.text.take(1200)}" }
            .takeLast(snapshotCharBudget(activeTokenWindow) / 2)
        val prompt = """
Update the running summary of this chat so work on the project can continue without the old messages.

Previous summary:
${conversation.summary.ifBlank { "(none yet)" }}

Messages to add to the summary:
$transcript

Write at most 120 words: what the user is building, requirements and preferences they stated, decisions made, and anything still pending. Do not include code. Return one reply action containing only the summary.
        """.trimIndent()

        val output = StringBuilder()
        val ok = runCatching { engine.generate("summary:$id", prompt).collect { output.append(it) } }.isSuccess
        engine.takeGenerationStats() // keep the summary call out of the reply's tokens/s
        engine.resetSession()
        if (!ok) return
        val summary = (recoverAssistantReply(output.toString()) ?: cleanGeneratedFileContent(output.toString()))
            .trim()
            .take(1500)
        if (summary.isBlank() || summary.startsWith("<")) return

        val updated = (conversations.firstOrNull { it.id == id } ?: conversation)
            .copy(summary = summary, summarizedCount = foldEnd)
        conversations = conversations.map { if (it.id == id) updated else it }.toMutableList()
        withContext(Dispatchers.IO) { saveConversationFile(context, updated) }
        _uiState.update { it.copy(chatSummarized = true) }
    }

    private fun absorbGenerationStats() {
        val stats = engine.takeGenerationStats() ?: return
        turnOutputTokens += stats.outputTokens
        turnDecodeSeconds += stats.decodeSeconds
    }

    private fun turnTokensPerSecond(): Double? =
        if (turnOutputTokens > 0 && turnDecodeSeconds > 0.05) turnOutputTokens / turnDecodeSeconds else null

    /** Before a model loads, show the window it will get so the chat's context meter is visible. */
    private fun refreshPredictedWindow(context: Context) {
        if (uiState.value.gemmaLoaded) return
        val file = modelFile(context)
        val window = if (file.exists()) safeTokenWindow(context, file, savedModelContextTokens(context, savedModelName(context))) else null
        _uiState.update { it.copy(contextWindowTokens = window) }
        refreshContextUsage()
    }

    /**
     * How much of the window the active chat uses: what its open session holds, or, with no session
     * (app restarted, model reloaded, another chat in use), an estimate of the history it will resend.
     */
    private fun refreshContextUsage() {
        val window = uiState.value.contextWindowTokens ?: return
        val sessionKey = uiState.value.activeConversationId.orEmpty()
        val used = engine.remainingTokens(sessionKey)?.let { window - it } ?: estimatedHistoryTokens()
        _uiState.update { it.copy(contextUsedTokens = used.coerceIn(0, window)) }
    }

    /** Rough tokens the active chat's history takes when it's sent to a fresh session (~4 chars a token). */
    private fun estimatedHistoryTokens(): Int {
        val messages = uiState.value.messages
        if (messages.isEmpty()) return 0
        val conversation = conversations.firstOrNull { it.id == uiState.value.activeConversationId }
        val summarized = conversation?.summarizedCount?.coerceAtMost(messages.size) ?: 0
        val summaryChars = if (summarized > 0) conversation?.summary.orEmpty().length else 0
        val historyChars = messages.drop(summarized).takeLast(RECENT_MESSAGES_KEPT).sumOf { it.text.take(2000).length }
        return (summaryChars + historyChars) / 4
    }

    private fun conversationInfos(): List<ConversationInfo> = conversations
        .sortedByDescending { it.updatedAt }
        .map { ConversationInfo(it.id, it.title, it.updatedAt, it.messages.size, it.kind, it.projectKey) }

    override fun onCleared() {
        super.onCleared()
        speaker?.shutdown()
        engine.close()
        multimodalEngine?.close()
        multimodalEngine = null
    }
}
