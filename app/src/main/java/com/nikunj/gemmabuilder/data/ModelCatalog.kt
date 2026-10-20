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
    val summary: String,
    /** A support file (a GGUF vision projector) listed under its model rather than on its own. */
    val hidden: Boolean = false,
    /** Id of the vision projector that lets this GGUF model see photos. */
    val visionId: String? = null,
    /** File name in the repo when it differs from the local [fileName]. */
    val sourceFileName: String = fileName,
    /** Largest context window offered for this model (what it was trained for, capped at 128K). */
    val maxContext: Int = 32768,
    /** Approximate context memory per token at F16, until a load measures the real figure. */
    val kvBytesPerToken: Long = 0,
    /** Image models: default and largest sensible number of steps, and the sampler they need ("" = default). */
    val imageSteps: Int = 4,
    val imageMaxSteps: Int = 8,
    val imageSampler: String = ""
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/$revision/$sourceFileName"
    val isGguf: Boolean get() = isGgufModel(fileName)
    /** An image-generation model (stable-diffusion.cpp), not a chat model. */
    val isImage: Boolean get() = engine == "Image"
}

val MODEL_CATALOG = listOf(
    CatalogModel(
        id = "gemma-4-e2b-litert",
        name = "Gemma 4 E2B",
        engine = "LiteRT",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        revision = "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1",
        fileName = "gemma-4-E2B-it.litertlm",
        maxContext = 131072, kvBytesPerToken = GEMMA4_E2B_KV,
        sizeBytes = 2_588_147_712,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        minRamGb = 6,
        summary = "Fast on the GPU, sees photos, and stays within memory at a 16K context on phones with 8 GB of RAM or more."
    ),
    CatalogModel(
        id = "gemma-4-e4b-litert",
        name = "Gemma 4 E4B",
        engine = "LiteRT",
        repo = "litert-community/gemma-4-E4B-it-litert-lm",
        revision = "2eee7ac325f20eb8c9ac1d0e972f7c84663062da",
        fileName = "gemma-4-E4B-it.litertlm",
        maxContext = 131072, kvBytesPerToken = GEMMA4_E4B_KV,
        sizeBytes = 3_659_530_240,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        // Runs at full precision on the GPU (exact numbers) and peaks around 6.5 GB.
        minRamGb = 12,
        summary = "Smarter than E2B but slower and much heavier on memory. Best on 12 GB+ phones with a smaller context."
    ),
    CatalogModel(
        id = "qwen3-0.6b-litert",
        name = "Qwen3 0.6B",
        engine = "LiteRT",
        repo = "litert-community/Qwen3-0.6B",
        revision = "e6687177e00897731e7716267eebd4eba0dde7c2",
        fileName = "Qwen3-0.6B.litertlm",
        maxContext = 32768, kvBytesPerToken = 114_688L,
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
        maxContext = 32768, kvBytesPerToken = 28_672L,
        sizeBytes = 1_117_320_768,
        sha256 = "cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046",
        minRamGb = 4,
        summary = "Trained for code. Small enough to be quick on the CPU."
    ),
    CatalogModel(
        id = "lfm2.5-1.2b-gguf",
        name = "LFM2.5 1.2B",
        engine = "GGUF",
        repo = "LiquidAI/LFM2.5-1.2B-Instruct-GGUF",
        revision = "8ed288026e23958ad9dfa92d53ed773a8eee7125",
        fileName = "LFM2.5-1.2B-Instruct-Q4_0.gguf",
        maxContext = 32768, kvBytesPerToken = 12_288L,
        sizeBytes = 695_751_488,
        sha256 = "2ea801949d760cdf1a2cc04a54262c22c3c0c54f0769d57760c9adeb0e59233f",
        minRamGb = 3,
        summary = "Small and quick on the CPU: the best pick for phones with 6 GB of RAM or less (e.g. Helio G-series). Good for chat, notes and web answers. LFM Open License."
    ),
    CatalogModel(
        id = "gemma-3-1b-gguf",
        name = "Gemma 3 1B",
        engine = "GGUF",
        repo = "unsloth/gemma-3-1b-it-GGUF",
        revision = "f0b45be0aac41bd6a100a4b5734cad5f67255bfb",
        fileName = "gemma-3-1b-it-Q4_0.gguf",
        maxContext = 32768, kvBytesPerToken = 5_120L,
        sizeBytes = 721_918_496,
        sha256 = "27ee88e03be02e9ba73def9a819d570d8ad73716e50769e87f374ae394b0276e",
        minRamGb = 3,
        summary = "Google's smallest Gemma, light on memory and fine on low-end phones. Simple answers; no photos."
    ),
    CatalogModel(
        id = "qwen3-4b-2507-gguf",
        name = "Qwen3 4B Instruct 2507",
        engine = "GGUF",
        repo = "unsloth/Qwen3-4B-Instruct-2507-GGUF",
        revision = "a06e946bb6b655725eafa393f4a9745d460374c9",
        fileName = "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        maxContext = 131072, kvBytesPerToken = 147_456L,
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
        maxContext = 131072, kvBytesPerToken = GEMMA4_E2B_KV,
        visionId = "gemma-4-e2b-gguf-vision",
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
        maxContext = 131072, kvBytesPerToken = 32_768L,
        visionId = "qwen3.5-4b-gguf-vision",
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
        maxContext = 32768, kvBytesPerToken = 147_456L,
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
        maxContext = 131072, kvBytesPerToken = 65_536L,
        visionId = "bonsai-27b-gguf-vision",
        sizeBytes = 3_803_452_480,
        sha256 = "17ef842e47450caeb8eaa3ebfbbab5d2f2278b62b79be107985fb69a2f819aa0",
        minRamGb = 12,
        summary = "Experimental. Qwen3.6 27B at 1 bit per weight, the largest model that fits a phone. Slow on the CPU; needs a 12 GB flagship."
    )
) + VISION_FILES + IMAGE_MODELS

