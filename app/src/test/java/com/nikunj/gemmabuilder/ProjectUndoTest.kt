package com.nikunj.gemmabuilder

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectUndoTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun restoresModifiedDeletedAndNewFiles() {
        val root = temp.newFolder("project")
        val undo = ProjectUndo(root, temp.newFolder("backups"))
        File(root, "old.txt").writeText("before")
        File(root, "deleted.txt").writeText("keep")
        val pending = undo.begin()
        File(root, "old.txt").writeText("after")
        File(root, "deleted.txt").delete()
        File(root, "new.txt").writeText("new")
        assertEquals(listOf("deleted.txt", "new.txt", "old.txt"), undo.finish(pending))
        undo.restore()
        assertEquals("before", File(root, "old.txt").readText())
        assertEquals("keep", File(root, "deleted.txt").readText())
        assertFalse(File(root, "new.txt").exists())
        assertTrue(undo.changedFiles().isEmpty())
    }

    @Test fun laterEditsPreventAnyRestore() {
        val root = temp.newFolder("project")
        val undo = ProjectUndo(root, temp.newFolder("backups"))
        val a = File(root, "a.txt").apply { writeText("old") }
        val b = File(root, "b.txt").apply { writeText("old") }
        val pending = undo.begin()
        a.writeText("model"); b.writeText("model")
        undo.finish(pending)
        b.writeText("manual")
        assertThrows(IllegalArgumentException::class.java) { undo.restore() }
        assertEquals("model", a.readText())
        assertEquals("manual", b.readText())
        assertEquals(2, undo.changedFiles().size)
    }

    @Test fun requestWithoutChangesKeepsPreviousUndo() {
        val root = temp.newFolder("project")
        val undo = ProjectUndo(root, temp.newFolder("backups"))
        val file = File(root, "a.txt").apply { writeText("old") }
        val first = undo.begin(); file.writeText("new"); undo.finish(first)
        val second = undo.begin(); assertTrue(undo.finish(second).isEmpty())
        undo.restore()
        assertEquals("old", file.readText())
    }
}
