package com.nikunj.gemmabuilder

// Small models sometimes fall into a loop (endless "| | |" or the same sentence over and over) and
// would keep going until the output limit. This spots that so generation can be stopped early.

/**
 * If the end of [text] has degenerated into repetition, returns the index where the repetition
 * starts (so the caller can trim it); otherwise null.
 */
fun degenerateRepetitionStart(text: CharSequence): Int? {
    if (text.length < 400) return null

    // A long run made of only two or three distinct characters, e.g. "| | | |" or "-----".
    val tail = text.subSequence(text.length - 400, text.length).toString()
    val tailSymbols = tail.filterNot { it.isWhitespace() }.toSet()
    if (tailSymbols.size <= 3) {
        val junk = tailSymbols + setOf(' ', '\t', '\n', '\r')
        var start = text.length
        while (start > 0 && text[start - 1] in junk) start--
        return start
    }

    // The same 40-character chunk repeated six or more times near the end.
    val window = text.subSequence(maxOf(0, text.length - 1200), text.length).toString()
    val chunk = window.takeLast(40)
    if (chunk.isBlank()) return null
    val hits = mutableListOf<Int>()
    var from = 0
    while (true) {
        val at = window.indexOf(chunk, from)
        if (at < 0) break
        hits += at
        from = at + 1
    }
    if (hits.size < 6) return null
    // The loop's period is the gap between repeats. Walk back over earlier copies (which may lie
    // before the window) so exactly one copy is kept.
    val period = hits[hits.size - 1] - hits[hits.size - 2]
    if (period <= 0) return null
    var start = text.length - window.length + hits.first()
    while (start - period >= 0 &&
        text.subSequence(start - period, start).toString() == text.subSequence(start, start + period).toString()
    ) {
        start -= period
    }
    return minOf(start + period, text.length)
}

/** Thrown inside a collector to end generation early (user pressed Stop, or a repetition loop). */
class StopGeneration(val reason: String) : RuntimeException(reason)
