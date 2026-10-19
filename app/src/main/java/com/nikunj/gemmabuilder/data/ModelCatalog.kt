package com.nikunj.gemmabuilder

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.os.StatFs
import java.io.File
import java.security.MessageDigest

/**
 * A model the app can download directly. Every entry is pinned to a Hugging Face commit and checked
 * against its SHA-256 after download, so users get exactly the file that was vetted here.
 * All entries are public (no login) and Apache-2.0 licensed.
 */
data class CatalogModel(
    val id: String,
    val name: String,
    val engine: String,
    val repo: String,
    val revision: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val minRamGb: Int,
    val summary: String
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/$revision/$fileName"
    val isGguf: Boolean get() = isGgufModel(fileName)
}

val MODEL_CATALOG = listOf(
    CatalogModel(
        id = "gemma-4-e2b-litert",
        name = "Gemma 4 E2B",
        engine = "LiteRT",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        revision = "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        minRamGb = 6,
        summary = "Recommended for most phones. Fast on the GPU and light on memory."
    ),
    CatalogModel(
        id = "gemma-4-e4b-litert",
        name = "Gemma 4 E4B",
        engine = "LiteRT",
        repo = "litert-community/gemma-4-E4B-it-litert-lm",
        revision = "2eee7ac325f20eb8c9ac1d0e972f7c84663062da",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        // Runs at full precision on the GPU (exact numbers) and peaks around 6.5 GB.
        minRamGb = 12,
        summary = "Recommended for phones with 12 GB of RAM. Clearly smarter than E2B."
    ),
    CatalogModel(
        id = "qwen3-0.6b-litert",
        name = "Qwen3 0.6B",
        engine = "LiteRT",
        repo = "litert-community/Qwen3-0.6B",
        revision = "e6687177e00897731e7716267eebd4eba0dde7c2",
        fileName = "Qwen3-0.6B.litertlm",
        sizeBytes = 613_722_264,
        sha256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",
        minRamGb = 4,
        summary = "Tiny and very fast. Fine for simple pages and quick questions."
    ),
    CatalogModel(
        id = "qwen2.5-coder-1.5b-gguf",
        name = "Qwen2.5 Coder 1.5B",
        engine = "GGUF",
        repo = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
        revision = "f86cb2c1fa58255f8052cc32aeede1b7482d4361",
        fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        sizeBytes = 1_117_320_768,
        sha256 = "cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046",
        minRamGb = 4,
        summary = "Trained for code. Small enough to be quick on the CPU."
    ),
    CatalogModel(
        id = "qwen3-4b-2507-gguf",
        name = "Qwen3 4B Instruct 2507",
        engine = "GGUF",
        repo = "unsloth/Qwen3-4B-Instruct-2507-GGUF",
        revision = "a06e946bb6b655725eafa393f4a9745d460374c9",
        fileName = "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        sizeBytes = 2_497_281_120,
        sha256 = "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
        minRamGb = 8,
        summary = "Strong all-rounder. Slower on CPU, best on recent flagships."
    ),
    CatalogModel(
        id = "gemma-4-e2b-gguf",
        name = "Gemma 4 E2B (GGUF)",
        engine = "GGUF",
        repo = "ggml-org/gemma-4-E2B-it-GGUF",
        revision = "b4243c156154b6dca9324415f8c7ccc098b4aed1",
        fileName = "gemma-4-E2B-it-Q4_0.gguf",
        sizeBytes = 2_841_481_184,
        sha256 = "8e30dff3ac4c8434c49a7036fa15564bdbb6044e42bf04550bf1a096ad7e6a52",
        minRamGb = 6,
        summary = "Gemma 4 through llama.cpp, for phones where the LiteRT build won't load."
    ),
    CatalogModel(
        id = "qwen3.5-4b-gguf",
        name = "Qwen3.5 4B",
        engine = "GGUF",
        repo = "unsloth/Qwen3.5-4B-GGUF",
        revision = "e87f176479d0855a907a41277aca2f8ee7a09523",
        fileName = "Qwen3.5-4B-Q4_0.gguf",
        sizeBytes = 2_583_221_408,
        sha256 = "298fcb5fe7a77ccc79745ae24751560c5ac56874caff4bb39b1f2055bd72b8bb",
        minRamGb = 8,
        summary = "Newer Qwen with thinking. Strong at reasoning and code; runs on the CPU."
    ),
    CatalogModel(
        id = "bonsai-8b-gguf",
        name = "Bonsai 8B (1-bit)",
        engine = "GGUF",
        repo = "prism-ml/Bonsai-8B-gguf",
        revision = "48516770dd04643643e9f9019a2a349cf26c5dbd",
        fileName = "Bonsai-8B-Q1_0.gguf",
        sizeBytes = 1_158_654_496,
        sha256 = "284a335aa3fb2ced3b1b01fcb40b08aa783e3b70832767f0dd2e3fdfa134bd54",
        minRamGb = 6,
        summary = "An 8B model squeezed to 1 bit per weight: 8B-class answers in a 1.2 GB download."
    ),
    CatalogModel(
        id = "bonsai-27b-gguf",
        name = "Bonsai 27B (1-bit)",
        engine = "GGUF",
        repo = "prism-ml/Bonsai-27B-gguf",
        revision = "f10afb355f104535e3e3e98cf7ab7795c72bd292",
        fileName = "Bonsai-27B-Q1_0.gguf",
        sizeBytes = 3_803_452_480,
        sha256 = "17ef842e47450caeb8eaa3ebfbbab5d2f2278b62b79be107985fb69a2f819aa0",
        minRamGb = 12,
        summary = "Experimental. Qwen3.6 27B at 1 bit per weight, the largest model that fits a phone. Slow on the CPU; needs a 12 GB flagship."
    )
)

