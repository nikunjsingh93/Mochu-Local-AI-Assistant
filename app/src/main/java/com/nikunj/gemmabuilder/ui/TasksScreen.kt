package com.nikunj.gemmabuilder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZonedDateTime

/** Scheduled assistant tasks, memory and reminders. Timers and the stopwatch live in the chat that started them. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    memories: List<String>,
    memoryEnabled: Boolean,
    onMemoryEnabledChange: (Boolean) -> Unit,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext
    // The Chat box underneath keeps focus otherwise, and its keyboard covers this screen.
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        TaskRepository.load(app)
    }
    val tasks by TaskRepository.tasks.collectAsState()
    BackHandler(onBack = onDismiss)

    // One clock for every countdown on the screen.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    // Permissions are re-checked when the user comes back from system settings.
    var exactAllowed by remember { mutableStateOf(TaskAlarms.canBeExact(app)) }
    var notificationsAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            exactAllowed = TaskAlarms.canBeExact(app)
            notificationsAllowed = ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }
    fun schedule(task: ScheduledTask) {
        if (!notificationsAllowed) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        TaskAlarms.schedule(app, task)
    }

    var newTaskKind by rememberSaveable { mutableStateOf<String?>(null) }
    // Deleting a reminder or scheduled task asks first; cancelling a running timer doesn't.
    var pendingDelete by remember { mutableStateOf<ScheduledTask?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(12.dp))
                Text("Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!notificationsAllowed) {
                    Notice(
                        "Notifications are off, so reminders and tasks can't alert you.",
                        "Allow"
                    ) { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                if (!exactAllowed) {
                    Notice(
                        "Reminders and tasks may go off a few minutes late until you allow alarms for this app.",
                        "Allow alarms"
                    ) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
                Text(
                    "Tip: just ask in Chat, like \"every day at 8am give me the latest tech news\", \"remind me at 5pm to call mom\" " +
                        "or \"set a timer for 10 minutes\" (timers and the stopwatch show in that chat).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---- Scheduled assistant tasks ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Scheduled assistant tasks", Modifier.weight(1f))
                    TextButton(onClick = { newTaskKind = TASK_AI }) { Text("Add") }
                }
                Text(
                    "The assistant runs these on this phone at the time you pick (reading websites if needed) and sends the answer as a notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val assistantTasks = tasks.filter { it.kind == TASK_AI }.sortedWith(compareBy({ it.done }, { it.triggerAt }))
                if (assistantTasks.isEmpty()) Empty("No scheduled tasks.")
                assistantTasks.forEach { task ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                                    Text(task.text, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (task.done) "Ran ${formatTaskTime(task.lastRunAt)}" else scheduleLabel(task),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { pendingDelete = task }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Row {
                                TextButton(onClick = {
                                    if (!notificationsAllowed) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    // Open the task's chat right away so the answer can be watched as it comes in.
                                    TaskAlarms.runNow(app, task.id)?.let(onOpenChat)
                                }) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Run now")
                                }
                                if (task.lastChatId.isNotBlank()) {
                                    TextButton(onClick = { onOpenChat(task.lastChatId) }) { Text("Open last result") }
                                }
                            }
                        }
                    }
                }
                // ---- Memory ----
                SectionTitle("Memory")
                MemoryPage(memories, memoryEnabled, onMemoryEnabledChange, onAddMemory, onDeleteMemory, scrollable = false)

                // ---- Reminders ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Reminders", Modifier.weight(1f))
                    TextButton(onClick = { newTaskKind = TASK_REMINDER }) { Text("Add") }
                }
                val reminders = tasks.filter { it.kind == TASK_REMINDER }.sortedBy { it.triggerAt }
                if (reminders.isEmpty()) Empty("No reminders.")
                reminders.forEach { reminder ->
                    TaskRow(
                        title = reminder.text,
                        subtitle = scheduleLabel(reminder),
                        onDelete = { pendingDelete = reminder }
                    )
                }

                Spacer(Modifier.size(24.dp))
            }
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (task.kind == TASK_REMINDER) "Delete reminder?" else "Delete scheduled task?") },
            text = {
                Text(
                    "\"${task.text}\" " + if (task.repeatDaily) "won't run again. Results it already saved stay in your chats." else "will be removed."
                )
            },
            confirmButton = {
                DangerButton("Delete") {
                    TaskAlarms.cancel(app, task.id)
                    pendingDelete = null
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
    newTaskKind?.let { kind ->
        NewTaskDialog(
            kind = kind,
            onDismiss = { newTaskKind = null },
            onCreate = { task ->
                schedule(task)
                newTaskKind = null
            }
        )
    }
}

private fun scheduleLabel(task: ScheduledTask): String =
    if (task.repeatDaily) "Every day at ${formatTaskTime(task.triggerAt).substringAfter(" at ")} · next ${formatTaskTime(task.triggerAt)}"
    else formatTaskTime(task.triggerAt).replaceFirstChar { it.uppercase() }

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun TaskRow(title: String, subtitle: String, progress: Float? = null, mono: Boolean = false, onDelete: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = if (mono) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                        fontFamily = if (mono) FontFamily.Monospace else null
                    )
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
            }
            progress?.let {
                LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp))
            }
        }
    }
}

/** Adds a custom timer, a reminder, or a scheduled assistant task. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskDialog(kind: String, onDismiss: () -> Unit, onCreate: (ScheduledTask) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var daily by rememberSaveable { mutableStateOf(kind == TASK_AI) }
    val start = remember { ZonedDateTime.now().plusMinutes(5) }
    val time = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    TASK_TIMER -> "New timer"
                    TASK_REMINDER -> "New reminder"
                    else -> "New scheduled task"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (kind == TASK_TIMER) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Label (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(if (kind == TASK_REMINDER) "Remind me to…" else "What should the assistant do?") },
                        placeholder = if (kind == TASK_AI) ({ Text("Check bestbuy.com laptop deals and summarize them") }) else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TimePicker(state = time)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Repeat every day", modifier = Modifier.weight(1f))
                        Switch(checked = daily, onCheckedChange = { daily = it })
                    }
                }
            }
        },
        confirmButton = {
            val valid = if (kind == TASK_TIMER) (minutes.toIntOrNull() ?: 0) > 0 else text.isNotBlank()
            Button(enabled = valid, onClick = {
                val task = if (kind == TASK_TIMER) {
                    val ms = minutes.toLong() * 60_000L
                    ScheduledTask(kind = TASK_TIMER, text = text.trim(), triggerAt = System.currentTimeMillis() + ms, durationMs = ms)
                } else {
                    val at = nextOccurrence(LocalTime.of(time.hour, time.minute), ZonedDateTime.now()).toInstant().toEpochMilli()
                    ScheduledTask(kind = kind, text = text.trim(), triggerAt = at, repeatDaily = daily)
                }
                onCreate(task)
            }) { Text(if (kind == TASK_TIMER) "Start" else "Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
