package com.nikunj.gemmabuilder

import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
fun ModelDownloadsScreen(
    state: BuilderUiState,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onUse: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAllowMobileDataChange: (Boolean) -> Unit,
    onImportFile: () -> Unit,
    onContextChange: (String, Int) -> Unit,
    onKvCacheTypeChange: (KvCacheType) -> Unit,
    onImageSettingsChange: (String, ImageOptions) -> Unit,
    onUseRemote: (RemoteModelConfig) -> Unit,
    onUseImported: (String) -> Unit,
    onDeleteImported: (String) -> Unit,
    onStopRemote: () -> Unit,
    onUnload: () -> Unit,
    // Null when shown as a tab, so there is no Back button or bottom inset of its own.
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val recommendedId = remember(context) { recommendedModel(context).id }
    val ramGb = remember(context) { deviceRamGb(context) }
    var pendingDelete by remember { mutableStateOf<CatalogModel?>(null) }
    var pendingDownload by remember { mutableStateOf<String?>(null) }
    // The progress notification needs this on Android 13+; the download runs either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDownload?.let(onDownload)
        pendingDownload = null
    }
    val startDownload: (String) -> Unit = { id ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = id
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDownload(id)
        }
    }

    if (onDismiss != null) BackHandler(onBack = onDismiss)

    // Picking a model scrolls back up to the Loaded model section.
    val scroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    var loadedY by remember { mutableIntStateOf(0) }
    val showLoaded = { scrollScope.launch { kotlinx.coroutines.delay(150); scroll.animateScrollTo(loadedY) }; Unit }
    val useAndShow: (String) -> Unit = { onUse(it); showLoaded() }
    val useImportedAndShow: (String) -> Unit = { onUseImported(it); showLoaded() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (onDismiss != null) Modifier.navigationBarsPadding() else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onDismiss != null) {
                    OutlinedButton(onClick = onDismiss) { Text("Back") }
                    Spacer(Modifier.width(12.dp))
                }
                Text("Models", style = MaterialTheme.typography.headlineSmall)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Models download from Hugging Face and are checked against a known fingerprint. " +
                        "This phone has ${String.format(Locale.US, "%.0f", ramGb)} GB of RAM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.padding(12.dp)) { ApiServerSetting() }
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.padding(12.dp)) { RemoteModelSetting(state.remoteModel, onUseRemote, onStopRemote) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow mobile data")
                        Text(
                            "Off: downloads wait for Wi-Fi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.allowMobileDataDownloads, onCheckedChange = onAllowMobileDataChange)
                }

                OutlinedButton(onClick = onImportFile, modifier = Modifier.fillMaxWidth()) {
                    Text("Import a .litertlm or .gguf file")
                }
                // The model in use gets its own section at the top; the other sections keep a fixed order.
                LoadedModelSection(
                    state = state, ramGb = ramGb, recommended = recommendedId,
                    onDownload = startDownload, onCancel = onCancel, onUse = useAndShow, onDelete = { pendingDelete = it },
                    onContextChange = onContextChange, onUseImported = useImportedAndShow, onDeleteImported = onDeleteImported,
                    onUnload = onUnload,
                    modifier = Modifier.onGloballyPositioned { loadedY = it.positionInParent().y.toInt() }
                )
                val liteRtSection: @Composable () -> Unit = {
                    ModelSection(
                        title = "LiteRT · fastest, runs on GPU",
                        models = MODEL_CATALOG.filter { !it.isGguf && !it.hidden && !it.isImage },
                        imported = state.importedModels.filterNot(::isGgufModel),
                        state = state,
                        ramGb = ramGb,
                        recommended = recommendedId,
                        onDownload = startDownload,
                        onCancel = onCancel,
                        onUse = useAndShow,
                        onDelete = { pendingDelete = it },
                        onContextChange = onContextChange,
                        onUseImported = useImportedAndShow,
                        onDeleteImported = onDeleteImported
                    )
                }
                val ggufSection: @Composable () -> Unit = {
                    Text("GGUF · llama.cpp, runs on CPU", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    KvCacheChoice(state.kvCacheType, onKvCacheTypeChange)
                    ModelSection(
                        title = "",
                        models = MODEL_CATALOG.filter { it.isGguf && !it.hidden && !it.isImage },
                        imported = state.importedModels.filter(::isGgufModel),
                        state = state,
                        ramGb = ramGb,
                        recommended = recommendedId,
                        onDownload = startDownload,
                        onCancel = onCancel,
                        onUse = useAndShow,
                        onDelete = { pendingDelete = it },
                        onContextChange = onContextChange,
                        onUseImported = useImportedAndShow,
                        onDeleteImported = onDeleteImported
                    )
                }
                liteRtSection()
                ggufSection()
                ModelSection(
                    title = "Image generation · runs on CPU",
                    models = MODEL_CATALOG.filter { it.isImage },
                    state = state,
                    ramGb = ramGb,
                    recommended = recommendedId,
                    onDownload = startDownload,
                    onCancel = onCancel,
                    onUse = onUse,
                    onDelete = { pendingDelete = it },
                    onContextChange = onContextChange,
                    onImageSettingsChange = onImageSettingsChange
                )
            }
        }
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${model.name}?") },
            text = { Text("This frees ${formatBytes(model.sizeBytes)}. You can download it again later.") },
            confirmButton = {
                DangerButton("Delete") { onDelete(model.id); pendingDelete = null }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModelSection(
    title: String,
    models: List<CatalogModel>,
    state: BuilderUiState,
    ramGb: Double,
    recommended: String,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onUse: (String) -> Unit,
    onDelete: (CatalogModel) -> Unit,
    onContextChange: (String, Int) -> Unit,
    onImageSettingsChange: (String, ImageOptions) -> Unit = { _, _ -> },
    imported: List<String> = emptyList(),
    onUseImported: (String) -> Unit = {},
    onDeleteImported: (String) -> Unit = {},
    /** True for the Loaded model section, which shows the active model instead of skipping it. */
    showActive: Boolean = false,
    onUnload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    fun catalogActive(model: CatalogModel) =
        if (model.isImage) state.activeImageModelId == model.id else state.remoteModel == null && state.activeModelPath?.endsWith("/${model.fileName}") == true
    fun importedActive(name: String) = state.remoteModel == null && state.activeModelPath?.endsWith("/imported/$name") == true
    @Composable
    fun importedCard(name: String) = ImportedModelCard(
        name,
        active = importedActive(name),
        contextTokens = state.modelContextTokens[name] ?: 8192,
        onContextChange = { onContextChange(name, it) },
        onUse = { onUseImported(name) },
        onDelete = { onDeleteImported(name) },
        onUnload = onUnload.takeIf { importedActive(name) }
    )
    // The model in use is shown in the Loaded model section instead (image models stay here).
    models.filterNot { !showActive && !it.isImage && catalogActive(it) }.forEach { model ->
        val isActive = catalogActive(model)
        ModelCard(
            model = model,
            download = state.modelDownloads[model.id] ?: ModelDownloadState(),
            isActive = isActive,
            recommended = model.id == recommended,
            usage = if (isActive && !model.isImage) state else null,
            contextTokens = if (model.isImage) null else state.modelContextTokens[model.fileName],
            onContextChange = { onContextChange(model.fileName, it) },
            visionStatus = model.visionId?.let { id -> state.modelDownloads[id] ?: ModelDownloadState() },
            ramEstimate = { tokens -> estimatedRamBytes(context, model, tokens, state.kvCacheType) },
            imageSettings = if (model.isImage) state.imageSettings[model.fileName] else null,
            onImageSettingsChange = { onImageSettingsChange(model.fileName, it) },
            // Phones report less than the RAM they are sold with: a 12 GB phone shows about 10.9 GB.
            lowRam = ramGb + 1.5 < model.minRamGb,
            onDownload = { onDownload(model.id) },
            onCancel = { onCancel(model.id) },
            onUse = { onUse(model.id) },
            onDelete = { onDelete(model) },
            onUnload = onUnload.takeIf { isActive && !model.isImage }
        )
    }
    imported.filter { showActive || !importedActive(it) }.forEach { importedCard(it) }
}

/** The chat model in use, on its own at the top of the Models page. */
@Composable
private fun LoadedModelSection(
    state: BuilderUiState,
    ramGb: Double,
    recommended: String,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onUse: (String) -> Unit,
    onDelete: (CatalogModel) -> Unit,
    onContextChange: (String, Int) -> Unit,
    onUseImported: (String) -> Unit,
    onDeleteImported: (String) -> Unit,
    onUnload: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.remoteModel != null) return
    val path = state.activeModelPath ?: return
    val catalog = MODEL_CATALOG.firstOrNull { !it.isImage && path.endsWith("/${it.fileName}") }
    val imported = state.importedModels.firstOrNull { path.endsWith("/imported/$it") }
    if (catalog == null && imported == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Loaded model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    ModelSection(
        title = "",
        models = listOfNotNull(catalog),
        imported = listOfNotNull(imported),
        showActive = true,
        state = state, ramGb = ramGb, recommended = recommended,
        onDownload = onDownload, onCancel = onCancel, onUse = onUse, onDelete = onDelete,
        onContextChange = onContextChange, onUseImported = onUseImported, onDeleteImported = onDeleteImported,
        onUnload = onUnload.takeIf { state.gemmaLoaded }
    )
    }
}

