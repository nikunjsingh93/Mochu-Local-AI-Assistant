package com.nikunj.gemmabuilder

import java.io.File

fun isFocusedEditRequest(request: String, focusedContext: String, hasAttachments: Boolean): Boolean =
    focusedContext.isNotBlank() && !hasAttachments &&
        Regex("(?i)\\b(change|edit|fix|adjust|rename|remove|update|replace)\\b").containsMatchIn(request)

fun isExplicitProjectRewrite(request: String): Boolean =
    Regex("(?i)\\b(rewrite|rebuild|start over|replace (the )?(entire|whole) file)\\b").containsMatchIn(request)

/** Select short, verbatim excerpts for filenames or distinctive words in the user's request. */
fun focusedProjectContext(root: File, request: String, maxChars: Int = 6000): String {
    if (maxChars <= 0 || !root.isDirectory) return ""
    val ignored = setOf("please", "change", "make", "update", "project", "file", "code", "add", "remove", "with", "from", "this", "that", "into", "should", "using")
    val terms = Regex("[A-Za-z_][A-Za-z_0-9-]{3,}").findAll(request.lowercase())
        .map { it.value }.filterNot { it in ignored }.distinct().take(12).toList()
    val allowed = setOf("html", "htm", "css", "js", "jsx", "ts", "tsx", "py", "json", "xml", "md")
    val candidates = root.walkTopDown().filter { it.isFile && it.length() <= 2_000_000 && it.extension.lowercase() in allowed }
        .mapNotNull { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            val explicit = request.contains(path, ignoreCase = true) || request.contains(file.name, ignoreCase = true)
            val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            val lines = text.lines()
            val matched = lines.indices.filter { i -> terms.any { term -> lines[i].contains(term, ignoreCase = true) } }
            if (!explicit && matched.isEmpty()) return@mapNotNull null
            val score = (if (explicit) 1000 else 0) + matched.size.coerceAtMost(100)
            Triple(path, lines, score) to matched
        }.sortedByDescending { it.first.third }.take(3).toList()
    val out = StringBuilder()
    for ((file, matched) in candidates) {
        val (path, lines) = file
        val indices = if (matched.isEmpty()) (0 until minOf(lines.size, 25)).toList() else
            matched.take(5).flatMap { i -> (maxOf(0, i - 3)..minOf(lines.lastIndex, i + 3)).toList() }.distinct().sorted()
        val excerpt = indices.joinToString("\n") { lines[it] }
        if (excerpt.isBlank()) continue
        val remaining = maxChars - out.length
        if (remaining < 100) break
        out.append("File: $path (excerpts; other lines omitted)\n")
        out.append(excerpt.take((maxChars - out.length).coerceAtLeast(0)))
        out.append("\n\n")
    }
    return out.toString().take(maxChars)
}