// Gemma 4 keeps most layers on a short sliding window, so only a few layers grow with the context.
private const val GEMMA4_E2B_KV = 24_576L
private const val GEMMA4_E4B_KV = 36_864L

/** Vision projectors for the GGUF models above (downloaded with their model). */
/** Models that create images from a description in Chat. */
private val IMAGE_MODELS: List<CatalogModel> get() = listOf(
    CatalogModel(
        id = IMAGE_MODEL_ID, name = "SD-Turbo", engine = "Image",
        repo = "Green-Sky/SD-Turbo-GGUF", revision = "19a31586d02d64a73b4419bc193b3ecfaf38e1f0",
        fileName = "sd_turbo-f16-q8_0.gguf", sizeBytes = 2_023_745_376,
        sha256 = "d50be7655f0a554cf8041c145d88b210bd5f3c545423119dee62ae08cae51580", minRamGb = 6,
        summary = "Stable Diffusion Turbo: detailed images in 4 steps. Ask in Chat, e.g. \"draw a cat astronaut\". Stability AI Community License: free, including commercial use for people and businesses under $1M a year. Powered by Stability AI."
    ),
    CatalogModel(
        id = "sdxs-512-image", name = "SDXS-512", engine = "Image",
        repo = "concedo/sdxs-512-tinySDdistilled-GGUF", revision = "3144d898d61492f8382ffcabec055733fc5b2a0e",
        fileName = "sdxs-512-tinySDdistilled_Q8_0.gguf", sizeBytes = 682_847_200,
        sha256 = "409ab23582ee074c6b9d5395784fc0741b0599fb9d138686c69087c71678eb6a", minRamGb = 4,
        summary = "Tiny and the fastest: an image in a single step, with simpler detail than the others. OpenRAIL++ license: commercial use allowed.",
        imageSteps = 1, imageMaxSteps = 4
    ),
    CatalogModel(
        id = "dreamshaper-8-lcm-image", name = "DreamShaper 8 LCM", engine = "Image",
        repo = "Lykon/dreamshaper-8-lcm", revision = "4645d8bc6a8e6b106d21606d63e8460cdad4f1a6",
        fileName = "DreamShaper8_LCM.safetensors", sizeBytes = 2_133_804_992,
        sha256 = "a4f3e1526c5dc4fcbe342f5c410d83ae202c7a415fcefcbb92e0f93fcd0a87c3", minRamGb = 6,
        summary = "Artistic, detailed images (Stable Diffusion 1.5 tuned for speed) in about 6 steps. CreativeML OpenRAIL-M license: commercial use allowed.",
        imageSteps = 6, imageMaxSteps = 12, imageSampler = "lcm"
    )
)

