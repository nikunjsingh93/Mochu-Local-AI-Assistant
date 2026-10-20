package com.nikunj.gemmabuilder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// "Export as Android app": the :webshell APK (a WebView that shows assets/www) with the project's
// files added, its package name and label replaced, and signed with a key kept in Android's keystore.

private const val SHELL_PACKAGE = "com.mochu.app.p0000000000000000000000000000000000000000000"
private const val SHELL_LABEL = "MochuAppLabel000000000000000000000000000000000000"
private const val SIGNING_ALIAS = "mochu-app-export"

/** The package name an exported app gets: stable per project, so exporting again updates it in place. */
fun exportPackageName(appName: String, projectKey: String): String {
    val slug = appName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "").take(20).ifBlank { "app" }
    val suffix = Integer.toHexString(projectKey.hashCode()).padStart(8, '0')
    return "com.mochu.app.${slug}_$suffix".let { if (it[14].isDigit()) "com.mochu.app.a$slug$suffix" else it }
}

/** Builds the APK for the project at [root] into [out]. Python projects aren't supported yet. */
fun exportProjectApk(context: Context, root: File, appName: String, packageName: String, out: File) {
    val kind = projectKind(root)
    require(kind != ProjectKind.Python) { "Python projects can't be exported as an app yet. Export React or web projects." }
    require(packageName.length <= SHELL_PACKAGE.length) { "Package name too long." }
    val label = appName.trim().ifBlank { "Mochu App" }.take(SHELL_LABEL.length)
    val unsigned = File(out.parentFile, out.name + ".unsigned")
    AlignedZipWriter(unsigned.outputStream()).use { zip ->
        ZipInputStream(context.assets.open("shell/webshell.apk")).use { shell ->
            while (true) {
                val entry = shell.nextEntry ?: break
                if (entry.name.startsWith("META-INF/")) continue
                var bytes = shell.readBytes()
                if (entry.name == "AndroidManifest.xml") {
                    bytes = patchBinaryXmlStrings(bytes, mapOf(SHELL_PACKAGE to packageName, SHELL_LABEL to label))
                }
                // resources.arsc must stay uncompressed and aligned for Android 11+.
                zip.add(entry.name, bytes, compress = entry.name != "resources.arsc")
            }
        }
        // The project, under assets/www; React projects also carry the runtime that runs them.
        root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.forEach { file ->
            val path = file.relativeTo(root).path.replace('\\', '/')
            val bytes = if (kind == ProjectKind.React && path == "index.html") {
                reactPreviewHtml(file.readText()).replace("https://appassets.androidplatform.net/runtime/", "/runtime/").toByteArray()
            } else file.readBytes()
            zip.add("assets/www/$path", bytes, compress = true)
        }
        if (kind == ProjectKind.React) {
            context.assets.list("runtime/react").orEmpty().forEach { name ->
                zip.add("assets/www/runtime/react/$name", context.assets.open("runtime/react/$name").use { it.readBytes() }, compress = true)
            }
        }
    }
    val (key, cert) = exportSigningKey()
    try {
        ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("mochu", key, listOf(cert)).build()))
            .setInputApk(unsigned)
            .setOutputApk(out)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()
    } finally {
        unsigned.delete()
    }
}

/** The key exported apps are signed with, created on first export and kept in the Android keystore. */
private fun exportSigningKey(): Pair<PrivateKey, X509Certificate> {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (!store.containsAlias(SIGNING_ALIAS)) {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
            initialize(
                KeyGenParameterSpec.Builder(SIGNING_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=Mochu Code export"))
                    .setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 30L * 365 * 24 * 3600 * 1000))
                    .build()
            )
        }.generateKeyPair()
    }
    val key = store.getKey(SIGNING_ALIAS, null) as PrivateKey
    val cert = store.getCertificate(SIGNING_ALIAS) as X509Certificate
    return key to cert
}

/**
 * Replaces strings in an Android binary XML file's string pool in place. Each replacement must be
 * no longer than the original; the rest of its slot is zero-filled, so no offsets move.
 */
fun patchBinaryXmlStrings(xml: ByteArray, replacements: Map<String, String>): ByteArray {
    val out = xml.copyOf()
    val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
    val poolStart = buf.getShort(2).toInt() and 0xffff // the file header's size; the string pool follows it
    require(buf.getShort(poolStart).toInt() and 0xffff == 0x0001) { "No string pool in AndroidManifest.xml." }
    val stringCount = buf.getInt(poolStart + 8)
    val flags = buf.getInt(poolStart + 16)
    val stringsStart = poolStart + buf.getInt(poolStart + 20)
    val utf8 = flags and 0x100 != 0
    var replaced = 0
    for (i in 0 until stringCount) {
        val at = stringsStart + buf.getInt(poolStart + buf.getShort(poolStart + 2).toInt().and(0xffff) + i * 4)
        if (utf8) {
            var p = at
            val charLen = out[p].toInt() and 0xff; p += if (charLen and 0x80 != 0) 2 else 1
            val byteLen = out[p].toInt() and 0xff; p += if (byteLen and 0x80 != 0) 2 else 1
            if (charLen and 0x80 != 0 || byteLen and 0x80 != 0) continue
            val value = String(out, p, byteLen, Charsets.UTF_8)
            val replacement = replacements[value] ?: continue
            val bytes = replacement.toByteArray(Charsets.UTF_8)
            require(bytes.size <= byteLen && replacement.length < 0x80) { "Replacement for $value is too long." }
            out[at] = replacement.length.toByte()
            out[at + 1] = bytes.size.toByte()
            java.util.Arrays.fill(out, at + 2, p + byteLen + 1, 0)
            System.arraycopy(bytes, 0, out, at + 2, bytes.size)
            replaced++
        } else {
            val len = buf.getShort(at).toInt() and 0xffff
            if (len and 0x8000 != 0) continue
            val value = String(out, at + 2, len * 2, Charsets.UTF_16LE)
            val replacement = replacements[value] ?: continue
            require(replacement.length <= len) { "Replacement for $value is too long." }
            buf.putShort(at, replacement.length.toShort())
            java.util.Arrays.fill(out, at + 2, at + 2 + len * 2 + 2, 0)
            val bytes = replacement.toByteArray(Charsets.UTF_16LE)
            System.arraycopy(bytes, 0, out, at + 2, bytes.size)
            replaced++
        }
    }
    require(replaced >= replacements.size) { "The app shell's manifest didn't have the expected placeholders." }
    return out
}

/** A zip writer that 4-byte-aligns uncompressed entries, as APKs need. */
private class AlignedZipWriter(output: OutputStream) : AutoCloseable {
    private val counter = CountingStream(output)
    private val zip = ZipOutputStream(counter)

    fun add(name: String, bytes: ByteArray, compress: Boolean) {
        val entry = ZipEntry(name)
        if (compress) {
            entry.method = ZipEntry.DEFLATED
        } else {
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
            // Data starts after the 30-byte local header, the name and the extra field.
            val dataStart = counter.count + 30 + name.toByteArray(Charsets.UTF_8).size
            val pad = ((4 - dataStart % 4) % 4).toInt()
            if (pad > 0) entry.extra = ByteArray(pad)
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun close() = zip.close()
}

private class CountingStream(out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
        private set

    override fun write(b: Int) { out.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
}
