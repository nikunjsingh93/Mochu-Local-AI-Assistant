package com.nikunj.gemmabuilder

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
    // Null when shown as a tab, so there is no Back button or bottom inset of its own.
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
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
                    models = MODEL_CATALOG.filter { !it.isGguf },
                    state = state,
                    ramGb = ramGb,
                    onDownload = startDownload,
                    onCancel = onCancel,
                    onUse = onUse,
                    onDelete = { pendingDelete = it }
                )
                ModelSection(
                    title = "GGUF · llama.cpp, runs on CPU",
                    models = MODEL_CATALOG.filter { it.isGguf },
                    state = state,
                    ramGb = ramGb,
                    onDownload = startDownload,
                    onCancel = onCancel,
                    onUse = onUse,
                    onDelete = { pendingDelete = it }
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
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onUse: (String) -> Unit,
    onDelete: (CatalogModel) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    models.forEach { model ->
        val isActive = state.activeModelPath?.endsWith("/${model.fileName}") == true
        ModelCard(
            model = model,
            download = state.modelDownloads[model.id] ?: ModelDownloadState(),
            isActive = isActive,
            usage = if (isActive) state else null,
            lowRam = ramGb + 0.5 < model.minRamGb,
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
    usage: BuilderUiState?,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (isActive) "In use" else formatBytes(model.sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(model.summary, style = MaterialTheme.typography.bodySmall)
            usage?.let { ModelUsage(it) }
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
    val used = (state.contextUsedTokens ?: 0).coerceIn(0, window)
    Text(
        "Context: ${formatTokens(used)} used · ${formatTokens(window - used)} left of ${formatTokens(window)} tokens",
        style = MaterialTheme.typography.labelSmall,
        color = muted
    )
    LinearProgressIndicator(progress = { used.toFloat() / window }, modifier = Modifier.fillMaxWidth())
    state.lastTokensPerSecond?.let { speed ->
        Text(
            "Last reply: ${String.format(Locale.US, "%.1f", speed)} tokens/s",
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
    }
}
