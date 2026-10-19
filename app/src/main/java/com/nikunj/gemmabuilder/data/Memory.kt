package com.nikunj.gemmabuilder

import android.content.Context
import java.io.File

// Long-term memory for Chat: short facts the user asked the assistant to remember ("remember that I'm
// vegetarian"). They're stored on the phone, listed in Settings, and added to every Chat's system
// prompt. The app handles remember/forget itself, so it works the same with any model.

private const val MAX_MEMORIES = 50
private const val MAX_MEMORY_CHARS = 300

private fun memoryFile(context: Context) = File(context.filesDir, "memory.txt")

fun loadMemories(context: Context): List<String> =
    runCatching { memoryFile(context).readLines() }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }

fun saveMemories(context: Context, memories: List<String>) {
    memoryFile(context).writeText(memories.joinToString("\n", postfix = if (memories.isEmpty()) "" else "\n"))
}

/** Adds [fact] unless it's already there; keeps the newest [MAX_MEMORIES]. */
fun withMemory(memories: List<String>, fact: String): List<String> {
    val clean = fact.trim().trimEnd('.').replace(Regex("\\s+"), " ").take(MAX_MEMORY_CHARS)
    if (clean.isEmpty() || memories.any { it.equals(clean, ignoreCase = true) }) return memories
    return (memories + clean).takeLast(MAX_MEMORIES)
}

/** Memories that [query] refers to: every word of the query appears in the memory. */
fun matchingMemories(memories: List<String>, query: String): List<String> {
    val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 && it !in setOf("that", "the", "about", "and") }
    if (words.isEmpty()) return emptyList()
    return memories.filter { memory -> words.all { it in memory.lowercase() } }
}

sealed class MemoryCommand {
    data class Remember(val fact: String) : MemoryCommand()
    data class Forget(val query: String) : MemoryCommand()
    data object ShowAll : MemoryCommand()
}

private val rememberRegex = Regex("""^(?:please\s+)?remember(?:\s+that)?[:,]?\s+(.+)$""", RegexOption.IGNORE_CASE)
private val forgetRegex = Regex("""^(?:please\s+)?forget(?:\s+(?:that|about))?[:,]?\s+(.+)$""", RegexOption.IGNORE_CASE)
private val listRegex = Regex(
    """^(?:what\s+do\s+you\s+(?:remember|know)\s+about\s+me|what\s+have\s+you\s+remembered|show\s+(?:my\s+)?memor(?:y|ies))\??$""",
    RegexOption.IGNORE_CASE
)

/** A message that manages memory, or null for an ordinary message. */
fun memoryCommandFrom(message: String): MemoryCommand? {
    val text = message.trim().trimEnd('.', '!')
    if (listRegex.matches(text)) return MemoryCommand.ShowAll
    rememberRegex.find(text)?.let { return MemoryCommand.Remember(it.groupValues[1].trim()) }
    forgetRegex.find(text)?.let { return MemoryCommand.Forget(it.groupValues[1].trim()) }
    return null
}

/** The system prompt section that tells the model what it remembers about the user. */
fun memoryPromptSection(memories: List<String>): String =
    if (memories.isEmpty()) "" else buildString {
        append("\n\nThings the user asked you to remember about them (use them when relevant; don't list them unprompted):\n")
        memories.forEach { append("- ").append(it).append('\n') }
    }
