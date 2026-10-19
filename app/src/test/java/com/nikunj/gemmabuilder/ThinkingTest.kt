package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingTest {
    @Test
    fun splitsFinishedThinking() {
        assertEquals(
            ThinkingSplit("The user wants a note.", "Thanks so much!", stillThinking = false),
            splitThinking("<think>\nThe user wants a note.\n</think>\n\nThanks so much!")
        )
    }

    @Test
    fun streamsThinkingBeforeTheAnswer() {
        assertEquals(ThinkingSplit("", "", stillThinking = true), splitThinking("<thi"))
        assertEquals(ThinkingSplit("Let me", "", stillThinking = true), splitThinking("<think>Let me"))
        // A closing tag that has only partly arrived stays hidden.
        assertEquals(ThinkingSplit("Done.", "", stillThinking = true), splitThinking("<think>Done.</thi"))
    }

    @Test
    fun handlesTemplatesThatOpenTheTagInThePrompt() {
        assertEquals(ThinkingSplit("Plan first.", "Answer", stillThinking = false), splitThinking("Plan first.</think>Answer"))
    }

    @Test
    fun leavesPlainRepliesAlone() {
        assertEquals(ThinkingSplit("", "Hello <b>there</b>", stillThinking = false), splitThinking("Hello <b>there</b>"))
        // Non-thinking Qwen3 replies start with an empty block.
        assertEquals(ThinkingSplit("", "Hi!", stillThinking = false), splitThinking("<think>\n\n</think>\n\nHi!"))
    }
}
