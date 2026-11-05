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
                    Text("Pairing code", style = MaterialTheme.typography.labelMedium)
                    Text(
                        sharing.optString("code").ifBlank { ClusterWorkerService.pairingCode(context) },
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp
                    )
                    val peer = sharing.optString("peer")
                    Text(
                        when {
                            state == "starting" -> "Starting…"
                            peer.isNotBlank() -> "Sharing with $peer"
                            else -> "Ready · ${String.format(Locale.US, "%.1f", sharing.optLong("freeMb") / 1024.0)} GB free · enter this code on the other device"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        } else if (state == "error") {
            Text("Sharing stopped: ${sharing.optString("error")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }

        // ---- Other devices helping this one ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use other devices for GGUF models")
                Text(
                    "Paired devices that are sharing hold part of the model. Applies the next time a GGUF model loads.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = useOthers, onCheckedChange = { useOthers = it; DeviceCluster.setEnabled(context, it) })
        }
        if (useOthers) {
            // Free memory here and on each paired device, checked every few seconds.
            var localFreeMb by remember { mutableStateOf(freeMemoryMb(context)) }
            var deviceFreeMb by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
            LaunchedEffect(paired) {
                while (true) {
                    localFreeMb = freeMemoryMb(context)
                    deviceFreeMb = withContext(Dispatchers.IO) {
                        paired.associate { d -> d.id to DeviceCluster.ask(d.host, d.port, d.code)?.takeIf { it >= 0 } }
                    }
                    delay(5_000)
                }
            }
            CombinedMemory(localFreeMb, paired.map { it.name to deviceFreeMb[it.id] })
            Text(
                "You can pair several devices; each one sharing holds part of the model.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            paired.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ ${device.name} · ${device.host}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
@Composable
private fun CombinedMemory(localFreeMb: Long, devices: List<Pair<String, Long?>>) {
    val parts = listOf("This device" to localFreeMb) + devices.mapNotNull { (name, mb) -> mb?.let { name to it } }
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error
    )
    fun gb(mb: Long) = String.format(Locale.US, "%.1f GB", mb / 1024.0)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Memory for models", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("≈ ${gb(total)} free", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
            parts.forEachIndexed { i, (name, mb) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(colors[i % colors.size]))
                    Text("  $name", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(gb(mb), style = MaterialTheme.typography.labelMedium)
                }
            }
            devices.filter { it.second == null }.forEach { (name, _) ->
                Text("$name: not sharing right now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Approximate: Android keeps some memory back, so pick a model a bit smaller than this.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
