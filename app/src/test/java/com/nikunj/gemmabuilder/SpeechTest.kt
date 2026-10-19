package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTest {
    @Test
    fun speaksPlainTextWithoutSourcesOrMarkup() {
        val reply = """
            ## Laptop deals
            - **HP Chromebook 15**: ${'$'}199 — basic [1]
            - See [the list](https://example.com/list) *today*

            > ⚠️ These numbers weren't found in the sources.

            **Sources**
            1. [TechRadar](https://techradar.com)
        """.trimIndent()
        assertEquals("Laptop deals\nHP Chromebook 15: ${'$'}199 — basic\nSee the list today", speakableText(reply))
    }

    @Test
    fun chunksAtSentenceEnds() {
        val chunks = speechChunks("One two. Three four five. Six.", maxChars = 16)
        assertEquals(listOf("One two.", "Three four five.", "Six."), chunks)
        assertTrue(speechChunks("x".repeat(50), maxChars = 20).all { it.length <= 20 })
    }
}
