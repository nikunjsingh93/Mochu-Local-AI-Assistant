package com.nikunj.gemmabuilder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/**
 * The model loaded by the app's screens, so a scheduled task can use it instead of loading a second
 * copy (two copies of a 3–4 GB model don't fit in memory).
 */
object EngineHolder {
    @Volatile var loaded: BuilderEngine? = null
    /** True while Chat or Studio is generating. */
    @Volatile var foregroundBusy = false
    /** True while a scheduled task is using a model. */
    @Volatile var backgroundRunning = false
}

/** Events from background work that open screens react to. */
object AppEvents {
    /** A scheduled task saved a new chat (its id), so chat lists should reload. */
    val chatAdded = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Chat id a notification asked to open. */
    val openChat = MutableStateFlow<String?>(null)
    /** A notification asked to show the Tasks screen. */
    val openTasks = MutableStateFlow(false)
    /** The scheduled task running now, so its chat can show it live. */
    val taskProgress = MutableStateFlow<TaskProgress?>(null)
}

/** What a running scheduled task has done so far: its steps and the answer written so far. */
data class TaskProgress(val chatId: String, val steps: List<ActivityStep>, val text: String, val status: String)

const val EXTRA_OPEN_CHAT = "com.nikunj.mochu.OPEN_CHAT"
const val EXTRA_OPEN_TASKS = "com.nikunj.mochu.OPEN_TASKS"
const val EXTRA_RUN_TASK = "com.nikunj.mochu.RUN_TASK"
private const val EXTRA_TASK_ID = "taskId"
private const val EXTRA_CHAT_ID = "chatId"
private const val ACTION_TASK_ALARM = "com.nikunj.mochu.TASK_ALARM"
private const val ACTION_DISMISS = "com.nikunj.mochu.DISMISS"

object TaskAlarms {
    fun canBeExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun alarmIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, TaskAlarmReceiver::class.java).setAction(ACTION_TASK_ALARM).putExtra(EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Saves [task] and wakes the app at its time. Without the exact-alarm permission it may run a few minutes late. */
    fun schedule(context: Context, task: ScheduledTask) {
        TaskRepository.upsert(context, task)
        if (task.done) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context, task.id)
        if (canBeExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, pending)
        }
    }

    /** Stops a task's alarm but keeps the task (a one-off that ran early, kept for its result). */
    fun disarm(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
    }

    fun cancel(context: Context, id: String) {
        val task = TaskRepository.tasks.value.firstOrNull { it.id == id }
        disarm(context, id)
        TaskRepository.remove(context, id)
        TaskNotifications.cancel(context, id)
        if (task != null) clearTaskMark(context, task)
    }

    /** The chats a task wrote are titled "⏰ <task>"; once the task is gone they lose the ⏰. */
    private fun clearTaskMark(context: Context, task: ScheduledTask) {
        val marked = "⏰ ${task.text.take(50)}"
        val changed = loadConversations(context).filter { it.title == marked }
        changed.forEach { saveConversationFile(context, it.copy(title = task.text.take(50))) }
        changed.firstOrNull()?.let { AppEvents.chatAdded.tryEmit(it.id) }
    }

    /** Re-arms every pending task (after a reboot or app update, which clear alarms). Missed ones run now. */
    fun rescheduleAll(context: Context) {
        TaskRepository.load(context)
        val now = System.currentTimeMillis()
        TaskRepository.tasks.value.filterNot { it.done }.forEach { task ->
            schedule(context, if (task.triggerAt < now - 60_000 && task.repeatDaily) task.copy(triggerAt = nextDailyTrigger(task.triggerAt, now)) else task)
        }
    }

    /**
     * Runs an assistant task right away. Its chat is created first (with the request in it) and its id
     * returned, so the caller can open it and watch the answer come in.
     */
    fun runNow(context: Context, id: String): String? {
        val task = TaskRepository.get(context, id) ?: return null
        val chatId = startTaskChat(context, task)
        ContextCompat.startForegroundService(
            context,
            Intent(context, TaskRunnerService::class.java).putExtra(EXTRA_TASK_ID, id).putExtra(EXTRA_CHAT_ID, chatId)
        )
        return chatId
    }

    /** Saves a new chat holding the task's request; the runner adds the answer to it. */
    fun startTaskChat(context: Context, task: ScheduledTask): String {
        val now = System.currentTimeMillis()
        val chat = ChatConversation(
            id = UUID.randomUUID().toString(),
            title = "⏰ ${task.text.take(50)}",
            updatedAt = now,
            messages = listOf(ChatMessage("user", task.text, timestamp = now)),
            kind = CHAT_KIND_ASSISTANT
        )
        saveConversationFile(context, chat)
        AppEvents.chatAdded.tryEmit(chat.id)
        return chat.id
    }
}

