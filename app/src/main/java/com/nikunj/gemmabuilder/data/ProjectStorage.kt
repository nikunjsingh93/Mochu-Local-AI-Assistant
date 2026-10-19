package com.nikunj.gemmabuilder

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

fun sanitizeImportedFileName(name: String): String {
    val normalized = name
        .replace("\\", "/")
        .substringAfterLast('/')
        .trim()
        .ifBlank { "imported-file" }
    return normalized.replace(Regex("""[^\w.\- ]"""), "_")
}

fun writeAction(root: File, action: WriteFileAction) {
    val out = safeResolve(root, action.path)
    out.parentFile?.mkdirs()
    out.writeText(action.content)
}

fun projectRoot(context: Context, conversationId: String): File = File(projectsRoot(context), conversationId)

fun legacyProjectRoot(context: Context): File = File(projectsRoot(context), "default")

fun ensureProjectForConversation(context: Context, conversationId: String, migrateLegacy: Boolean): File {
    val root = projectRoot(context, conversationId)
    root.mkdirs()
    val index = File(root, "index.html")
    if (!index.exists()) {
        val legacy = legacyProjectRoot(context)
        val shouldMigrate = migrateLegacy && legacy.exists() && legacy.walkTopDown().any { it.isFile }
        if (shouldMigrate) {
            legacy.walkTopDown().filter { it.isFile }.forEach { source ->
                val destination = File(root, source.relativeTo(legacy).path)
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = true)
            }
        } else {
            index.writeText(welcomeHtml())
        }
    }
    return root
}

fun savedModelName(context: Context): String =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getString("modelName", null)
        ?.takeIf { it.isNotBlank() }
        ?: liteRtModelFile(context).takeIf { it.exists() }?.name
        ?: "Gemma model"

fun isLikelyMultimodalModel(name: String): Boolean {
    // Image/audio input is only wired up for LiteRT-LM models.
    if (isGgufModel(name)) return false
    val lower = name.lowercase(Locale.US)
    return lower.contains("multimodal") ||
        lower.contains("vision") ||
        lower.contains("audio") ||
        lower.contains("3n") ||
        lower.contains("e2b")
}

fun saveModelName(context: Context, name: String) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit()
        .putString("modelName", name.ifBlank { "Gemma model" })
        .apply()
}

fun savedChatFontScale(context: Context): Float =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getFloat("chatFontScale", 1.2f)
        .coerceIn(0.8f, 1.8f)

fun saveChatFontScale(context: Context, scale: Float) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit()
        .putFloat("chatFontScale", scale.coerceIn(0.8f, 1.8f))
        .apply()
}

fun savedCodeFontScale(context: Context): Float =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getFloat("codeFontScale", 1f)
        .coerceIn(0.8f, 1.8f)

fun saveCodeFontScale(context: Context, scale: Float) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit()
        .putFloat("codeFontScale", scale.coerceIn(0.8f, 1.8f))
        .apply()
}

/** Context window choices in Settings (tokens the model can hold: prompt plus reply). */
val CONTEXT_TOKEN_OPTIONS = listOf(4096, 8192, 16384, 32768)

fun savedContextTokens(context: Context): Int {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val saved = prefs.getInt("contextTokens", -1)
    if (saved in CONTEXT_TOKEN_OPTIONS) return saved
    // Earlier versions stored a character budget; map it to the window it used to load.
    return when (prefs.getInt("contextSizeChars", 24000)) {
        12000 -> 8192
        48000, 96000 -> 32768
        else -> 16384
    }
}

fun saveContextTokens(context: Context, tokens: Int) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit()
        .putInt("contextTokens", if (tokens in CONTEXT_TOKEN_OPTIONS) tokens else 16384)
        .apply()
}

/**
 * Characters of project snapshot (index.html, other files, recent chat) that fit in a window of
 * [windowTokens], after the system prompt and room for the reply (~3 characters per token).
 */
fun snapshotCharBudget(windowTokens: Int): Int {
    val replyReserve = minOf(windowTokens / 2, 4096)
    val systemPrompt = 900
    return ((windowTokens - replyReserve - systemPrompt).coerceAtLeast(1024)) * 3
}

