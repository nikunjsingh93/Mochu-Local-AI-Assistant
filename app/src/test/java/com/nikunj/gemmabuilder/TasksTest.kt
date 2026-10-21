package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TasksTest {
    private val zone = ZoneId.of("America/Toronto")
    // Monday 2026-10-19, 14:30.
    private val now = ZonedDateTime.of(2026, 10, 19, 14, 30, 0, 0, zone)
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private val nowMs = now.toInstant().toEpochMilli()

    @Test
    fun parsesTimers() {
        assertEquals(TaskCommand.Timer(600_000, ""), taskCommandFrom("Set a timer for 10 minutes", now))
        assertEquals(TaskCommand.Timer(300_000, "pasta"), taskCommandFrom("set a pasta timer for 5 min", now))
        assertEquals(TaskCommand.Timer(90 * 60_000L, ""), taskCommandFrom("timer 1 hour 30 minutes", now))
        assertEquals(TaskCommand.Timer(15 * 60_000L, ""), taskCommandFrom("15 minute timer", now))
        assertNull(taskCommandFrom("How does a kitchen timer work?", now))
    }

    @Test
    fun parsesReminders() {
        assertEquals(TaskCommand.Reminder("call mom", at(19, 17), false), taskCommandFrom("Remind me at 5pm to call mom", now))
        assertEquals(TaskCommand.Reminder("check the oven", nowMs + 20 * 60_000, false), taskCommandFrom("remind me to check the oven in 20 minutes", now))
        assertEquals(TaskCommand.Reminder("standup", at(20, 9, 15), false), taskCommandFrom("remind me tomorrow at 9:15am about standup".replace("about ", "to "), now))
        // 9am has passed today, so it's tomorrow; "every day" repeats it.
        assertEquals(TaskCommand.Reminder("take vitamins", at(20, 9), true), taskCommandFrom("Remind me every day at 9am to take vitamins", now))
        // "at 7" means the next 7 o'clock: this evening.
        assertEquals(TaskCommand.Reminder("leave", at(19, 19), false), taskCommandFrom("remind me at 7 to leave", now))
    }

    @Test
    fun parsesScheduledAssistantTasks() {
        assertEquals(
            TaskCommand.Assistant("Check bestbuy.com laptop deals and summarize it", at(20, 10), true),
            taskCommandFrom("Every day at 10am check bestbuy.com laptop deals and summarize it", now)
        )
        assertEquals(
            TaskCommand.Assistant("Give me the latest tech news", at(20, 7), false),
            taskCommandFrom("tomorrow at 7am give me the latest tech news", now)
        )
        // Typos and the time at the end, as typed on a phone.
        assertEquals(
            TaskCommand.Assistant("Summarise tech news", at(20, 8), true),
            taskCommandFrom("summarise tech news eveyday at 8am", now)
        )
        assertEquals(
            TaskCommand.Assistant("Summarize the latest tech news", at(19, 18), false),
            taskCommandFrom("summarize the latest tech news at 6pm", now)
        )
        assertEquals(TaskCommand.Reminder("drink water", at(20, 9), true), taskCommandFrom("remind me everday at 9am to drink water", now))
        assertNull(taskCommandFrom("Summarize what we discussed about every developer", now))
        assertNull(taskCommandFrom("At 10am I had a meeting about prices", now).takeIf { it !is TaskCommand.Assistant })
        assertNull(taskCommandFrom("What happened in 1969?", now))
    }

    @Test
    fun parsesStopwatch() {
        assertEquals(TaskCommand.StartStopwatch, taskCommandFrom("Start a stopwatch", now))
        assertEquals(TaskCommand.StopStopwatch, taskCommandFrom("stop the stopwatch", now))
    }

    @Test
    fun nextDailyRunSkipsMissedDays() {
        val next = nextDailyTrigger(at(17, 10), nowMs, zone)
        assertEquals(at(20, 10), next)
    }

    @Test
    fun tasksRoundTrip() {
        val tasks = listOf(ScheduledTask(kind = TASK_AI, text = "Check news", triggerAt = 5, repeatDaily = true, lastChatId = "c1"))
        assertEquals(tasks, parseTasks(encodeTasks(tasks)))
        assertTrue(parseTasks("[]").isEmpty())
    }

    @Test
    fun parsesEverydayPhrasings() {
        fun daily(text: String) = (taskCommandFrom(text, now) as? TaskCommand.Assistant)?.let { it.daily to it.request }
        assertEquals(true to "Tell me a joke", daily("every day at 11pm tell me a joke"))
        assertEquals(true to "Tell me a joke", daily("tell me a joke every day at 11pm"))
        assertEquals(true to "Write me a short poem", daily("every night at 9pm write me a short poem"))
        assertEquals(true to "Give me a motivational quote", daily("every morning at 7 give me a motivational quote"))
        assertEquals(true to "Share a fun fact", daily("daily at 8am share a fun fact"))
        assertEquals(true to "Suggest a dinner recipe", daily("suggest a dinner recipe everyday at 5 pm"))
        assertEquals(false to "Tell me a joke", (taskCommandFrom("at 9pm tell me a joke", now) as? TaskCommand.Assistant)?.let { it.daily to it.request })
        assertEquals(true to "Tell me a joke", daily("Everyday at 11 pm tell me a joke"))
        assertEquals(true to "Tell me a joke", daily("every day at 11 p.m. tell me a joke"))
        assertEquals(true to "Tell me a joke", daily("every day 11pm tell me a joke"))
        assertNull(taskCommandFrom("what time is it", now))
        assertNull(taskCommandFrom("tell me a joke", now))
    }

    @Test
    fun timeWithTodayAndNotifyMe() {
        val c = taskCommandFrom("search the web latest sales on electronics and notify me at 7:48pm today", now) as? TaskCommand.Assistant
        assertEquals("Search the web latest sales on electronics", c?.request)
        assertEquals(at(19, 19, 48), c?.at)
    }

    @Test
    fun dottedTimesAndScheduleWording() {
        val c = taskCommandFrom("schedule a task that i want tech news today at 11.45pm today give me a summary", now) as? TaskCommand.Assistant
        assertEquals(at(19, 23, 45), c?.at)
        assertTrue(c?.request.orEmpty().startsWith("Tech news"))
        assertEquals(at(19, 19, 30), (taskCommandFrom("remind me at 7.30pm to call mom", now) as? TaskCommand.Reminder)?.at)
    }

    @Test
    fun politeOpeningsAndWrittenReminders() {
        assertEquals(TaskCommand.Reminder("call mom", at(19, 17), false), taskCommandFrom("Could you please remind me at 5pm to call mom?", now))
        val c = taskCommandFrom("could you please remind me at 9.20 today to laugh and add a mindful note along with it", now) as? TaskCommand.Assistant
        assertEquals(at(19, 21, 20), c?.at)
        assertEquals("Remind me to laugh and add a mindful note along with it", c?.request)
    }

    @Test
    fun reschedulesTheLastTask() {
        assertEquals(TaskCommand.Reschedule(at(20, 9), false), taskCommandFrom("actually run this only today at 9am", now))
        assertEquals(TaskCommand.Reschedule(at(19, 18), null), taskCommandFrom("Actually, make it 6pm", now))
        assertEquals(TaskCommand.Reschedule(at(20, 8), true), taskCommandFrom("change it to every day at 8am", now))
        assertTrue(taskCommandFrom("actually remind me at 5pm to call mom", now) is TaskCommand.Reminder)
        assertNull(taskCommandFrom("Actually, what is the capital of France?", now))
    }
}
