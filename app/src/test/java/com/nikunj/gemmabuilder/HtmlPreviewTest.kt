package com.nikunj.gemmabuilder

import org.junit.Assert.*
import org.junit.Test

class HtmlPreviewTest {
    @Test fun recognizesDirectPreviewRequests() {
        assertTrue(wantsHtmlPreview("Please open it in the browser for me"))
        assertTrue(wantsHtmlPreview("Can you run the page?"))
        assertTrue(wantsHtmlPreview("preview the HTML"))
        assertFalse(wantsHtmlPreview("How do I open it in the browser?"))
        assertFalse(wantsHtmlPreview("open google.com"))
        assertFalse(wantsHtmlPreview("show"))
    }

    @Test fun findsLatestCompleteHtmlFence() {
        val first = "```html\n<html><body>First</body></html>\n```"
        val second = "```html\n<html><body>Second</body></html>\n```"
        assertEquals("<html><body>Second</body></html>", lastHtmlDocument(listOf(first, second)))
        assertEquals("<html><body>First</body></html>", lastHtmlDocument(listOf(first, "```html\n<html>incomplete")))
        assertNull(lastHtmlDocument(listOf("Here is <html>not a complete file</html>")))
        assertNull(lastHtmlDocument(listOf("```javascript\n<html></html>\n```")))
    }
}
