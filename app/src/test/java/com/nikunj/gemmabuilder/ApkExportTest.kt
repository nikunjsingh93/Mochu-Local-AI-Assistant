package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApkExportTest {
    /** A minimal binary XML file: header, then a UTF-16 string pool holding [strings]. */
    private fun binaryXml(strings: List<String>): ByteArray {
        val data = strings.map { s -> ByteBuffer.allocate(2 + s.length * 2 + 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(s.length.toShort()); s.forEach { putChar(it) }; putShort(0)
        }.array() }
        val poolHeader = 28
        val stringsStart = poolHeader + strings.size * 4
        val poolSize = stringsStart + data.sumOf { it.size }
        val buf = ByteBuffer.allocate(8 + poolSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0003); buf.putShort(8); buf.putInt(8 + poolSize)
        buf.putShort(0x0001); buf.putShort(poolHeader.toShort()); buf.putInt(poolSize)
        buf.putInt(strings.size); buf.putInt(0); buf.putInt(0); buf.putInt(stringsStart); buf.putInt(0)
        var offset = 0
        data.forEach { buf.putInt(offset); offset += it.size }
        data.forEach { buf.put(it) }
        return buf.array()
    }

    private fun readString(xml: ByteArray, index: Int): String {
        val buf = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN)
        val at = 8 + buf.getInt(8 + 20) + buf.getInt(8 + 28 + index * 4)
        val len = buf.getShort(at).toInt()
        return String(xml, at + 2, len * 2, Charsets.UTF_16LE)
    }

    @Test
    fun replacesPlaceholdersInPlace() {
        val xml = binaryXml(listOf("android", "com.mochu.app.p000000000000", "LabelPlaceholder0000"))
        val patched = patchBinaryXmlStrings(xml, mapOf("com.mochu.app.p000000000000" to "com.mochu.app.todo", "LabelPlaceholder0000" to "Todo"))
        assertEquals(xml.size, patched.size)
        assertEquals("android", readString(patched, 0))
        assertEquals("com.mochu.app.todo", readString(patched, 1))
        assertEquals("Todo", readString(patched, 2))
    }

    @Test
    fun packageNamesAreValidAndStable() {
        val name = exportPackageName("My Todo App!", "project-1")
        assertEquals(name, exportPackageName("My Todo App!", "project-1"))
        assertTrue(name.matches(Regex("""com\.mochu\.app\.[a-z][a-z0-9_]*""")))
        assertTrue(exportPackageName("123", "p").matches(Regex("""com\.mochu\.app\.[a-z][a-z0-9_]*""")))
    }
}

class ShellManifestTest {
    @Test
    fun patchesTheRealShellManifest() {
        val apk = java.io.File("../webshell/build/outputs/apk/release/webshell-release-unsigned.apk")
        if (!apk.exists()) return
        val manifest = java.util.zip.ZipFile(apk).use { zip -> zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readBytes() }
        val patched = patchBinaryXmlStrings(
            manifest,
            mapOf(
                "com.mochu.app.p0000000000000000000000000000000000000000000" to "com.mochu.app.todo_1234abcd",
                "MochuAppLabel000000000000000000000000000000000000" to "Todo"
            )
        )
        assertEquals(manifest.size, patched.size)
        val text = String(patched, Charsets.UTF_16LE) + String(patched, Charsets.UTF_8)
        assertTrue(text.contains("com.mochu.app.todo_1234abcd"))
        assertTrue(!text.contains("p0000000000000000000"))
    }
}
