package com.nikunj.gemmabuilder

import java.io.File
import java.util.UUID

/** A request's backup is kept outside the project, so it never enters exports or model context. */
class ProjectUndo(private val root: File, private val storage: File) {
    private val latest get() = File(storage, "latest")

    fun begin(): File {
        storage.mkdirs()
        val pending = File(storage, "pending-${UUID.randomUUID()}")
        copyTree(root, File(pending, "before"))
        return pending
    }

    fun finish(pending: File): List<String> {
        try {
            copyTree(root, File(pending, "after"))
            val changed = changes(pending)
            if (changed.isEmpty()) return changed
            // Keep the previous undo until the new backup is ready.
            val previous = File(storage, "previous")
            previous.deleteRecursively()
            if (latest.exists()) check(latest.renameTo(previous)) { "Could not save previous undo." }
            if (!pending.renameTo(latest)) {
                previous.renameTo(latest)
                error("Could not save undo snapshot.")
            }
            previous.deleteRecursively()
            return changed
        } finally {
            pending.deleteRecursively()
        }
    }

    fun changedFiles(): List<String> = if (latest.isDirectory) changes(latest) else emptyList()

    /** Refuse the whole restore if any affected file has changed since the model's request. */
    fun restore(): List<String> {
        val changed = changedFiles()
        require(changed.isNotEmpty()) { "There is no request to undo." }
        val before = File(latest, "before")
        val after = File(latest, "after")
        val conflicts = changed.filter { !sameFile(File(root, it), File(after, it)) }
        require(conflicts.isEmpty()) { "Files changed since this request: ${conflicts.joinToString()}. Undo was not applied." }
        for (path in changed) {
            val target = File(root, path)
            val original = File(before, path)
            if (original.isFile) {
                target.parentFile?.mkdirs()
                original.copyTo(target, overwrite = true)
            } else if (target.exists()) {
                check(target.delete()) { "Could not remove $path." }
            }
        }
        latest.deleteRecursively()
        return changed
    }

    private fun changes(snapshot: File): List<String> {
        val before = File(snapshot, "before")
        val after = File(snapshot, "after")
        return (paths(before) + paths(after)).toSortedSet().filter { !sameFile(File(before, it), File(after, it)) }
    }

    private fun paths(dir: File): Set<String> = if (!dir.exists()) emptySet() else
        dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()

    private fun sameFile(a: File, b: File): Boolean {
        if (!a.exists() || !b.exists()) return !a.exists() && !b.exists()
        if (!a.isFile || !b.isFile || a.length() != b.length()) return false
        return a.inputStream().buffered().use { left -> b.inputStream().buffered().use { right ->
            val x = ByteArray(8192); val y = ByteArray(8192)
            while (true) {
                val n = left.read(x)
                val m = right.read(y)
                if (n != m) return@use false
                if (n < 0) return@use true
                if (!x.copyOf(n).contentEquals(y.copyOf(n))) return@use false
            }
            @Suppress("UNREACHABLE_CODE") false
        } }
    }

    private fun copyTree(source: File, destination: File) {
        check(destination.mkdirs() || destination.isDirectory) { "Could not create undo snapshot." }
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            val out = File(destination, file.relativeTo(source).path)
            out.parentFile?.mkdirs()
            file.copyTo(out, overwrite = true)
        }
    }
}