object TaskNotifications {
    const val CHANNEL_TIMER = "timers"
    const val CHANNEL_REMINDER = "reminders"
    const val CHANNEL_RESULT = "task_results"
    const val CHANNEL_RUNNING = "task_running"
    const val CHANNEL_STOPWATCH = "stopwatch"
    const val STOPWATCH_ID = 7001
    const val RUNNING_ID = 7002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_TIMER) != null) return
        val alarmSound = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TIMER, "Timers", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmSound)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_REMINDER, "Reminders", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_RESULT, "Scheduled task results", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_RUNNING, "Running scheduled tasks", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_STOPWATCH, "Stopwatch", NotificationManager.IMPORTANCE_LOW)
            )
        )
    }

    private fun openApp(context: Context, request: Int, extras: Intent.() -> Unit): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply(extras),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun post(context: Context, id: Int, notification: Notification) {
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, notification) }
    }

    fun cancel(context: Context, taskId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(taskId.hashCode())
    }

    /** Opens the chat [chatId] when there is one, else the Tasks screen. */
    private fun openChatOrTasks(context: Context, request: Int, chatId: String): PendingIntent =
        openApp(context, request) { if (chatId.isNotBlank()) putExtra(EXTRA_OPEN_CHAT, chatId) else putExtra(EXTRA_OPEN_TASKS, true) }

    /** A running timer's countdown in the notification shade; tapping it opens its chat. */
    fun timerRunning(context: Context, task: ScheduledTask) {
        ensureChannels(context)
        post(
            context, task.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_STOPWATCH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(if (task.text.isBlank()) "Timer" else "${task.text} timer")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(task.triggerAt)
                .setShowWhen(true)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(openChatOrTasks(context, task.id.hashCode(), task.chatId))
                .build()
        )
    }

    fun timerDone(context: Context, task: ScheduledTask) {
        ensureChannels(context)
        val dismiss = PendingIntent.getBroadcast(
            context, task.id.hashCode(),
            Intent(context, TaskAlarmReceiver::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (task.text.isBlank()) "Timer done" else "${task.text} timer done")
            .setContentText("${formatDuration(task.durationMs)} is up")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openChatOrTasks(context, task.id.hashCode(), task.chatId))
            .addAction(0, "Dismiss", dismiss)
            .setAutoCancel(true)
            .build()
            // Keeps ringing until dismissed, like a kitchen timer.
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        post(context, task.id.hashCode(), notification)
    }

    fun reminder(context: Context, task: ScheduledTask) {
        ensureChannels(context)
        post(
            context, task.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Reminder")
                .setContentText(task.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(task.text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openChatOrTasks(context, task.id.hashCode(), task.chatId))
                .setAutoCancel(true)
                .build()
        )
    }

    fun running(context: Context, task: ScheduledTask?, step: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(task?.text?.take(60) ?: "Scheduled task")
            .setContentText(step)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun result(context: Context, task: ScheduledTask, chatId: String, summary: String) {
        ensureChannels(context)
        post(
            context, task.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(task.text.take(60))
                .setContentText(summary.take(120))
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary.take(600)))
                .setContentIntent(openApp(context, chatId.hashCode()) { putExtra(EXTRA_OPEN_CHAT, chatId) })
                .setAutoCancel(true)
                .build()
        )
    }

    /** When Android won't let a task start in the background, the user can start it with a tap. */
    fun tapToRun(context: Context, task: ScheduledTask) {
        ensureChannels(context)
        post(
            context, task.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(task.text.take(60))
                .setContentText("Tap to run this scheduled task now.")
                .setContentIntent(openApp(context, task.id.hashCode()) { putExtra(EXTRA_RUN_TASK, task.id) })
                .setAutoCancel(true)
                .build()
        )
    }

    fun stopwatch(context: Context, state: StopwatchState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!state.running) {
            manager.cancel(STOPWATCH_ID)
            return
        }
        ensureChannels(context)
        post(
            context, STOPWATCH_ID,
            NotificationCompat.Builder(context, CHANNEL_STOPWATCH)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("Stopwatch")
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - state.elapsedMs())
                .setShowWhen(true)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(openChatOrTasks(context, STOPWATCH_ID, state.chatId))
                .build()
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return buildList {
        if (h > 0) add("$h hr")
        if (m > 0) add("$m min")
        if (s > 0 && h == 0L) add("$s sec")
    }.joinToString(" ").ifBlank { "0 sec" }
}

