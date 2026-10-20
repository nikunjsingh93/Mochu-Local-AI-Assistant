package com.nikunj.gemmabuilder

// Reasoning models stream their thinking as <think>...</think> before the answer (LiteRT models via
// the engine, GGUF models like Qwen3 and Bonsai directly). This splits the two while text streams in.

/** A reply split into the model's reasoning and the answer shown to the user. */
data class ThinkingSplit(val thinking: String, val answer: String, val stillThinking: Boolean)

private const val OPEN_TAG = "<think>"
private const val CLOSE_TAG = "</think>"

fun splitThinking(raw: String): ThinkingSplit {
    val start = raw.trimStart()
    // The opening tag is still arriving, e.g. "<thi".
    if (start.isNotEmpty() && start.length < OPEN_TAG.length && OPEN_TAG.startsWith(start)) {
        return ThinkingSplit("", "", stillThinking = true)
    }
    val close = raw.indexOf(CLOSE_TAG)
    return when {
        start.startsWith(OPEN_TAG) -> {
            val body = start.removePrefix(OPEN_TAG)
            val end = body.indexOf(CLOSE_TAG)
            if (end >= 0) {
                ThinkingSplit(body.substring(0, end).trim(), body.substring(end + CLOSE_TAG.length).trimStart(), stillThinking = false)
            } else {
                ThinkingSplit(withoutPartialCloseTag(body).trim(), "", stillThinking = true)
            }
        }
        // Some chat templates open the tag in the prompt, so only the closing tag shows up.
        close >= 0 && !raw.substring(0, close).contains(OPEN_TAG) ->
            ThinkingSplit(raw.substring(0, close).trim(), raw.substring(close + CLOSE_TAG.length).trimStart(), stillThinking = false)
        else -> ThinkingSplit("", raw, stillThinking = false)
    }
}

/** Drops a closing tag that is only partly streamed ("...done.</thi"), so it never flashes on screen. */
private fun withoutPartialCloseTag(text: String): String {
    for (length in CLOSE_TAG.length - 1 downTo 2) {
        if (text.endsWith(CLOSE_TAG.take(length))) return text.dropLast(length)
    }
    return text
}

/**
 * Rewrites a model's own reasoning markers to <think>...</think> as text streams in. Gemma 4 GGUF
 * writes "<|channel>thought ... <channel|>"; Qwen-style models already use <think> and pass through.
 * A marker split across chunks is held back until it is complete.
 */
class ThinkTagTranslator {
    private val replacements = listOf("<|channel>thought" to "<think>", "<channel|>" to "</think>")
    private val pending = StringBuilder()

    fun feed(chunk: String): String {
        pending.append(chunk)
        var text = pending.toString()
        replacements.forEach { (from, to) -> text = text.replace(from, to) }
        // Keep a tail that could be the start of a marker.
        val hold = replacements.maxOf { (from, _) ->
            (minOf(from.length - 1, text.length) downTo 1).firstOrNull { text.endsWith(from.take(it)) } ?: 0
        }
        pending.setLength(0)
        pending.append(text.takeLast(hold))
        return text.dropLast(hold)
    }

    fun flush(): String = pending.toString().also { pending.setLength(0) }
}