private val VISION_FILES: List<CatalogModel> get() = listOf(
    CatalogModel(
        id = "gemma-4-e2b-gguf-vision", name = "Gemma 4 E2B photo support", engine = "GGUF",
        repo = "ggml-org/gemma-4-E2B-it-GGUF", revision = "6938c58e698502037401a5f19f6905f2fb2f7dee",
        fileName = "mmproj-gemma-4-E2B-it-Q8_0.gguf", sizeBytes = 557_368_064,
        sha256 = "9406f99c16d68cda4f1f0552192dcc99021ea1fc6d2fd50b1dc3ccf30d04b292", minRamGb = 0, summary = "", hidden = true
    ),
    CatalogModel(
        id = "qwen3.5-4b-gguf-vision", name = "Qwen3.5 4B photo support", engine = "GGUF",
        repo = "unsloth/Qwen3.5-4B-GGUF", revision = "e87f176479d0855a907a41277aca2f8ee7a09523",
        fileName = "Qwen3.5-4B-mmproj-F16.gguf", sizeBytes = 672_423_616,
        sha256 = "cd88edcf8d031894960bb0c9c5b9b7e1fea6ebee02b9f7ce925a00d12891f864", minRamGb = 0, summary = "", hidden = true,
        sourceFileName = "mmproj-F16.gguf"
    ),
    CatalogModel(
        id = "bonsai-27b-gguf-vision", name = "Bonsai 27B photo support", engine = "GGUF",
        repo = "prism-ml/Bonsai-27B-gguf", revision = "f10afb355f104535e3e3e98cf7ab7795c72bd292",
        fileName = "Bonsai-27B-mmproj-Q8_0.gguf", sizeBytes = 629_246_880,
        sha256 = "eb561d41a7bbeb0fcf04883c8af11078ef6cae0a66862a0b68443cfca495269d", minRamGb = 0, summary = "", hidden = true
    )
)

/** The vision projector file for a downloaded GGUF model, if it has one and it's downloaded. */
fun visionFileFor(context: Context, modelFileName: String): java.io.File? {
    val visionId = MODEL_CATALOG.firstOrNull { it.fileName == modelFileName }?.visionId ?: return null
    val vision = MODEL_CATALOG.firstOrNull { it.id == visionId } ?: return null
    return downloadedModelFile(context, vision).takeIf { it.exists() }
}

/** Whether this GGUF model can see photos once its projector is downloaded. */
fun hasVisionSupport(modelFileName: String): Boolean = MODEL_CATALOG.any { it.fileName == modelFileName && it.visionId != null }

/**
 * The model to suggest. Phones with 6 GB of RAM or less (they report under about 7 GB) get a 1B-class
 * model on the CPU: Gemma 4 E2B needs more memory than they can spare and can crash them.
 */
fun recommendedModel(context: Context): CatalogModel {
    val id = if (deviceRamGb(context) < 6.8) "lfm2.5-1.2b-gguf" else "gemma-4-e2b-litert"
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
