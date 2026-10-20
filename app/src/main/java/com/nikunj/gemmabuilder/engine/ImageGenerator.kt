package com.nikunj.gemmabuilder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** JNI surface of imagegen/src/main/cpp/sd_bridge.cpp (stable-diffusion.cpp, in libmochu_sd.so). */
internal object StableDiffusionNative {
    init {
        System.loadLibrary("mochu_sd")
    }

    external fun nativeLoad(modelPath: String, nThreads: Int): Long
    external fun nativeGenerate(handle: Long, prompt: String, negativePrompt: String, width: Int, height: Int, steps: Int, seed: Long, sampler: String): ByteArray?
    external fun nativeProgress(): IntArray
    external fun nativeCancel(handle: Long)
    external fun nativeFree(handle: Long)
}

/** The image model suggested first on the Models page. */
val IMAGE_MODEL_ID = "sd-turbo-image"

/** Image sizes offered (square). Most of these models were trained at 512; bigger takes much longer. */
val IMAGE_SIZE_OPTIONS = listOf(256, 384, 512, 768)

/** The image model Chat uses: the one picked on the Models page, or else any downloaded one. */
fun activeImageModel(context: Context): CatalogModel? {
    val images = MODEL_CATALOG.filter { it.isImage && downloadedModelFile(context, it).exists() }
    val saved = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("activeImageModel", null)
    return images.firstOrNull { it.id == saved } ?: images.firstOrNull()
}

fun saveActiveImageModel(context: Context, id: String) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("activeImageModel", id).apply()
}

/** Image size and steps chosen for a model on the Models page (defaults: 512 and the model's own steps). */
fun savedImageSettings(context: Context, model: CatalogModel): Pair<Int, Int> {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val size = prefs.getInt("imageSize_${model.fileName}", 512).takeIf { it in IMAGE_SIZE_OPTIONS } ?: 512
    val steps = prefs.getInt("imageSteps_${model.fileName}", model.imageSteps).coerceIn(1, model.imageMaxSteps)
    return size to steps
}

fun saveImageSettings(context: Context, model: CatalogModel, size: Int, steps: Int) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
        .putInt("imageSize_${model.fileName}", size).putInt("imageSteps_${model.fileName}", steps.coerceIn(1, model.imageMaxSteps)).apply()
}

/**
 * Makes one image on the CPU with stable-diffusion.cpp. The model is loaded for each image and freed
 * right after, so its memory is only in use while an image is being made.
 */
class ImageGenerator(private val context: Context) {
    @Volatile private var handle = 0L

    /** Returns the saved PNG, or null if the model failed or [cancel] was called. */
    suspend fun generate(model: CatalogModel, prompt: String, size: Int, steps: Int): File? = withContext(Dispatchers.Default) {
        val file = downloadedModelFile(context, model).takeIf { it.exists() } ?: return@withContext null
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
        val h = StableDiffusionNative.nativeLoad(file.absolutePath, threads)
        if (h == 0L) return@withContext null
        handle = h
        try {
            val rgb = StableDiffusionNative.nativeGenerate(
                h, prompt, "blurry, low quality, deformed, watermark, text", size, size, steps, System.nanoTime() and 0x7fffffff, model.imageSampler
            ) ?: return@withContext null
            savePng(rgb, size, size)
        } finally {
            handle = 0L
            StableDiffusionNative.nativeFree(h)
        }
    }

    /** Steps done and total for the image being made. */
    fun progress(): Pair<Int, Int> = StableDiffusionNative.nativeProgress().let { it[0] to it[1] }

    fun cancel() {
        handle.takeIf { it != 0L }?.let(StableDiffusionNative::nativeCancel)
    }

    private fun savePng(rgb: ByteArray, width: Int, height: Int): File {
        val pixels = IntArray(width * height) { i ->
            val r = rgb[i * 3].toInt() and 0xff
            val g = rgb[i * 3 + 1].toInt() and 0xff
            val b = rgb[i * 3 + 2].toInt() and 0xff
            (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
        val out = File(dir, "created-${System.currentTimeMillis()}.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }
}

/**
 * The description to draw when a Chat message asks for an image ("draw a fox", "generate an image of
 * a beach at sunset"), or null for anything else.
 */
fun imagePromptFrom(message: String): String? {
    val text = message.trim().trimEnd('.', '!', '?')
    val polite = """^(?:hey\s+)?(?:mochu[,\s]+)?(?:please\s+)?(?:(?:can|could|would|will)\s+you\s+)?(?:please\s+)?"""
    val drawVerb = Regex("""$polite(?:draw|paint|sketch|illustrate)\s+(?:me\s+)?(?:(?:an?|the)\s+)?(?:(?:image|picture|pic|drawing|painting|sketch|illustration)\s+of\s+)?(.{3,})$""", RegexOption.IGNORE_CASE)
    val makeVerb = Regex(
        """$polite(?:generate|create|make|render|design|show\s+me|give\s+me)\s+(?:me\s+)?(?:(?:an?|the|some)\s+)?(?:ai\s+)?(?:image|picture|pic|photo|drawing|painting|illustration|artwork|wallpaper|portrait|art)s?\s*(?:of|showing|with|for|:)?\s*(.{3,})$""",
        RegexOption.IGNORE_CASE
    )
    val match = drawVerb.find(text) ?: makeVerb.find(text) ?: return null
    return match.groupValues[1].trim().takeIf { it.length >= 3 }
}

/** Copies a created image into the phone's Pictures/Mochu album. */
fun saveImageToGallery(context: Context, path: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, File(path).name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Mochu")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } } != null
}.getOrDefault(false)