// GPU is the default; LiteRtGemmaEngine falls back to CPU when the device can't run it.
fun savedBackendPreference(context: Context): String =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getString("backendPreference", "gpu")
        ?.lowercase(Locale.US)
        ?.takeIf { it in setOf("cpu", "gpu", "npu") }
        ?: "gpu"

fun saveBackendPreference(context: Context, backendPreference: String) {
    val normalized = backendPreference.lowercase(Locale.US).takeIf {
        it in setOf("cpu", "gpu", "npu")
    } ?: "gpu"
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit()
        .putString("backendPreference", normalized)
        .apply()
}

fun savedSpeculativeDecodingEnabled(context: Context): Boolean =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("speculativeDecodingEnabled", false)

fun saveSpeculativeDecodingEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("speculativeDecodingEnabled", enabled)
        .apply()
}

fun displayNameForUri(resolver: ContentResolver, uri: Uri, fallback: String = "Gemma model"): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index).orEmpty()
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { fallback } ?: fallback
}

// Keeps the original file name so existing installs find their imported model.
private fun liteRtModelFile(context: Context): File = File(context.filesDir, "models/gemma-4-e2b.litertlm")

private fun ggufModelFile(context: Context): File = File(context.filesDir, "models/model.gguf")

/** Where a model with this display name is stored; one slot per format. */
fun modelFileFor(context: Context, modelName: String): File =
    if (isGgufModel(modelName)) ggufModelFile(context) else liteRtModelFile(context)

/** The model to load: a downloaded model when one is selected, otherwise the imported model slot. */
fun modelFile(context: Context): File =
    savedActiveModelPath(context)?.let(::File)?.takeIf { it.exists() }
        ?: modelFileFor(context, savedModelName(context))

/** Absolute path of the selected downloaded model, or null when the imported model is used. */
fun savedActiveModelPath(context: Context): String? =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("activeModelPath", null)

fun saveActiveModelPath(context: Context, path: String?) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().apply {
        if (path == null) remove("activeModelPath") else putString("activeModelPath", path)
    }.apply()
}

/** Deletes the stored model of the other format so only one multi-GB model takes up space. */
fun deleteOtherModelFormats(context: Context, keepModelName: String) {
    val keep = modelFileFor(context, keepModelName)
    listOf(liteRtModelFile(context), ggufModelFile(context))
        .filter { it != keep }
        .forEach { it.delete() }
}

fun copyModel(resolver: ContentResolver, uri: Uri, outFile: File) {
    outFile.parentFile?.mkdirs()
    resolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open selected file." }
        outFile.outputStream().use { output -> input.copyTo(output) }
    }
}

data class EditOutcome(
    val writes: List<WriteFileAction>,
    val editCount: Int,
    /** (path, find text) of the first edit that didn't match, or null when every edit applied. */
    val missing: Pair<String, String>? = null
)

/**
 * Applies edit_file actions to the project, returning the edited files as full writes. An edit to a
 * file that a write_file in the same reply also creates is applied on top of that new content.
 */
fun applyEditActions(root: File, edits: List<EditFileAction>, writes: List<WriteFileAction>): EditOutcome {
    val current = mutableMapOf<String, String>()
    var count = 0
    for (action in edits) {
        val base = current[action.path]
            ?: writes.lastOrNull { it.path == action.path }?.content
            ?: runCatching { safeResolve(root, action.path).takeIf { it.exists() }?.readText() }.getOrNull()
            ?: return EditOutcome(emptyList(), 0, action.path to "(file does not exist)")
        when (val result = applyFileEdits(base, action.edits)) {
            is EditResult.Applied -> {
                current[action.path] = result.text
                count += action.edits.size
            }
            is EditResult.Missing -> return EditOutcome(emptyList(), 0, action.path to result.find)
        }
    }
    return EditOutcome(current.map { (path, text) -> WriteFileAction(path, text) }, count)
}

/** Chat's "think before answering" setting; on by default. */
fun savedThinkingEnabled(context: Context): Boolean =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("thinkingEnabled", true)

fun saveThinkingEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("thinkingEnabled", enabled).apply()
}

/** Chat's long-term memory switch; on by default. */
fun savedMemoryEnabled(context: Context): Boolean =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("memoryEnabled", true)

fun saveMemoryEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("memoryEnabled", enabled).apply()
}