@Composable
private fun ModelCard(
    model: CatalogModel,
    download: ModelDownloadState,
    isActive: Boolean,
    recommended: Boolean,
    usage: BuilderUiState?,
    contextTokens: Int?,
    onContextChange: (Int) -> Unit,
    visionStatus: ModelDownloadState?,
    ramEstimate: (Int) -> Long?,
    imageSettings: ImageOptions?,
    onImageSettingsChange: (ImageOptions) -> Unit,
    lowRam: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    onUnload: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (recommended || isActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isActive) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                            Text(
                                "In use",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (recommended) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "Recommended for this phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    formatBytes(model.sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CapabilityBadges(modelCapabilities(model.fileName))
            Text(model.summary, style = MaterialTheme.typography.bodySmall)
            usage?.let { ModelUsage(it) }
            // Photo support downloads with its model; only its progress shows here.
            visionStatus?.let { visionDownload ->
                when (visionDownload.phase) {
                    DownloadPhase.Downloaded -> Text("Sees photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    in ACTIVE_DOWNLOAD_PHASES -> {
                        Text("Photo support: ${visionDownload.message ?: "downloading…"}", style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(progress = { visionDownload.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> Unit
                }
            }
            if (download.phase == DownloadPhase.Downloaded && imageSettings != null) {
                ImageSettings(model, imageSettings, onImageSettingsChange)
            }
            if (download.phase == DownloadPhase.Downloaded && contextTokens != null) {
                ContextSlider(contextTokens, onContextChange, Modifier.padding(top = 4.dp), model.maxContext, ramEstimate)
            }
            if (lowRam) {
                Text(
                    "Needs about ${model.minRamGb} GB of RAM; it may not load on this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (download.phase in ACTIVE_DOWNLOAD_PHASES || download.partialBytes > 0) {
                if (download.phase == DownloadPhase.Verifying || download.phase == DownloadPhase.Queued) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            download.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (download.phase == DownloadPhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (download.phase) {
                    DownloadPhase.NotDownloaded, DownloadPhase.Failed -> {
                        val resumable = download.partialBytes > 0
                        Button(onClick = onDownload) {
                            Text(
                                when {
                                    resumable -> "Resume"
                                    download.phase == DownloadPhase.Failed -> "Retry"
                                    else -> "Download"
                                }
                            )
                        }
                        if (resumable) OutlinedButton(onClick = onCancel) { Text("Discard") }
                    }
                    DownloadPhase.Queued, DownloadPhase.WaitingForNetwork, DownloadPhase.Downloading ->
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    DownloadPhase.Verifying -> Unit
                    DownloadPhase.Downloaded -> {
                        if (!isActive) Button(onClick = onUse) { Text("Use") }
                        onUnload?.let { OutlinedButton(onClick = it) { Text("Unload") } }
                        DangerOutlinedButton("Delete", onDelete)
                    }
                }
            }
        }
    }
}

/** Context use and speed for the model in use. */
@Composable
private fun ModelUsage(state: BuilderUiState) {
    val window = state.contextWindowTokens
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!state.gemmaLoaded || window == null) {
        Text("Loads when you send a message.", style = MaterialTheme.typography.labelSmall, color = muted)
        return
    }
    // Context use belongs to a chat, so it's shown in Chat rather than here.
    Text(
        state.lastTokensPerSecond?.let { "Loaded · last reply ${String.format(Locale.US, "%.1f", it)} tokens/s" } ?: "Loaded.",
        style = MaterialTheme.typography.labelSmall,
        color = muted
    )
}

/** KV cache number type for GGUF models. */
@Composable
private fun KvCacheChoice(current: KvCacheType, onChange: (KvCacheType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("KV cache", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            KvCacheType.entries.forEach { type ->
                FilterChip(selected = type == current, onClick = { onChange(type) }, label = { Text(type.label) })
            }
        }
        Text(
            "How a GGUF model stores the chat it is reading. Q8_0 takes about half the memory of F16 with almost no change in answers, so longer chats fit.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Size, aspect ratio and steps for an image model. More steps add detail; bigger takes much longer. */
@Composable
private fun ImageSettings(model: CatalogModel, options: ImageOptions, onChange: (ImageOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        val (width, height) = options.dimensions
        Text("Image size · $width×$height", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IMAGE_SIZE_OPTIONS.forEach { option ->
                FilterChip(selected = option == options.size, onClick = { onChange(options.copy(size = option)) }, label = { Text("$option") })
            }
        }
        Text("Aspect ratio", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            IMAGE_ASPECTS.forEach { aspect ->
                FilterChip(selected = aspect == options.aspect, onClick = { onChange(options.copy(aspect = aspect)) }, label = { Text(aspect) })
            }
        }
        Text("Steps: ${options.steps}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        Text(
            "More steps add detail but take longer. ${model.name} works best around ${model.imageSteps}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = options.steps.toFloat(),
            onValueChange = { onChange(options.copy(steps = kotlin.math.round(it).toInt())) },
            valueRange = 1f..model.imageMaxSteps.toFloat(),
            steps = (model.imageMaxSteps - 2).coerceAtLeast(0)
        )
    }
}

/** A model on the user's own computer or server (Ollama, LM Studio, llama.cpp, vLLM, another Mochu). */
@Composable
private fun RemoteModelSetting(active: RemoteModelConfig?, onUse: (RemoteModelConfig) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { savedRemoteModelConfig(context) }
    var url by remember { mutableStateOf(saved?.baseUrl ?: "") }
    var key by remember { mutableStateOf(saved?.apiKey ?: "") }
    var contextText by remember { mutableStateOf(saved?.contextTokens?.takeIf { it > 0 }?.toString().orEmpty()) }
    var fallback by remember { mutableStateOf(remoteFallbackEnabled(context)) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var chosen by remember { mutableStateOf(saved?.model) }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    // Servers outside your network get your messages: ask once per address before connecting.
    var confirmOutside by remember { mutableStateOf<String?>(null) }
    var allowedOutside by remember { mutableStateOf(allowedRemoteHosts(context)) }
    var connectAgain by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(active != null) }
    fun connectNow() {
            val base = normalizeBaseUrl(url)
            checking = true
            message = null
            scope.launch {
                val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { isLocalNetworkHost(base) }
                if (!local && base !in allowedOutside) {
                    confirmOutside = base
                } else {
                    runCatching { listRemoteModels(base, key) }
                        .onSuccess { list ->
                            models = list
                            url = base
                            if (chosen !in list) chosen = list.firstOrNull()
                            message = if (list.isEmpty()) "Connected, but the server lists no models. Load one on the server first." else null
                        }
                        .onFailure { e -> message = "Couldn't connect: ${e.message?.trimEnd('.') ?: "no answer"}. Check the address and that the server allows connections from your network." }
                }
                checking = false
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Load a model from your home server")
                Text(
                    "Use a bigger model running on your computer on the same Wi-Fi, through its OpenAI-compatible API (Ollama, LM Studio, llama.cpp server, vLLM, or another phone with Mochu's API server).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Off by default; turning it off while a server model is in use goes back to the phone's.
            Switch(checked = enabled, onCheckedChange = { on -> enabled = on; if (!on && active != null) onStop() })
        }
        if (enabled) {
        if (active != null) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                Text("In use: ${active.model}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
        OutlinedTextField(
            value = url, onValueChange = { url = it; models = emptyList(); message = null }, singleLine = true,
            label = { Text("Server address") }, placeholder = { Text("192.168.1.20:11434") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it.trim() }, singleLine = true,
            label = { Text("API key (if the server needs one)") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = contextText, onValueChange = { contextText = it.filter(Char::isDigit).take(7) }, singleLine = true,
            label = { Text("Context size in tokens (optional)") },
            placeholder = { Text("Ask the server") },
            supportingText = { Text("Leave empty to use what the server reports. Set it (like 131072) if the context meter shows the wrong size.") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("If my server can't be reached, use the phone's model")
                Text(
                    "Mochu checks the server before each message. When it doesn't answer (say you left home and it has a home-network address), the phone's model replies instead, and the server is used again once it's back.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = fallback, onCheckedChange = { fallback = it; setRemoteFallbackEnabled(context, it) })
        }
        Text(
            "Ollama: port 11434 · LM Studio: 1234 · llama.cpp: 8080 · Unsloth Studio: 8888 · Mochu: 8080. A domain, or a cloud API like api.openai.com with your key, works too.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Allowing an outside address continues the same Connect.
            LaunchedEffect(connectAgain) { if (connectAgain > 0) connectNow() }
            OutlinedButton(enabled = url.isNotBlank() && !checking, onClick = { connectNow() }) { Text(if (checking) "Connecting…" else "Connect") }
            if (active != null) TextButton(onClick = onStop) { Text("Use phone model") }
        }
        if (models.isNotEmpty()) {
            Text("Models on the server", style = MaterialTheme.typography.labelLarge)
            models.forEach { id ->
                Row(Modifier.fillMaxWidth().clickable { chosen = id }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = chosen == id, onClick = { chosen = id })
                    Text(id, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(enabled = chosen != null, onClick = { chosen?.let { onUse(RemoteModelConfig(normalizeBaseUrl(url), key, it, contextText.toIntOrNull() ?: 0)) } }) { Text("Use this model") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        }
    }
    confirmOutside?.let { base ->
        AlertDialog(
            onDismissRequest = { confirmOutside = null },
            title = { Text("Send chats to this server?") },
            text = { Text("$base isn't on your local network. While you use its model, your messages (and any photos or documents you attach) are sent to it over the internet, so they no longer stay on your devices.") },
            confirmButton = { TextButton(onClick = { allowRemoteHost(context, base); allowedOutside = allowedOutside + base; confirmOutside = null; connectAgain++ }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { confirmOutside = null }) { Text("Cancel") } }
        )
    }
}

/** Small pills for what a model can do: photos, thinking, coding, image creation. */
@Composable
fun CapabilityBadges(caps: List<ModelCapability>) {
    if (caps.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        caps.forEach { cap ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Icon(
                        when (cap) {
                            ModelCapability.Photos -> Icons.Outlined.Visibility
                            ModelCapability.Thinking -> Icons.Outlined.Lightbulb
                            ModelCapability.Code -> Icons.Outlined.Code
                            ModelCapability.Images -> Icons.Outlined.Image
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(cap.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** The model imported from a file, with what its name suggests it can do. */
@Composable
private fun ImportedModelCard(
    name: String,
    active: Boolean,
    contextTokens: Int,
    onContextChange: (Int) -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    onUnload: (() -> Unit)? = null
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${name.substringBeforeLast('.')}?") },
            text = { Text("This removes the imported file from the app. You can import it again later.") },
            confirmButton = { DangerButton("Delete") { confirmDelete = false; onDelete() } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (active) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                    Text("In use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
            Text(name.substringBeforeLast('.'), fontWeight = FontWeight.Bold)
            Text(
                "Imported ${if (isGgufModel(name)) "GGUF · runs on CPU" else "LiteRT · runs on GPU"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CapabilityBadges(modelCapabilities(name))
            ContextSlider(contextTokens, onContextChange, Modifier.padding(top = 4.dp), maxTokens = 32768)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active) Button(onClick = onUse) { Text("Use") }
                onUnload?.let { OutlinedButton(onClick = it) { Text("Unload") } }
                DangerOutlinedButton("Delete") { confirmDelete = true }
            }
        }
    }
}
