package com.nikunj.gemmabuilder

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

// Timers, reminders, a stopwatch, and scheduled assistant tasks ("every day at 10am check this site
// and summarize it"). All run inside the app: alarms wake it up and it posts its own notifications.

const val TASK_TIMER = "timer"
const val TASK_REMINDER = "reminder"
const val TASK_AI = "ai"

data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    /** [TASK_TIMER], [TASK_REMINDER] or [TASK_AI]. */
    val kind: String,
    /** What the reminder says, the timer's label, or the request the assistant runs. */
    val text: String,
    val triggerAt: Long,
    val repeatDaily: Boolean = false,
    /** A timer's full length, for showing progress. */
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long = 0,
    /** The chat holding the last result of an assistant task. */
    val lastChatId: String = "",
    /** A one-off task that has run; kept so its result stays reachable. */
    val done: Boolean = false
)

data class StopwatchState(val running: Boolean = false, val startedAt: Long = 0, val accumulatedMs: Long = 0) {
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long = accumulatedMs + if (running) now - startedAt else 0
}

/** Tasks and the stopwatch, shared by the UI, the alarm receiver and the task runner (one process). */
object TaskRepository {
    private val _tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()
    private val _stopwatch = MutableStateFlow(StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _tasks.value = runCatching { parseTasks(tasksFile(context).readText()) }.getOrDefault(emptyList())
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _stopwatch.value = StopwatchState(
            running = prefs.getBoolean("stopwatchRunning", false),
            startedAt = prefs.getLong("stopwatchStartedAt", 0),
            accumulatedMs = prefs.getLong("stopwatchAccumulated", 0)
        )
    }

    fun get(context: Context, id: String): ScheduledTask? {
        load(context)
        return _tasks.value.firstOrNull { it.id == id }
    }

    @Synchronized
    fun upsert(context: Context, task: ScheduledTask) {
        load(context)
        _tasks.update { list -> list.filterNot { it.id == task.id } + task }
        save(context)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        load(context)
        _tasks.update { list -> list.filterNot { it.id == id } }
        save(context)
    }

    fun setStopwatch(context: Context, state: StopwatchState) {
        _stopwatch.value = state
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("stopwatchRunning", state.running)
            .putLong("stopwatchStartedAt", state.startedAt)
            .putLong("stopwatchAccumulated", state.accumulatedMs)
            .apply()
    }

    private fun tasksFile(context: Context) = File(context.filesDir, "tasks.json")

    private fun save(context: Context) {
        tasksFile(context).writeText(encodeTasks(_tasks.value))
    }
}

fun encodeTasks(tasks: List<ScheduledTask>): String = JSONArray().apply {
    tasks.forEach { t ->
        put(
            JSONObject()
                .put("id", t.id).put("kind", t.kind).put("text", t.text).put("triggerAt", t.triggerAt)
                .put("repeatDaily", t.repeatDaily).put("durationMs", t.durationMs).put("createdAt", t.createdAt)
                .put("lastRunAt", t.lastRunAt).put("lastChatId", t.lastChatId).put("done", t.done)
        )
    }
}.toString()

fun parseTasks(json: String): List<ScheduledTask> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        ScheduledTask(
            id = o.getString("id"),
            kind = o.getString("kind"),
            text = o.optString("text"),
            triggerAt = o.getLong("triggerAt"),
            repeatDaily = o.optBoolean("repeatDaily"),
            durationMs = o.optLong("durationMs"),
            createdAt = o.optLong("createdAt"),
            lastRunAt = o.optLong("lastRunAt"),
            lastChatId = o.optString("lastChatId"),
            done = o.optBoolean("done")
        )
    }
}

