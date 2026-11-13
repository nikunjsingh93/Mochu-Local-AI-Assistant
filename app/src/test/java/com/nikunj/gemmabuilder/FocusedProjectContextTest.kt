package com.nikunj.gemmabuilder

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FocusedProjectContextTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun findsVerbatimRelevantLinesInMiddleOfLargeFile() {
        val root = temp.newFolder()
        val lines = (1..1000).map { if (it == 500) "  .sidebar { color: blue; }" else "line $it" }
        File(root, "index.html").writeText(lines.joinToString("\n"))
        val context = focusedProjectContext(root, "Change the sidebar styling")
        assertTrue(context.contains("  .sidebar { color: blue; }"))
        assertTrue(context.contains("index.html"))
        assertFalse(context.contains("line 1000"))
    }

    @Test fun explicitFilenameFindsFileWithoutMatchingContent() {
        val root = temp.newFolder()
        File(root, "app.js").writeText("const count = 3;")
        assertTrue(focusedProjectContext(root, "Update app.js").contains("const count = 3;"))
        assertEquals("", focusedProjectContext(root, "Explain this project"))
    }

    @Test fun recognizesFocusedEditsAndExplicitRewrites() {
        assertTrue(isFocusedEditRequest("Change the sidebar", "relevant excerpt", false))
        assertFalse(isFocusedEditRequest("Change the sidebar", "", false))
        assertFalse(isFocusedEditRequest("Change the sidebar", "excerpt", true))
        assertTrue(isExplicitProjectRewrite("Please rewrite this entire page"))
        assertFalse(isExplicitProjectRewrite("Change the sidebar color"))
    }
}