fun formatTaskTime(at: Long): String {
    val now = java.time.LocalDate.now()
    val day = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
    return when (day) {
        now -> "today at $time"
        now.plusDays(1) -> "tomorrow at $time"
        else -> "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))} at $time"
    }
}

/** Wakes up for timers, reminders and scheduled tasks; re-arms alarms after a reboot or app update. */
class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> TaskAlarms.rescheduleAll(context)
            ACTION_DISMISS -> intent.getStringExtra(EXTRA_TASK_ID)?.let { TaskNotifications.cancel(context, it) }
            ACTION_TASK_ALARM -> {
                // A one-off task that already ran ("Run now") has nothing left to do.
                val task = intent.getStringExtra(EXTRA_TASK_ID)?.let { TaskRepository.get(context, it) }?.takeUnless { it.done } ?: return
                when (task.kind) {
                    TASK_TIMER -> {
                        TaskNotifications.timerDone(context, task)
                        TaskRepository.remove(context, task.id)
                    }
                    TASK_REMINDER -> {
                        TaskNotifications.reminder(context, task)
                        if (task.repeatDaily) {
                            TaskAlarms.schedule(context, task.copy(triggerAt = nextDailyTrigger(task.triggerAt, System.currentTimeMillis()), lastRunAt = System.currentTimeMillis()))
                        } else {
                            TaskRepository.remove(context, task.id)
                        }
                    }
                    TASK_AI -> {
                        // Exact alarms may start a foreground service from the background; inexact ones may not.
                        val started = runCatching { TaskAlarms.runNow(context, task.id) }.isSuccess
                        if (!started) TaskNotifications.tapToRun(context, task)
                    }
                }
            }
        }
    }
}

/**
 * Runs a scheduled assistant task: reads the web if it needs to, asks the model, saves the answer as
 * a new chat, and posts it as a notification. Runs as a foreground service so Android lets it finish.
 */
class TaskRunnerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = Mutex()
    private var pending = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_TASK_ID)
        val task = id?.let { TaskRepository.get(this, it) }
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)
        val notification = TaskNotifications.running(this, task, "Starting…")
        startForeground(TaskNotifications.RUNNING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (task == null) {
            if (pending == 0) finish()
            return START_NOT_STICKY
        }
        pending++
        scope.launch {
            queue.withLock { runCatching { run(task, chatId ?: TaskAlarms.startTaskChat(applicationContext, task)) } }
            AppEvents.taskProgress.value = null
            // Stop (and drop the "running" notification) once nothing else is queued.
            if (--pending == 0) finish()
        }
        return START_NOT_STICKY
    }

    private var liveChatId = ""
    private val liveSteps = mutableListOf<ActivityStep>()

    private fun progress(task: ScheduledTask, step: String, text: String = "") {
        AppEvents.taskProgress.value = TaskProgress(liveChatId, liveSteps.toList(), text, step)
        runCatching {
            getSystemService(NotificationManager::class.java).notify(TaskNotifications.RUNNING_ID, TaskNotifications.running(this, task, step))
        }
    }

    private suspend fun run(task: ScheduledTask, chatId: String) {
        liveChatId = chatId
        liveSteps.clear()
        // Let a reply that's being written in the app finish first.
        repeat(600) { if (!EngineHolder.foregroundBusy) return@repeat; delay(1_000) }
        EngineHolder.backgroundRunning = true
        var ownEngine: BuilderEngine? = null
        try {
            progress(task, "Getting the model ready…")
            val engine = EngineHolder.loaded ?: withContext(Dispatchers.IO) { loadModelForTask(applicationContext) }
                ?.also { ownEngine = it }
            val steps = liveSteps
            val pages = mutableListOf<PageText>()
            var unread = emptyList<SearchResult>()
            var searchBlocked = false
            if (needsWeb(task.text)) {
                val urls = linksIn(task.text)
                val targets = if (urls.isNotEmpty()) {
                    urls.map { SearchResult(hostOf(it), it, "") }
                } else {
                    progress(task, "Searching the web…")
                    val query = searchQueryFrom(task.text)
                    // A bot check now often clears within a minute, so wait and try once more.
                    var search = withContext(Dispatchers.IO) { runCatching { searchDuckDuckGo(query, limit = 6) } }
                    if (search.exceptionOrNull() is SearchBlockedException) {
                        progress(task, "Search is busy, retrying in a minute…")
                        delay(60_000)
                        search = withContext(Dispatchers.IO) { runCatching { searchDuckDuckGo(query, limit = 6) } }
                    }
                    searchBlocked = search.exceptionOrNull() is SearchBlockedException
                    val results = search.getOrDefault(emptyList())
                    steps += if (searchBlocked) ActivityStep(ACTIVITY_NOTE, "DuckDuckGo asked for a bot check")
                    else ActivityStep(ACTIVITY_SEARCH, "Searched \"$query\" · ${results.size} results")
                    unread = results.drop(2).take(3)
                    results.take(2)
                }
                for (target in targets) {
                    progress(task, "Reading ${hostOf(target.url)}…")
                    val direct = withContext(Dispatchers.IO) { fetchPageText(target.url) }
                    val page = direct?.takeIf { it.text.length >= 600 && !looksBlocked(it) }
                        ?: if (tooLowOnMemoryForWebView(applicationContext)) null else readWithWebView(applicationContext, target.url)
                    if (page == null || page.text.length < 200 || looksBlocked(page)) {
                        steps += ActivityStep(ACTIVITY_SKIP, "Couldn't read ${hostOf(target.url)}", target.url)
                    } else {
                        pages += page.copy(title = page.title.ifBlank { target.title }, url = target.url)
                        steps += ActivityStep(ACTIVITY_READ, "Read ${hostOf(target.url)}", target.url)
                    }
                }
            }
            val reply = if (engine == null) {
                "_Couldn't run this task: no model is downloaded or it failed to load._"
            } else if (searchBlocked && pages.isEmpty()) {
                "_${SEARCH_BLOCKED_NOTE}_"
            } else {
                progress(task, "Writing the summary…")
                val prompt = if (needsWeb(task.text)) webResearchPrompt(task.text, pages, unread, 12_000) else task.text
                val memory = if (savedMemoryEnabled(applicationContext)) memoryPromptSection(loadMemories(applicationContext)) else ""
                val output = StringBuilder()
                var lastUpdate = 0L
                runCatching {
                    engine.generate("task:${task.id}", prompt, ASSISTANT_SYSTEM_PROMPT + memory).collect { chunk ->
                        output.append(chunk)
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastUpdate > 150) {
                            lastUpdate = nowMs
                            AppEvents.taskProgress.value = TaskProgress(liveChatId, liveSteps.toList(), splitThinking(output.toString()).answer, "Writing the summary…")
                        }
                        degenerateRepetitionStart(output)?.let { start -> output.setLength(start); throw StopGeneration("loop") }
                    }
                }
                engine.resetSession()
                engine.takeGenerationStats()
                val answer = splitThinking(output.toString()).answer.trim().ifBlank { "_The model returned an empty answer._" }
                if (pages.isEmpty()) answer else answer + "\n\n**Sources**\n" + pages.mapIndexed { i, page ->
                    val url = page.url.replace("(", "%28").replace(")", "%29").replace(" ", "%20")
                    "${i + 1}. [${page.title.ifBlank { hostOf(page.url) }.replace("[", "(").replace("]", ")")}]($url)"
                }.joinToString("\n")
            }
            val now = System.currentTimeMillis()
            val started = withContext(Dispatchers.IO) { loadConversations(applicationContext).firstOrNull { it.id == chatId } }
            val chat = (started ?: ChatConversation(
                id = chatId,
                title = "⏰ ${task.text.take(50)}",
                updatedAt = now,
                messages = listOf(ChatMessage("user", task.text, timestamp = now - 1)),
                kind = CHAT_KIND_ASSISTANT
            )).let { it.copy(updatedAt = now, messages = it.messages + ChatMessage("assistant", reply, timestamp = now, activity = steps.toList())) }
            withContext(Dispatchers.IO) { saveConversationFile(applicationContext, chat) }
            AppEvents.taskProgress.value = null
            AppEvents.chatAdded.tryEmit(chat.id)
            TaskNotifications.result(applicationContext, task, chat.id, speakableText(reply))
            val latest = TaskRepository.get(applicationContext, task.id) ?: task
            if (latest.repeatDaily) {
                TaskAlarms.schedule(applicationContext, latest.copy(triggerAt = nextDailyTrigger(latest.triggerAt, now), lastRunAt = now, lastChatId = chat.id))
            } else {
                TaskRepository.upsert(applicationContext, latest.copy(lastRunAt = now, lastChatId = chat.id, done = true))
                TaskAlarms.disarm(applicationContext, latest.id)
            }
        } finally {
            ownEngine?.close()
            EngineHolder.backgroundRunning = false
        }
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}

