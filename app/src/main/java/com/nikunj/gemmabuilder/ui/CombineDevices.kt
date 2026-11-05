package com.nikunj.gemmabuilder

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Combine devices (experimental): this device can lend its memory to another Mochu, or use other
 * devices' memory so a GGUF model too big for one device runs across several.
 */
@Composable
fun CombineDevicesSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(ClusterWorkerService.readStatus(context)) }
    var useOthers by remember { mutableStateOf(DeviceCluster.enabled(context)) }
    var paired by remember { mutableStateOf(DeviceCluster.paired(context)) }
    val nearby by DeviceCluster.nearby.collectAsState()
    var pairing by remember { mutableStateOf<NearbyDevice?>(null) }

    // The worker runs in its own process and reports through a file.
    LaunchedEffect(Unit) {
        while (true) {
            sharing = withContext(Dispatchers.IO) { ClusterWorkerService.readStatus(context) }
            delay(2_000)
        }
    }
    LaunchedEffect(useOthers) {
        if (useOthers) DeviceCluster.startDiscovery(context) else DeviceCluster.stopDiscovery(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Combine devices (experimental)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Run a GGUF model too big for one device across your phones and tablets on the same Wi-Fi. " +
                "Each device holds part of the model. It's slower than one device, so use it only for models that don't fit. " +
                "Keep the devices close to the router, plugged in, with Mochu open.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- This device as a helper ----
        val state = sharing.optString("state")
        val on = state == "running" || state == "starting"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share this device")
                Text(
                    "Lend this device's memory to Mochu on another phone or tablet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = on, onCheckedChange = { want ->
                if (want) ClusterWorkerService.start(context) else ClusterWorkerService.stop(context)
                sharing = sharing.put("state", if (want) "starting" else "stopped")
            })
        }
        if (on) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val peer = sharing.optString("peer")
                    if (peer.isNotBlank()) {
                        // Connected: it shares with this one device only, so the code isn't needed.
                        Text("✓ Connected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            sharing.optString("peerName").ifBlank { peer },
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "This device holds part of the model for it. Keep Mochu open and the screen on.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text("Pairing code", style = MaterialTheme.typography.labelMedium)
                        Text(
                            sharing.optString("code").ifBlank { ClusterWorkerService.pairingCode(context) },
                            fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp
                        )
                        Text(
                            if (state == "starting") "Starting…"
                            else "Ready · ${memoryText(sharing.optLong("freeMb"), sharing.optLong("totalMb"))} · enter this code on the other device",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        } else if (state == "error") {
            Text("Sharing stopped: ${sharing.optString("error")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        // Model parts other devices sent here are kept so the next load is quick; each model can be removed.
        var cached by remember { mutableStateOf<List<ClusterCache.Entry>>(emptyList()) }
        LaunchedEffect(on) { cached = withContext(Dispatchers.IO) { ClusterCache.list(context) } }
        if (cached.isNotEmpty()) {
            Text("Model parts kept here", style = MaterialTheme.typography.labelLarge)
            Text(
                if (on) "Kept so the next shared load is quick. Stop sharing to delete them." else "Kept so the next shared load is quick.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            cached.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${entry.model} · ${String.format(Locale.US, "%.1f", entry.bytes / 1073741824.0)} GB",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(enabled = !on, onClick = {
                        scope.launch {
                            cached = withContext(Dispatchers.IO) { ClusterCache.delete(context, entry); ClusterCache.list(context) }
                        }
                    }) { Text("Delete cache") }
                }
            }
        }

        // ---- Other devices helping this one ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Create a cluster with multiple devices")
                Text(
                    "Paired devices that are sharing each hold part of a GGUF model. Applies the next time a GGUF model loads.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = useOthers, onCheckedChange = { useOthers = it; DeviceCluster.setEnabled(context, it) })
        }
        if (useOthers) {
            // Free memory here and on each paired device, checked every few seconds.
            var localFreeMb by remember { mutableStateOf(freeMemoryMb(context)) }
            val localTotalMb = remember { totalMemoryMb(context) }
            var deviceMemory by remember { mutableStateOf<Map<String, Pair<Long, Long>?>>(emptyMap()) }
            LaunchedEffect(paired) {
                while (true) {
                    localFreeMb = freeMemoryMb(context)
                    deviceMemory = withContext(Dispatchers.IO) {
                        paired.associate { d -> d.id to DeviceCluster.askMemory(d.host, d.port, d.code)?.takeIf { it.first >= 0 } }
                    }
                    delay(5_000)
                }
            }
            CombinedMemory(
                listOf(DeviceMemory("This device", localFreeMb, localTotalMb)) +
                    paired.map { d -> deviceMemory[d.id].let { m -> DeviceMemory(d.name, m?.first, m?.second ?: 0L) } }
            )
            Text(
                "You can pair several devices; each one sharing holds part of the model.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val connected by DeviceCluster.connected.collectAsState()
            if (connected.isNotEmpty()) {
                Text(
                    "Model running on ${connected.size + 1} devices",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            paired.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${device.name} · ${device.host}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (device.id in connected) "● Connected · holding part of the model" else "Paired · used when a GGUF model loads",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (device.id in connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { DeviceCluster.forget(context, device.id); paired = DeviceCluster.paired(context) }) { Text("Forget") }
                }
            }
            val unpaired = nearby.filter { n -> paired.none { it.id == n.id } }
            if (unpaired.isEmpty() && paired.isEmpty()) {
                Text("Looking for devices… Turn on Share this device on the other one.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            unpaired.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${device.name} · ${String.format(Locale.US, "%.1f", device.freeMb / 1024.0)} GB free",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { pairing = device }) { Text("Pair") }
                }
            }
        }
    }

    pairing?.let { device ->
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pairing = null },
            title = { Text("Pair with ${device.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the 4-digit code shown on ${device.name}.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(4); error = null },
                        singleLine = true,
                        label = { Text("Pairing code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = code.length == 4 && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { DeviceCluster.pair(context, device, code) }
                        busy = false
                        if (result == null) {
                            paired = DeviceCluster.paired(context)
                            pairing = null
                        } else error = result
                    }
                }) { Text(if (busy) "Pairing…" else "Pair") }
            },
            dismissButton = { TextButton(onClick = { pairing = null }) { Text("Cancel") } }
        )
    }
}

