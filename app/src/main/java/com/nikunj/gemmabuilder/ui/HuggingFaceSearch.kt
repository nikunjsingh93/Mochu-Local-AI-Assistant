package com.nikunj.gemmabuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

/** Models tab: search Hugging Face for GGUF models and download one file; it joins the imported models. */
@Composable
fun HuggingFaceSearchCard(
    state: BuilderUiState,
    ramGb: Double,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<HfRepo>?>(null) }
    var openRepo by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<HfFile>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val search = {
        if (query.isNotBlank() && !busy) {
            busy = true; error = null; openRepo = null; files = null
            scope.launch {
                runCatching { searchHuggingFace(query) }
                    .onSuccess { repos = it }
                    .onFailure { error = "Search failed: ${it.message ?: "no connection"}" }
                busy = false
            }
        }
    }
    val open = { repo: String ->
        if (openRepo == repo) { openRepo = null; files = null } else {
            openRepo = repo; files = null; error = null; busy = true
            scope.launch {
                runCatching { huggingFaceFiles(repo) }
                    .onSuccess { files = it }
                    .onFailure { error = "Couldn't list files: ${it.message ?: "no connection"}" }
                busy = false
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Search Hugging Face", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Find GGUF models, like \"LFM2\" or \"Qwen3\". A downloaded file appears with your imported models.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Model name") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() })
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { search() }, enabled = query.isNotBlank() && !busy) { Text("Search") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            repos?.let { list ->
                if (list.isEmpty()) Text("No GGUF models found.", style = MaterialTheme.typography.bodySmall)
                list.forEach { repo ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !repo.gated) { open(repo.id) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            (if (openRepo == repo.id) "▾ " else "▸ ") + repo.id,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (repo.gated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (repo.gated) "Needs Hugging Face access — not available here"
                            else listOfNotNull("${count(repo.downloads)} downloads · ${count(repo.likes)} likes", repoSize(repo)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (openRepo == repo.id) files?.let { fs ->
                        if (fs.isEmpty()) Text("No downloadable GGUF files in this repository.", style = MaterialTheme.typography.bodySmall)
                        fs.forEach { file -> HfFileRow(repo.id, file, state, ramGb, onDownload, onCancel, context) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HfFileRow(
    repo: String, file: HfFile, state: BuilderUiState, ramGb: Double,
    onDownload: (String) -> Unit, onCancel: (String) -> Unit, context: android.content.Context
) {
    val id = HfDownloads.idFor(repo, file)
    val download = state.modelDownloads[id]
    val have = file.fileName in state.importedModels
    val gb = file.sizeBytes / 1e9
    // Rough fit: the model plus about 1.5 GB for the app and context must fit in this device's RAM.
    val fit = when {
        gb + 1.5 <= ramGb * 0.75 -> "fits this device"
        gb + 1.5 <= ramGb * 2.5 -> "too big alone · try Combine devices"
        else -> "too big"
    }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(file.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                parameterCount(file.fileName) ?: parameterCount(repo),
                String.format(Locale.US, "%.2f GB", gb),
                fit,
                activeParameters(file.fileName)?.let { "MoE, $it active (fast for its size)" }
                    ?: "MoE (fast for its size)".takeIf { isMixtureOfExperts(file.fileName) || isMixtureOfExperts(repo) }
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            have -> Text("✓ Downloaded — in your imported models", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            download != null && download.phase in setOf(DownloadPhase.Queued, DownloadPhase.WaitingForNetwork, DownloadPhase.Downloading, DownloadPhase.Verifying) -> {
                LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format(Locale.US, "%.2f / %.2f GB · %d%%", gb * download.progress, gb, (download.progress * 100).toInt()) +
                            (download.message?.takeIf { download.phase != DownloadPhase.Downloading }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onCancel(id) }) { Text("Cancel") }
                }
            }
            else -> {
                download?.message?.takeIf { download.phase == DownloadPhase.Failed }?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { HfDownloads.register(context, repo, file); onDownload(id) }) { Text("Download") }
            }
        }
    }
}

private fun count(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1e3)
    else -> n.toString()
}

/** Total parameters from a name like "Qwen3.6-35B-A3B" (35B) or "LFM2.5-350M" (350M); the "A3B" active part is skipped. */
fun parameterCount(name: String): String? =
    Regex("""(?<![A-Za-z\d.])(\d+(?:\.\d+)?)([BM])(?![A-Za-z\d])""", RegexOption.IGNORE_CASE).find(name)
        ?.let { "${it.groupValues[1]}${it.groupValues[2].uppercase(Locale.US)} params" }

/** Active parameters of a mixture-of-experts name like "35B-A3B" (3B). */
private fun activeParameters(name: String): String? =
    Regex("""(?i)[-_]A(\d+(?:\.\d+)?)B(?![A-Za-z\d])""").find(name)?.let { "${it.groupValues[1]}B" }

/** "27B params · ≈16.6 GB at Q4": exact count from the GGUF metadata, else from the name; the size assumes Q4_K_M (about 4.85 bits per weight). */
private fun repoSize(repo: HfRepo): String? {
    if (repo.parameters <= 0) return parameterCount(repo.id)
    val p = repo.parameters.toDouble()
    val params = if (p >= 1e9) String.format(Locale.US, if (p >= 1e10) "%.0fB" else "%.1fB", p / 1e9) else String.format(Locale.US, "%.0fM", p / 1e6)
    return String.format(Locale.US, "%s params · ≈%.1f GB at Q4", params, p * 4.85 / 8 / 1e9)
}