/** The next time [time] comes round after [now] (today if it hasn't passed yet, else tomorrow). */
fun nextOccurrence(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
    val today = now.with(time).withSecond(0).withNano(0)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/** The same time one day later, for daily tasks. */
fun nextDailyTrigger(previous: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    var next = Instant.ofEpochMilli(previous).atZone(zone).plusDays(1)
    while (next.toInstant().toEpochMilli() <= now) next = next.plusDays(1)
    return next.toInstant().toEpochMilli()
}

// ---- Chat commands ("set a timer for 10 minutes", "remind me at 5pm to call mom", ...) ----

sealed class TaskCommand {
    data class Timer(val durationMs: Long, val label: String) : TaskCommand()
    data class Reminder(val text: String, val at: Long, val daily: Boolean) : TaskCommand()
    data class Assistant(val request: String, val at: Long, val daily: Boolean) : TaskCommand()
    data object StartStopwatch : TaskCommand()
    data object StopStopwatch : TaskCommand()
    data object ResetStopwatch : TaskCommand()
}

/** When a message says to do something: the time, whether it repeats, and the message without it. */
data class ParsedWhen(val at: Long, val daily: Boolean, val rest: String)

private const val NUM = """(\d+(?:\.\d+)?|a|an|one|two|three|four|five|ten|fifteen|twenty|thirty|forty five|half an?)"""
private val unitRegex = Regex("""$NUM\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)

private fun numberWord(word: String): Double? = when (word.lowercase().trim()) {
    "a", "an", "one" -> 1.0
    "two" -> 2.0
    "three" -> 3.0
    "four" -> 4.0
    "five" -> 5.0
    "ten" -> 10.0
    "fifteen" -> 15.0
    "twenty" -> 20.0
    "thirty" -> 30.0
    "forty five" -> 45.0
    "half a", "half an" -> 0.5
    else -> word.toDoubleOrNull()
}

/** "10 minutes", "1.5 hours", "an hour" → milliseconds. */
fun parseDuration(text: String): Long? {
    var total = 0.0
    var found = false
    for (m in unitRegex.findAll(text)) {
        val n = numberWord(m.groupValues[1]) ?: continue
        val unit = m.groupValues[2].lowercase()
        total += n * when {
            unit.startsWith("h") -> 3_600_000.0
            unit.startsWith("m") -> 60_000.0
            else -> 1_000.0
        }
        found = true
    }
    return if (found && total >= 1_000) total.toLong() else null
}

private val inRegex = Regex("""\bin\s+((?:$NUM\s*(?:seconds?|secs?|minutes?|mins?|hours?|hrs?)\s*(?:and\s+)?)+)""", RegexOption.IGNORE_CASE)
private val atRegex = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)(?=\W|$)|\bat\s+(\d{1,2})(?::(\d{2}))?\b|\bat\s+(noon|midnight)\b""", RegexOption.IGNORE_CASE)
// Typos are common on phone keyboards: "eveyday", "everday", "evryday", "evey day".
private val dailyRegex = Regex("""\b(ev[a-z]{0,4}\s*day|daily|each\s+day|ev[a-z]{0,4}\s+(?:morning|evening|night))\b""", RegexOption.IGNORE_CASE)
/** A time or delay that ends the message, as in "summarize tech news at 8am" or "... in 2 hours". */
private val timeAtEnd = Regex(
    """(?:\bat\s+\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.?|p\.m\.?)?|\b\d{1,2}(?::\d{2})?\s*(?:am|pm)|\bin\s+\d+\s*(?:minutes?|mins?|hours?|hrs?)|\btomorrow(?:\s+morning)?)(?:\s+(?:today|tonight|tomorrow|this\s+(?:morning|afternoon|evening)))?\s*[.!?]*$""",
    RegexOption.IGNORE_CASE
)
private val tomorrowRegex = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE)
private val todayRegex = Regex("""\b(today|tonight|this\s+(?:morning|afternoon|evening))\b""", RegexOption.IGNORE_CASE)

/** Finds "in 20 minutes", "at 5pm", "tomorrow at 9", "every day at 10am" in [text]. */
fun parseWhen(text: String, now: ZonedDateTime): ParsedWhen? {
    inRegex.find(text)?.let { m ->
        val ms = parseDuration(m.groupValues[1]) ?: return@let
        return ParsedWhen(now.toInstant().toEpochMilli() + ms, false, text.removeRange(m.range))
    }
    val at = atRegex.find(text) ?: return null
    val g = at.groupValues
    var hour: Int
    var minute: Int
    when {
        g[6].isNotEmpty() -> { hour = if (g[6].equals("noon", true)) 12 else 0; minute = 0 }
        g[1].isNotEmpty() -> {
            hour = g[1].toInt() % 12
            minute = g[2].toIntOrNull() ?: 0
            if (g[3].lowercase().startsWith("p")) hour += 12
        }
        else -> {
            hour = g[4].toInt()
            minute = g[5].toIntOrNull() ?: 0
            // "at 7" without am/pm: the next 7 o'clock to come.
            if (hour in 1..11) {
                val morning = nextOccurrence(LocalTime.of(hour, minute), now)
                val evening = nextOccurrence(LocalTime.of(hour + 12, minute), now)
                if (evening.isBefore(morning)) hour += 12
            }
        }
    }
    if (hour !in 0..23 || minute !in 0..59) return null
    var rest = text.removeRange(at.range)
    val daily = dailyRegex.containsMatchIn(rest)
    rest = rest.replace(dailyRegex, " ")
    val tomorrow = tomorrowRegex.containsMatchIn(rest)
    rest = rest.replace(tomorrowRegex, " ").replace(todayRegex, " ")
    val time = LocalTime.of(hour, minute)
    val whenAt = if (tomorrow) {
        ZonedDateTime.of(LocalDate.from(now).plusDays(1), time, now.zone)
    } else {
        nextOccurrence(time, now)
    }
    return ParsedWhen(whenAt.toInstant().toEpochMilli(), daily, rest)
}

