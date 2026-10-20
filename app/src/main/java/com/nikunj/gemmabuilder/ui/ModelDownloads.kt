package com.nikunj.gemmabuilder

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
                    .verticalScroll(rememberScrollState()),
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

                ModelSection(
                    title = "LiteRT · fastest, runs on GPU",
                    models = MODEL_CATALOG.filter { !it.isGguf && !it.hidden && !it.isImage },
                    state = state,
                    ramGb = ramGb,
                    recommended = recommendedId,
                    onDownload = startDownload,
                    onCancel = onCancel,
                    onUse = onUse,
                    onDelete = { pendingDelete = it },
                    onContextChange = onContextChange
                )
                Text("GGUF · llama.cpp, runs on CPU", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                KvCacheChoice(state.kvCacheType, onKvCacheTypeChange)
                ModelSection(
                    title = "",
                    models = MODEL_CATALOG.filter { it.isGguf && !it.hidden && !it.isImage },
                    state = state,
                    ramGb = ramGb,
                    recommended = recommendedId,
                    onDownload = startDownload,
                    onCancel = onCancel,
                    onUse = onUse,
                    onDelete = { pendingDelete = it },
                    onContextChange = onContextChange
                )
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
    onImageSettingsChange: (String, ImageOptions) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    models.forEach { model ->
        val isActive = if (model.isImage) state.activeImageModelId == model.id else state.activeModelPath?.endsWith("/${model.fileName}") == true
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
            onDelete = { onDelete(model) }
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
    onDelete: () -> Unit
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
            "How a GGUF model stores the chat it is reading. Q8_0 takes about half the memory of F16 with almost no change in answers; Q4_0 takes about a quarter, so very long chats fit, but answers can get a little worse.",
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
