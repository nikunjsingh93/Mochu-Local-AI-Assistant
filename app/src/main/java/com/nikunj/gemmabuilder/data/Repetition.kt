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

    // Common prefixes in code and lists can occur many times without being a loop. Require
    // consecutive identical blocks covering at least 240 characters, including the current tail.
    for (period in 1..minOf(200, text.length / 6)) {
        val copies = maxOf(6, (240 + period - 1) / period)
        val length = period * copies
        if (length > text.length) continue
        var matches = true
        for (i in text.length - length until text.length - period) {
            if (text[i] != text[i + period]) {
                matches = false
                break
            }
        }
        if (!matches) continue
        var start = text.length - length
        while (start > 0 && text[start - 1] == text[start - 1 + period]) start--
        return start + period
    }
    return null
}

/** Thrown inside a collector to end generation early (user pressed Stop, or a repetition loop). */
class StopGeneration(val reason: String) : RuntimeException(reason)