private fun tidy(text: String): String = text
    .replace(Regex("""\s+"""), " ")
    .trim()
    .trim(',', '.', '!', '?', ' ')
    .replace(Regex("""^(?:to|that|and|then)\s+""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s+(?:and|then)$""", RegexOption.IGNORE_CASE), "")
    .trim()

private val timerRegex = Regex(
    """^(?:please\s+)?(?:(?:set|start)\s+(?:a\s+|an\s+|the\s+)?(?:(.+?)\s+)?timer\s+(?:for\s+)?(.+)|(?:set\s+(?:a\s+|an\s+)?)?(.+?)\s+timer(?:\s+for\s+(.+))?|timer\s+(?:for\s+)?(.+))$""",
    RegexOption.IGNORE_CASE
)
private val reminderRegex = Regex("""^(?:please\s+)?(?:remind\s+me|set\s+(?:a\s+)?reminder)\b\s*(.*)$""", RegexOption.IGNORE_CASE)
private val assistantVerbs = Regex(
    """\b(check|summari[sz]e|search|look\s+up|find|get\s+me|tell\s+me|give\s+me|send\s+me|show\s+me|fetch|read|news|headlines|weather|prices?|deals?|briefing|write|compose|make|create|generate|draft|share|suggest|recommend|plan|list|explain|teach|translate|quiz|motivate|inspire|jokes?|quotes?|poems?|story|facts?|tips?|riddles?|recipes?|affirmations?)\b""",
    RegexOption.IGNORE_CASE
)
private val scheduleHint = Regex("""^(?:please\s+)?(?:schedule|every\s*day|everyday|daily|each\s+day|every\s+(?:morning|evening)|tomorrow|tonight|at\s+\d|in\s+\d)""", RegexOption.IGNORE_CASE)

/** A message that sets a timer, reminder, stopwatch or scheduled task, or null for an ordinary message. */
fun taskCommandFrom(message: String, now: ZonedDateTime = ZonedDateTime.now()): TaskCommand? {
    val text = message.trim().trimEnd('.', '!')
    val lower = text.lowercase()
    when {
        Regex("""^(?:please\s+)?(?:start|begin)\s+(?:a\s+|the\s+)?stopwatch$""").matches(lower) -> return TaskCommand.StartStopwatch
        Regex("""^(?:please\s+)?(?:stop|pause|end)\s+(?:the\s+)?stopwatch$""").matches(lower) -> return TaskCommand.StopStopwatch
        Regex("""^(?:please\s+)?(?:reset|clear)\s+(?:the\s+)?stopwatch$""").matches(lower) -> return TaskCommand.ResetStopwatch
    }
    reminderRegex.find(text)?.let { m ->
        val parsed = parseWhen(m.groupValues[1], now) ?: return null
        val what = tidy(parsed.rest).ifBlank { "Reminder" }
        return TaskCommand.Reminder(what, parsed.at, parsed.daily)
    }
    if (Regex("""\btimer\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) && text.length <= 80) {
        timerRegex.find(text)?.let { m ->
            val parts = m.groupValues.drop(1).filter { it.isNotBlank() }
            val durationPart = parts.firstOrNull { parseDuration(it) != null } ?: return@let
            val ms = parseDuration(durationPart) ?: return@let
            val label = parts.firstOrNull { it != durationPart && parseDuration(it) == null }?.let(::tidy).orEmpty()
            return TaskCommand.Timer(ms, label)
        }
    }
    // "every day at 10am check bestbuy.com and summarize it", "at 7am give me the weather",
    // and the same with the time last: "summarize tech news at 8am".
    if (scheduleHint.containsMatchIn(text) || dailyRegex.containsMatchIn(text) || timeAtEnd.containsMatchIn(text)) {
        val parsed = parseWhen(text, now) ?: return null
        val request = tidy(
            parsed.rest.replace(Regex("""^\s*(?:please\s+)?schedule\b""", RegexOption.IGNORE_CASE), "")
                // "... and notify me", "... and let me know": the notification does that anyway.
                .replace(Regex("""\s*(?:,|and)?\s*(?:notify|alert|ping|tell|message)\s+me(?:\s+(?:about\s+it|with\s+it))?\s*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*(?:,|and)?\s*let\s+me\s+know\s*$""", RegexOption.IGNORE_CASE), "")
        )
        // "Every night at 9pm <anything>" is clearly a scheduled request; a one-off time needs a task-like verb.
        if (request.length >= 4 && (parsed.daily || assistantVerbs.containsMatchIn(request))) {
            return TaskCommand.Assistant(request.replaceFirstChar { it.uppercase() }, parsed.at, parsed.daily)
        }
    }
    return null
}

/** "1:02:03.4" or "02:03.4" for the stopwatch. */
fun formatStopwatch(ms: Long): String {
    val tenths = (ms / 100) % 10
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d.%d", h, m, s, tenths)
    else String.format(java.util.Locale.US, "%02d:%02d.%d", m, s, tenths)
}