/**
 * Free memory for a model across devices: a bar split by device and the approximate total. Android
 * keeps some memory back, so the model should stay a bit under the total.
 */
/** One device's memory: [freeMb] null when it isn't sharing; [totalMb] 0 when unknown. */
private data class DeviceMemory(val name: String, val freeMb: Long?, val totalMb: Long)

private fun gbText(mb: Long) = String.format(Locale.US, if (mb >= 10 * 1024) "%.0f GB" else "%.1f GB", mb / 1024.0)

/** "6.6 GB free / 12 GB", or just "6.6 GB free" when the total is unknown. */
private fun memoryText(freeMb: Long, totalMb: Long) =
    "${gbText(freeMb)} free" + if (totalMb > 0) " / ${String.format(Locale.US, "%.0f", Math.ceil(totalMb / 1024.0))} GB" else ""

@Composable
private fun CombinedMemory(devices: List<DeviceMemory>) {
    val parts = devices.filter { it.freeMb != null }.map { it.name to it.freeMb!! }
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    val totalRam = devices.filter { it.freeMb != null }.sumOf { it.totalMb }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error
    )
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Memory for models", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("≈ ${memoryText(total, totalRam)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                parts.forEachIndexed { i, (_, mb) ->
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .weight(mb.coerceAtLeast(1).toFloat())
                            .fillMaxHeight()
                            .background(colors[i % colors.size])
                    )
                }
            }
            devices.filter { it.freeMb != null }.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(colors[i % colors.size]))
                    Text("  ${d.name}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(memoryText(d.freeMb!!, d.totalMb), style = MaterialTheme.typography.labelMedium)
                }
            }
            devices.filter { it.freeMb == null }.forEach { d ->
                Text("${d.name}: not sharing right now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Approximate: Android keeps some memory back, so pick a model a bit smaller than this.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
