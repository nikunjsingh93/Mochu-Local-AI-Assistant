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
    external fun nativeGenerate(handle: Long, prompt: String, negativePrompt: String, width: Int, height: Int, steps: Int, seed: Long): ByteArray?
    external fun nativeProgress(): IntArray
    external fun nativeCancel(handle: Long)
    external fun nativeFree(handle: Long)
}

/** The image model offered on the Models page. */
val IMAGE_MODEL_ID = "sd-turbo-image"

/** Turbo models are distilled to work in a few steps; 4 is a good balance of quality and time. */
const val IMAGE_STEPS = 4
private const val IMAGE_SIZE = 512

/**
 * Makes one image on the CPU with stable-diffusion.cpp. The model is loaded for each image and freed
 * right after, so its memory is only in use while an image is being made.
 */
class ImageGenerator(private val context: Context) {
    @Volatile private var handle = 0L

    /** Returns the saved PNG, or null if the model failed or [cancel] was called. */
    suspend fun generate(prompt: String): File? = withContext(Dispatchers.Default) {
        val model = MODEL_CATALOG.firstOrNull { it.id == IMAGE_MODEL_ID } ?: return@withContext null
        val file = downloadedModelFile(context, model).takeIf { it.exists() } ?: return@withContext null
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
        val h = StableDiffusionNative.nativeLoad(file.absolutePath, threads)
        if (h == 0L) return@withContext null
        handle = h
        try {
            val rgb = StableDiffusionNative.nativeGenerate(
                h, prompt, "blurry, low quality, deformed, watermark, text", IMAGE_SIZE, IMAGE_SIZE, IMAGE_STEPS, System.nanoTime() and 0x7fffffff
            ) ?: return@withContext null
            savePng(rgb, IMAGE_SIZE, IMAGE_SIZE)
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