/**
 * The model to suggest for this phone: E4B on 12 GB+ phones, E2B on 6–8 GB phones, and the small
 * Qwen3 0.6B below that. Phones report less than they are sold with (12 GB shows as ~10.9, 6 GB as ~5.5).
 */
fun recommendedModel(context: Context): CatalogModel {
    val ram = deviceRamGb(context)
    val id = when {
        ram >= 10.5 -> "gemma-4-e4b-litert"
        ram >= 5.0 -> "gemma-4-e2b-litert"
        else -> "qwen3-0.6b-litert"
    }
    return MODEL_CATALOG.first { it.id == id }
}

/** Downloaded models live in app-specific external storage: private to the app, no permission needed. */
fun downloadedModelsDir(context: Context): File =
    File(context.getExternalFilesDir(null) ?: context.filesDir, "models")

fun downloadedModelFile(context: Context, model: CatalogModel): File =
    File(downloadedModelsDir(context), model.fileName)

/** DownloadManager writes here; the file is renamed to its real name only after the hash checks out. */
fun partialModelFile(context: Context, model: CatalogModel): File =
    File(downloadedModelsDir(context), "${model.fileName}.part")

fun deviceRamGb(context: Context): Double {
    val info = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
    return info.totalMem / 1_073_741_824.0
}

fun freeBytesForDownloads(context: Context): Long {
    val dir = downloadedModelsDir(context).apply { mkdirs() }
    return StatFs(dir.absolutePath).availableBytes
}

/** Bytes already downloaded into the .part file, which a new download resumes from. */
fun partialBytes(context: Context, model: CatalogModel): Long =
    partialModelFile(context, model).takeIf { it.exists() }?.length() ?: 0L

/**
 * Pre-release test builds queued downloads with DownloadManager; cancel any that are left so they
 * can't write into the same .part file the download service now uses.
 */
fun cancelLegacySystemDownloads(context: Context) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    MODEL_CATALOG.forEach { model ->
        val key = "download_${model.id}"
        val id = prefs.getLong(key, -1L)
        if (id >= 0) {
            runCatching { manager.remove(id) }
            prefs.edit().remove(key).apply()
        }
    }
}

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 20)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun savedAllowMobileDataDownloads(context: Context): Boolean =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("allowMobileDataDownloads", false)

fun saveAllowMobileDataDownloads(context: Context, allow: Boolean) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("allowMobileDataDownloads", allow).apply()
}

val ACTIVE_DOWNLOAD_PHASES = setOf(
    DownloadPhase.Queued,
    DownloadPhase.WaitingForNetwork,
    DownloadPhase.Downloading,
    DownloadPhase.Verifying
)

fun formatBytes(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(java.util.Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
    else "${bytes / 1_000_000} MB"