/** Requests that need the web: a site to check, or anything about news, prices, weather and the like. */
fun needsWeb(request: String): Boolean =
    Regex("""(?:https?://|www\.|\.(?:com|org|net|io|ca|co|uk|dev)\b)""", RegexOption.IGNORE_CASE).containsMatchIn(request) ||
        Regex("""\b(check|search|look\s+up|news|headlines|weather|prices?|deals?|latest|today's|score|stocks?)\b""", RegexOption.IGNORE_CASE).containsMatchIn(request)

/** Loads the selected model for a task when the app hasn't loaded one; the caller closes it. */
internal suspend fun loadModelForTask(context: Context): BuilderEngine? {
    savedRemoteModelConfig(context)?.takeIf { remoteModelActive(context) }?.let { return RemoteEngine(it) }
    val file = modelFile(context)
    if (!file.exists()) return null
    val window = safeTokenWindow(context, file, savedModelContextTokens(context, savedModelName(context)))
    return runCatching {
        if (isGgufModel(savedModelName(context))) {
            LlamaCppEngine(context, file.absolutePath, window).also { it.load() }
        } else {
            LiteRtGemmaEngine(context, file.absolutePath, savedBackendPreference(context), false, window).also { it.load() }
        }
    }.onSuccess { recordLoadedModel(context, file, window) }.getOrNull()
}
