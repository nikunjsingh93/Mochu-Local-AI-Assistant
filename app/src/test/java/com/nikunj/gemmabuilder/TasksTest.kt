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
}
