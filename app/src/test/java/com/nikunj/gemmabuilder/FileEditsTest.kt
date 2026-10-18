package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileEditsTest {
    private val page = """
        <style>
          .counter { font-size: 24px; }
          button { padding: 8px; }
        </style>
        <div class="counter">0</div>
    """.trimIndent()

    @Test
    fun appliesExactMatch() {
        val result = applyFileEdits(page, listOf(FileEdit(".counter { font-size: 24px; }", ".counter { font-size: 64px; }")))
        assertEquals(page.replace("24px", "64px"), (result as EditResult.Applied).text)
    }

    @Test
    fun matchesLinesWhenIndentationDiffers() {
        val find = "    .counter { font-size: 24px; }\n        button { padding: 8px; }"
        val result = applyFileEdits(page, listOf(FileEdit(find, "  .counter { font-size: 64px; }")))
        val text = (result as EditResult.Applied).text
        assertTrue(text.contains("  .counter { font-size: 64px; }"))
        assertTrue(!text.contains("button { padding"))
        assertTrue(text.contains("<div class=\"counter\">0</div>"))
    }

    @Test
    fun appliesSeveralEditsInOrder() {
        val result = applyFileEdits(
            page,
            listOf(FileEdit("24px", "64px"), FileEdit(">0<", ">10<"))
        )
        val text = (result as EditResult.Applied).text
        assertTrue(text.contains("64px") && text.contains(">10<"))
    }

    @Test
    fun reportsSnippetThatIsNotInTheFile() {
        val result = applyFileEdits(page, listOf(FileEdit(".missing { color: red; }", "")))
        assertEquals(".missing { color: red; }", (result as EditResult.Missing).find)
    }

    @Test
    fun parsesEditActionWithMultiplePairs() {
        val response = """
            <action name="edit_file">
            <path>index.html</path>
            <find>
            .counter { font-size: 24px; }
            </find>
            <replace>
            .counter { font-size: 64px; }
            </replace>
            <find>>0<</find>
            <replace>>10<</replace>
            </action>
        """.trimIndent()
        val actions = parseEditFileActions(response)
        assertEquals(1, actions.size)
        assertEquals("index.html", actions[0].path)
        assertEquals(
            listOf(
                FileEdit(".counter { font-size: 24px; }", ".counter { font-size: 64px; }"),
                FileEdit(">0<", ">10<")
            ),
            actions[0].edits
        )
    }

    @Test
    fun ignoresUnclosedEditAction() {
        assertTrue(parseEditFileActions("<action name=\"edit_file\"><path>a.html</path><find>x</find>").isEmpty())
    }
}
