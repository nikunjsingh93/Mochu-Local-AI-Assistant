package com.nikunj.gemmabuilder

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeToolsTest {
    private val colors = CodeColors(Color.Red, Color.Green, Color.Gray, Color.Blue, Color.Magenta, Color.Yellow)

    private fun colorOf(highlighted: androidx.compose.ui.text.AnnotatedString, word: String): Color? {
        val start = highlighted.text.indexOf(word)
        return highlighted.spanStyles.firstOrNull { it.start <= start && it.end >= start + word.length }?.item?.color
    }

    @Test
    fun colorsPythonTokens() {
        val h = highlightCode("def greet(name):\n    # say hi\n    return \"hi \" + name * 2", "python", colors)
        assertEquals("def greet(name):\n    # say hi\n    return \"hi \" + name * 2", h.text)
        assertEquals(Color.Red, colorOf(h, "def"))
        assertEquals(Color.Red, colorOf(h, "return"))
        assertEquals(Color.Gray, colorOf(h, "# say hi"))
        assertEquals(Color.Green, colorOf(h, "\"hi \""))
        assertEquals(Color.Blue, colorOf(h, "2"))
        assertEquals(null, colorOf(h, "greet"))
    }

    @Test
    fun colorsHtmlTagsAndAttributes() {
        val h = highlightCode("<!DOCTYPE html>\n<!-- page -->\n<div class=\"box\">if this works</div>", "html", colors)
        assertEquals(Color.Magenta, colorOf(h, "<!DOCTYPE"))
        assertEquals(Color.Gray, colorOf(h, "<!-- page -->"))
        assertEquals(Color.Magenta, colorOf(h, "<div"))
        assertEquals(Color.Yellow, colorOf(h, "class"))
        assertEquals(Color.Green, colorOf(h, "\"box\""))
        // Words in page text aren't code keywords.
        assertEquals(null, colorOf(h, "if"))
    }

    @Test
    fun namesFilesByTypeAndTitle() {
        assertEquals("Snake-Game.html", codeFileName("<html><head><title>Snake Game</title></head></html>", "html"))
        assertTrue(codeFileName("print(1)", "python").matches(Regex("""mochu-py-\d{8}-\d{6}\.py""")))
        assertTrue(codeFileName("hello", "").endsWith(".txt"))
        assertEquals("ts" to "text/plain", codeFileType("typescript"))
    }

    @Test
    fun tablesBecomeCsv() {
        assertEquals("Name,Price\n**Fire HD**,\"1,299.99\"".replace("**", ""), tableToCsv(listOf(listOf("Name", "Price"), listOf("**Fire HD**", "1,299.99"))))
        assertEquals("a,\"say \"\"hi\"\"\"", tableToCsv(listOf(listOf("a", "say \"hi\""))))
    }
}
