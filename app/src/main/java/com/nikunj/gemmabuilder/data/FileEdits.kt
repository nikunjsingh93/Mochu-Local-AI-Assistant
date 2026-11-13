package com.nikunj.gemmabuilder

// edit_file actions: small find/replace changes to an existing file instead of a full rewrite.

data class FileEdit(val find: String, val replace: String)

data class EditFileAction(val path: String, val edits: List<FileEdit>)

/** Stop retrying when a model repeatedly returns unusable edits. */
class CodeEditRetryGuard {
    private var failures = 0
    private var lastMissing: Pair<String, String>? = null

    fun invalidCall(): Boolean = ++failures >= 2

    fun missingEdit(missing: Pair<String, String>): Boolean {
        val repeated = lastMissing == missing
        lastMissing = missing
        return ++failures >= 2 || repeated
    }
}

/** Parses complete edit_file actions: a path plus one or more find/replace pairs. */
fun parseEditFileActions(text: String): List<EditFileAction> {
    val options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    val actionRegex = Regex("""<action\s+name=["']edit_file["']>(.*?)</action>""", options)
    val pathRegex = Regex("""<path>(.*?)</path>""", options)
    val pairRegex = Regex("""<find>\r?\n?(.*?)\r?\n?</find>\s*<replace>\r?\n?(.*?)\r?\n?</replace>""", options)
    val actions = actionRegex.findAll(text).mapNotNull { match ->
        val body = match.groupValues[1]
        val path = pathRegex.find(body)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: "index.html"
        val edits = pairRegex.findAll(body)
            .map { FileEdit(it.groupValues[1], it.groupValues[2]) }
            .filter { it.find.isNotBlank() }
            .toList()
        edits.takeIf { it.isNotEmpty() }?.let { EditFileAction(path, it) }
    }.toList()
    // LFM3 can use its native XML function-call format even when asked for an action.
    val calls = Regex("""<tool_call>\s*<function=edit_file>(.*?)</function>\s*</tool_call>""", options)
    val parameter = Regex("""<parameter=([\w]+)>\s*\r?\n?(.*?)\r?\n?\s*</parameter>""", options)
    val native = calls.findAll(text).mapNotNull { call ->
        val args = parameter.findAll(call.groupValues[1]).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val path = args["path"]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val old = args["find"] ?: args["old"] ?: args["old_text"] ?: if (args.containsKey("new") || args.containsKey("new_text")) args["replace"] else null
        val replacement = args["new"] ?: args["new_text"] ?: if (args.containsKey("find") || args.containsKey("old") || args.containsKey("old_text")) args["replace"] else null
        if (old.isNullOrBlank() || replacement == null) null else EditFileAction(path, listOf(FileEdit(old, replacement)))
    }.toList()
    return actions + native
}

sealed class EditResult {
    data class Applied(val text: String) : EditResult()
    /** [find] did not occur in the file, so the edit can't be placed. */
    data class Missing(val find: String) : EditResult()
}

/** Applies [edits] in order. Exact matches first; otherwise lines are compared ignoring indentation. */
fun applyFileEdits(original: String, edits: List<FileEdit>): EditResult {
    var text = original
    for (edit in edits) {
        val exact = text.indexOf(edit.find)
        // A short match can occur in several places; guessing would change the wrong code.
        if (exact >= 0 && text.indexOf(edit.find, exact + 1) >= 0) return EditResult.Missing(edit.find)
        val range = if (exact >= 0) exact until exact + edit.find.length else findLinesIgnoringIndent(text, edit.find)
        range ?: return EditResult.Missing(edit.find)
        text = text.replaceRange(range, edit.replace)
    }
    return EditResult.Applied(text)
}

/** Range in [text] of whole lines matching [snippet] line by line once surrounding whitespace is trimmed. */
private fun findLinesIgnoringIndent(text: String, snippet: String): IntRange? {
    val wanted = snippet.lines().map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
    if (wanted.isEmpty()) return null
    val lines = text.lines()
    val starts = IntArray(lines.size)
    var offset = 0
    lines.forEachIndexed { i, line -> starts[i] = offset; offset += line.length + 1 }
    var match: IntRange? = null
    for (first in 0..lines.size - wanted.size) {
        if (wanted.indices.all { lines[first + it].trim() == wanted[it] }) {
            val last = first + wanted.size - 1
            if (match != null) return null
            match = starts[first] until starts[last] + lines[last].length
        }
    }
    return match
}
