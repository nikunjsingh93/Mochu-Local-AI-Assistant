package com.nikunj.gemmabuilder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

// Searching Hugging Face for GGUF models and downloading one: the public API lists repositories and
// their files (with each file's SHA-256), and a picked file is downloaded by ModelDownloadService
// like a catalog model, then kept with the imported models.

/** A repository in search results. */
/** [parameters] is the model's total parameter count from its GGUF metadata, or 0 if unknown. */
data class HfRepo(val id: String, val downloads: Long, val likes: Long, val gated: Boolean, val parameters: Long = 0)

/** A GGUF file in a repository. */
data class HfFile(val path: String, val sizeBytes: Long, val sha256: String) {
    val fileName: String get() = path.substringAfterLast('/')
}

private const val HF = "https://huggingface.co"

private fun getJson(url: String): String {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "MochuAI")
        setRequestProperty("Accept", "application/json")
    }
    try {
        val code = c.responseCode
        if (code !in 200..299) error("Hugging Face answered $code")
        return c.inputStream.bufferedReader().readText()
    } finally {
        c.disconnect()
    }
}

/** GGUF repositories matching [query], most downloaded first. */
suspend fun searchHuggingFace(query: String): List<HfRepo> = withContext(Dispatchers.IO) {
    val q = URLEncoder.encode(query.trim(), "UTF-8")
    val array = JSONArray(getJson("$HF/api/models?search=$q&filter=gguf&sort=downloads&direction=-1&limit=25&expand%5B%5D=downloads&expand%5B%5D=likes&expand%5B%5D=gated&expand%5B%5D=gguf"))
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        // "gated" is false, or "auto"/"manual" when a login and approval are needed.
        val gated = o.opt("gated").let { it != null && it != false && it.toString() != "false" }
        HfRepo(o.getString("id"), o.optLong("downloads"), o.optLong("likes"), gated, o.optJSONObject("gguf")?.optLong("total") ?: 0)
    }
}

/** The GGUF model files in [repo] (vision projectors left out), smallest first. */
suspend fun huggingFaceFiles(repo: String): List<HfFile> = withContext(Dispatchers.IO) {
    val array = JSONArray(getJson("$HF/api/models/$repo/tree/main?recursive=true"))
    (0 until array.length()).mapNotNull { i ->
        val o = array.getJSONObject(i)
        val path = o.optString("path")
        // Split models (name-00001-of-00003.gguf) need every part, so they are left out.
        if (Regex("""-\d{5}-of-\d{5}\.gguf$""", RegexOption.IGNORE_CASE).containsMatchIn(path)) return@mapNotNull null
        if (o.optString("type") != "file" || !path.lowercase(Locale.US).endsWith(".gguf") || "mmproj" in path.lowercase(Locale.US)) return@mapNotNull null
        val lfs = o.optJSONObject("lfs")
        val sha = lfs?.optString("oid").orEmpty()
        if (sha.length != 64) return@mapNotNull null
        HfFile(path, lfs?.optLong("size")?.takeIf { it > 0 } ?: o.optLong("size"), sha)
    }.sortedBy { it.sizeBytes }
}

/** "MoE" models: a name like "24B-A2B" or "30B-A3B" (only a few billion parameters run per token). */
fun isMixtureOfExperts(name: String): Boolean = Regex("""(?i)\bA\d+(\.\d+)?B\b|[-_]A\d+(\.\d+)?B""").containsMatchIn(name)

/** Hugging Face files the user chose to download; download IDs start with "hf:". */
object HfDownloads {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun idFor(repo: String, file: HfFile) = "hf:$repo/${file.path}"

    fun isHf(id: String) = id.startsWith("hf:")

    /** Remembers [file] so ModelDownloadService can download it; returns its download ID. */
    fun register(context: Context, repo: String, file: HfFile): String {
        val id = idFor(repo, file)
        val all = JSONObject(prefs(context).getString("hfDownloads", "{}") ?: "{}")
        all.put(id, JSONObject().put("repo", repo).put("path", file.path).put("size", file.sizeBytes).put("sha", file.sha256))
        prefs(context).edit().putString("hfDownloads", all.toString()).apply()
        return id
    }

    /** The catalog-style entry the download service uses, or null if [id] isn't a chosen file. */
    fun find(context: Context, id: String): CatalogModel? {
        val o = JSONObject(prefs(context).getString("hfDownloads", "{}") ?: "{}").optJSONObject(id) ?: return null
        val path = o.getString("path")
        val name = path.substringAfterLast('/')
        return CatalogModel(
            id = id,
            name = name.substringBeforeLast('.'),
            engine = "GGUF",
            repo = o.getString("repo"),
            revision = "main",
            fileName = name,
            sourceFileName = path,
            sizeBytes = o.getLong("size"),
            sha256 = o.getString("sha"),
            minRamGb = 0,
            summary = "From Hugging Face: ${o.getString("repo")}"
        )
    }
}
