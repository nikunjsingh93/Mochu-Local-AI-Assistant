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
    if (!index.exists() && root.listFiles().isNullOrEmpty()) {
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
    savedRemoteModelConfig(context)?.model?.takeIf { remoteModelActive(context) } ?: savedLocalModelName(context)

/** The selected on-phone model, whether or not a remote model is in use. */
fun savedLocalModelName(context: Context): String =
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
val CONTEXT_TOKEN_OPTIONS = listOf(4096, 8192, 16384, 32768, 65536, 131072)

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

/** The context window chosen for one model (by file name); the old app-wide setting is the default. */
fun savedModelContextTokens(context: Context, modelFileName: String): Int {
    val saved = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("contextTokens_$modelFileName", -1)
    return if (saved in CONTEXT_TOKEN_OPTIONS) saved else defaultContextTokens(context, modelFileName)
}

/**
 * The window a model starts with: 8K, which every model here fits in on a 6-8 GB phone. Gemma 4 E2B
 * gets 16K on 12 GB phones (they report about 11 GB), where it has been stable.
 */
fun defaultContextTokens(context: Context, modelFileName: String): Int =
    if (modelFileName == "gemma-4-E2B-it.litertlm" && deviceRamGb(context) >= 10.5) 16384 else 8192

fun saveModelContextTokens(context: Context, modelFileName: String, tokens: Int) {
    val value = CONTEXT_TOKEN_OPTIONS.lastOrNull { it <= tokens } ?: CONTEXT_TOKEN_OPTIONS.first()
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("contextTokens_$modelFileName", value).apply()
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

/** Read Chat replies aloud after a message spoken with the mic; on by default. */
fun savedSpeakVoiceReplies(context: Context): Boolean =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("speakVoiceReplies", true)

fun saveSpeakVoiceReplies(context: Context, enabled: Boolean) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("speakVoiceReplies", enabled).apply()
}

/** Chat's web search switch; on by default (Chat still only searches when a question needs it). */
fun savedWebSearchEnabled(context: Context): Boolean =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("webSearchEnabled", true)

fun saveWebSearchEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("webSearchEnabled", enabled).apply()
}

/** Names the user gave Code projects (project key → name); unnamed projects use their first chat's title. */
fun savedProjectNames(context: Context): Map<String, String> =
    context.getSharedPreferences("project_names", Context.MODE_PRIVATE).all
        .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

fun saveProjectName(context: Context, projectKey: String, name: String?) {
    context.getSharedPreferences("project_names", Context.MODE_PRIVATE).edit().apply {
        if (name.isNullOrBlank()) remove(projectKey) else putString(projectKey, name.trim())
    }.apply()
}

/** Number types a GGUF model can keep its context (KV cache) in, with their size relative to F16. */
enum class KvCacheType(val label: String, val ratio: Double) {
    F16("F16", 1.0),
    Q8_0("Q8_0", 34.0 / 64.0), // 32 values in 34 bytes; F16 takes 64
    Q4_0("Q4_0", 18.0 / 64.0)
}

fun savedKvCacheType(context: Context): KvCacheType {
    val name = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("kvCacheType", null)
    return KvCacheType.entries.firstOrNull { it.name == name } ?: KvCacheType.F16
}

fun saveKvCacheType(context: Context, type: KvCacheType) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("kvCacheType", type.name).apply()
}

/** What llama.cpp reported for a model's context memory: bytes per token (at F16) and a fixed part. */
fun saveMeasuredKv(context: Context, modelFileName: String, perTokenF16: Long, fixedBytes: Long) {
    context.getSharedPreferences("kv_sizes", Context.MODE_PRIVATE).edit()
        .putLong("perToken_$modelFileName", perTokenF16).putLong("fixed_$modelFileName", fixedBytes).apply()
}

/** Approximate memory a model uses with [tokens] of context: its weights plus the KV cache. */
fun estimatedRamBytes(context: Context, model: CatalogModel, tokens: Int, kvType: KvCacheType): Long? {
    val prefs = context.getSharedPreferences("kv_sizes", Context.MODE_PRIVATE)
    val perToken = prefs.getLong("perToken_${model.fileName}", 0L).takeIf { it > 0 } ?: model.kvBytesPerToken.takeIf { it > 0 } ?: return null
    val fixed = prefs.getLong("fixed_${model.fileName}", 0L)
    val ratio = if (model.isGguf) kvType.ratio else 1.0
    // Plus working buffers and the runtime itself.
    return model.sizeBytes + fixed + (perToken * tokens * ratio).toLong() + 400L * 1024 * 1024
}

/** How hard Chat thinks: 1 quick, 2 balanced, 3 deep (no limit). */
fun savedThinkingLevel(context: Context): Int =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("thinkingLevel", 1).coerceIn(1, 3)

fun saveThinkingLevel(context: Context, level: Int) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("thinkingLevel", level.coerceIn(1, 3)).apply()
}

/** Thinking tokens allowed for a level; -1 is no limit. */
fun thinkingBudget(level: Int): Int = when (level) {
    1 -> 512
    2 -> 2048
    else -> -1
}

/** True when there is a model to answer with: on the phone, or the user's own server. */
fun modelAvailable(context: Context): Boolean = remoteModelActive(context) || modelFile(context).exists()

/** The name of the model imported from a file (one at a time), or null. */
fun savedImportedModelName(context: Context): String? =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("importedModelName", null)
        ?.takeIf { modelFileFor(context, it).exists() }

fun saveImportedModelName(context: Context, name: String) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("importedModelName", name).apply()
}
