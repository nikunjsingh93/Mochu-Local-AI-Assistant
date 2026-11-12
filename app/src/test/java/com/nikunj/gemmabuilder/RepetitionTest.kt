package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepetitionTest {
    private val answer = "Here are the deals:\n\n- MacBook Air M4: \$799 [1]\n- Dell XPS 13: \$999 [2]\n"

    @Test
    fun normalTextIsNotFlagged() {
        val long = (1..40).joinToString("\n") { "- Laptop number $it costs \$${500 + it * 10} and has ${8 + it % 3 * 8}GB RAM." }
        assertNull(degenerateRepetitionStart(long))
    }

    @Test
    fun pipeLoopIsTrimmedBackToTheAnswer() {
        val text = answer + "| ".repeat(400)
        assertEquals(answer.trimEnd(), text.substring(0, degenerateRepetitionStart(text)!!).trimEnd())
    }

    @Test
    fun repeatedSentenceKeepsOneCopy() {
        val loop = "The best laptop is the one that fits your budget. "
        val text = answer + loop.repeat(30)
        val kept = text.substring(0, degenerateRepetitionStart(text)!!)
        assertEquals(true, kept.startsWith(answer))
        assertEquals(true, kept.length < answer.length + loop.length * 2)
    }

    @Test
    fun shortTextIsIgnored() {
        assertNull(degenerateRepetitionStart("| | | |"))
    }

    @Test
    fun similarCodeLinesAreNotFlaggedWhileStreaming() {
        val code = "```javascript\n" + (1..30).joinToString("\n") {
            "sun.shadow.camera.propertyNumber$it = ${it * 10};"
        } + "\nconst materials = { grass: new THREE.MeshStandardMaterial() };"
        for (end in 1..code.length) {
            assertNull("False repetition at character $end", degenerateRepetitionStart(code.substring(0, end)))
        }
    }

    @Test
    fun repeatedCodeBlockIsStillStopped() {
        val block = "sun.shadow.camera.left = -42;\nsun.shadow.camera.right = 42;\n"
        val text = answer + block.repeat(8)
        assertEquals((answer + block).trimEnd(), text.substring(0, degenerateRepetitionStart(text)!!).trimEnd())
    }
}
