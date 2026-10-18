package com.nikunj.gemmabuilder

// edit_file actions: small find/replace changes to an existing file instead of a full rewrite.

data class FileEdit(val find: String, val replace: String)

data class EditFileAction(val path: String, val edits: List<FileEdit>)

/** Parses complete edit_file actions: a path plus one or more find/replace pairs. */
fun parseEditFileActions(text: String): List<EditFileAction> {
    val options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    val actionRegex = Regex("""<action\s+name=["']edit_file["']>(.*?)</action>""", options)
    val pathRegex = Regex("""<path>(.*?)</path>""", options)
    val pairRegex = Regex("""<find>\r?\n?(.*?)\r?\n?</find>\s*<replace>\r?\n?(.*?)\r?\n?</replace>""", options)
    return actionRegex.findAll(text).mapNotNull { match ->
        val body = match.groupValues[1]
        val path = pathRegex.find(body)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: "index.html"
        val edits = pairRegex.findAll(body)
            .map { FileEdit(it.groupValues[1], it.groupValues[2]) }
            .filter { it.find.isNotBlank() }
            .toList()
        edits.takeIf { it.isNotEmpty() }?.let { EditFileAction(path, it) }
    }.toList()
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
    for (first in 0..lines.size - wanted.size) {
        if (wanted.indices.all { lines[first + it].trim() == wanted[it] }) {
            val last = first + wanted.size - 1
            return starts[first] until starts[last] + lines[last].length
        }
    }
    return null
}
