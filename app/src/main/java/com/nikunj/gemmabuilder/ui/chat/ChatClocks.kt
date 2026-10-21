package com.nikunj.gemmabuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Timers and the stopwatch started from this chat, live, with their buttons. */
@Composable
fun ChatClocks(chatId: String?, modifier: Modifier = Modifier) {
    if (chatId.isNullOrBlank()) return
    val app = LocalContext.current.applicationContext
    LaunchedEffect(Unit) { TaskRepository.load(app) }
    val tasks by TaskRepository.tasks.collectAsState()
    val stopwatch by TaskRepository.stopwatch.collectAsState()
    val timers = tasks.filter { it.kind == TASK_TIMER && it.chatId == chatId && !it.done }.sortedBy { it.triggerAt }
    val showStopwatch = stopwatch.chatId == chatId && (stopwatch.running || stopwatch.accumulatedMs > 0)
    if (timers.isEmpty() && !showStopwatch) return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stopwatch.running, timers.size) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (stopwatch.running) 100 else 500)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        timers.forEach { timer ->
            val left = (timer.triggerAt - now).coerceAtLeast(0)
            ClockCard(
                time = formatStopwatch(left).substringBeforeLast('.'),
                label = listOf(timer.text.ifBlank { "Timer" }, formatDuration(timer.durationMs)).joinToString(" · "),
                progress = if (timer.durationMs > 0) 1f - left.toFloat() / timer.durationMs else null
            ) {
                TextButton(onClick = { TaskAlarms.cancel(app, timer.id) }) { Text(if (left == 0L) "Dismiss" else "Cancel") }
            }
        }
        if (showStopwatch) {
            ClockCard(time = formatStopwatch(stopwatch.elapsedMs(now)), label = "Stopwatch") {
                if (!stopwatch.running) {
                    TextButton(onClick = {
                        TaskRepository.setStopwatch(app, StopwatchState())
                        TaskNotifications.stopwatch(app, StopwatchState())
                    }) { Text("Reset") }
                }
                FilledTonalButton(onClick = {
                    val next = if (stopwatch.running) {
                        StopwatchState(running = false, accumulatedMs = stopwatch.elapsedMs(), chatId = stopwatch.chatId)
                    } else {
                        stopwatch.copy(running = true, startedAt = System.currentTimeMillis())
                    }
                    TaskRepository.setStopwatch(app, next)
                    TaskNotifications.stopwatch(app, next)
                }) { Text(if (stopwatch.running) "Pause" else "Start") }
            }
        }
    }
}

@Composable
private fun ClockCard(time: String, label: String, progress: Float? = null, buttons: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(time, fontFamily = FontFamily.Monospace, fontSize = 22.sp)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                buttons()
            }
            progress?.let {
                LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp))
            }
        }
    }
}
