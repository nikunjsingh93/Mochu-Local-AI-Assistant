package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryTest {
    @Test
    fun parsesCommands() {
        assertEquals(MemoryCommand.Remember("I'm vegetarian"), memoryCommandFrom("Remember that I'm vegetarian."))
        assertEquals(MemoryCommand.Remember("my sister is Priya"), memoryCommandFrom("please remember: my sister is Priya"))
        assertEquals(MemoryCommand.Forget("vegetarian"), memoryCommandFrom("forget that vegetarian"))
        assertEquals(MemoryCommand.Forget("my sister"), memoryCommandFrom("Forget about my sister"))
        assertEquals(MemoryCommand.ShowAll, memoryCommandFrom("What do you remember about me?"))
        assertNull(memoryCommandFrom("Do you remember the capital of France?"))
        assertNull(memoryCommandFrom("Write a poem about remembering summer"))
    }

    @Test
    fun addsWithoutDuplicates() {
        val one = withMemory(emptyList(), "  I live in Toronto. ")
        assertEquals(listOf("I live in Toronto"), one)
        assertEquals(one, withMemory(one, "i live in toronto"))
        assertEquals(50, (1..60).fold(emptyList<String>()) { list, i -> withMemory(list, "fact $i") }.size)
    }

    @Test
    fun findsMemoriesToForget() {
        val memories = listOf("I'm vegetarian", "My sister is Priya", "I live in Toronto")
        assertEquals(listOf("My sister is Priya"), matchingMemories(memories, "my sister"))
        assertEquals(listOf("I'm vegetarian"), matchingMemories(memories, "that I'm vegetarian"))
        assertEquals(emptyList<String>(), matchingMemories(memories, "the"))
    }
}
