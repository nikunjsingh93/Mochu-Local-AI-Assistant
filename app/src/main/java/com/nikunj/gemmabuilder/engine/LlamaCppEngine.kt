package com.nikunj.gemmabuilder

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** JNI surface of app/src/main/cpp/llama_bridge.cpp. */
internal object LlamaCppNative {
    const val SEND_OK = 0
    const val SEND_TEMPLATE_FAILED = 1
    const val SEND_DECODE_FAILED = 2
    const val SEND_CONTEXT_FULL = 3

    private var cpuVariant: String? = null

    /** Loads the native library and the best CPU backend once per process; returns the variant name. */
    @Synchronized
    fun ensureInitialized(nativeLibraryDir: String): String {
        cpuVariant?.let { return it }
        System.loadLibrary("ondevice_llama")
        return nativeInitBackends(nativeLibraryDir).also { cpuVariant = it }
    }

    external fun nativeInitBackends(nativeLibraryDir: String): String
    external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    external fun nativeContextSize(handle: Long): Int
    external fun nativeDescribe(handle: Long): String
    external fun nativeStartChat(handle: Long, systemPrompt: String)
    external fun nativeSendUser(handle: Long, userText: String, maxOutput: Int, thinking: Boolean, images: Array<String>): Int
    external fun nativeLoadVision(handle: Long, mmprojPath: String, nThreads: Int): Boolean
    external fun nativeNextToken(handle: Long): ByteArray?
    external fun nativeTokenCount(handle: Long): Int
    external fun nativeFree(handle: Long)
}

/** Runs .gguf models with llama.cpp on the CPU. */
class LlamaCppEngine(
    private val context: Context,
    private val modelPath: String,
    private val maxNumTokens: Int,
    /** The model's vision projector (mmproj .gguf), so it can see photos; null for text-only. */
    private val visionPath: String? = null
) : BuilderEngine {
    override var supportsImages: Boolean = false
        private set

    // llama.cpp contexts are not thread-safe, so every native call runs on this one thread.
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "llama-cpp") }
    private val dispatcher = executor.asCoroutineDispatcher()

    @Volatile private var handle = 0L
    @Volatile private var sessionKey: String? = null
    @Volatile private var sessionPrompt: String? = null
    @Volatile private var usedTokens = 0
    @Volatile private var lastStats: GenerationStats? = null
    private var contextSize = 0

    /** e.g. "qwen3 4B Q4_K - Medium · android_armv8.6_1". */
    var description: String = ""
        private set

    suspend fun load() = withContext(dispatcher) {
        val variant = LlamaCppNative.ensureInitialized(context.applicationInfo.nativeLibraryDir)
        require(variant.isNotEmpty()) { "No llama.cpp CPU backend could be loaded on this device." }
        // Leave a couple of cores for the UI; big.LITTLE phones gain little past four threads.
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)
        val loaded = LlamaCppNative.nativeLoad(modelPath, maxNumTokens, threads)
        require(loaded != 0L) { "llama.cpp could not load this .gguf model (unsupported architecture or not enough memory)." }
        handle = loaded
        contextSize = LlamaCppNative.nativeContextSize(loaded)
        // A failed projector only costs photos; the model still works for text.
        if (visionPath != null && java.io.File(visionPath).exists()) {
            supportsImages = runCatching { LlamaCppNative.nativeLoadVision(loaded, visionPath, threads) }.getOrDefault(false)
        }
        val cpuLabel = variant.removePrefix("libggml-cpu-").removeSuffix(".so")
        description = "${LlamaCppNative.nativeDescribe(loaded)} · $cpuLabel"
    }

    // Reasoning models here (Qwen3, Bonsai) write <think>...</think> into the text themselves.
    override fun generate(sessionKey: String, message: String, systemPrompt: String, thinking: Boolean, images: List<String>): Flow<String> = flow {
        val h = handle
        if (h == 0L) {
            emit("Model is not loaded.")
            return@flow
        }
        if (this@LlamaCppEngine.sessionKey != sessionKey || sessionPrompt != systemPrompt) {
            LlamaCppNative.nativeStartChat(h, systemPrompt)
            sessionPrompt = systemPrompt
            this@LlamaCppEngine.sessionKey = sessionKey
        }
        var finished = false
        try {
            when (LlamaCppNative.nativeSendUser(h, message, contextSize / 2, thinking, if (supportsImages) images.toTypedArray() else emptyArray())) {
                LlamaCppNative.SEND_OK -> Unit
                LlamaCppNative.SEND_CONTEXT_FULL -> error("Prompt is larger than the $contextSize-token context window.")
                LlamaCppNative.SEND_TEMPLATE_FAILED -> error("This model's chat template could not be applied.")
                else -> error("llama.cpp failed while reading the prompt.")
            }
            val decodeStart = System.nanoTime()
            var tokens = 0
            val tags = ThinkTagTranslator()
            while (true) {
                currentCoroutineContext().ensureActive()
                val bytes = LlamaCppNative.nativeNextToken(h) ?: break
                tokens++
                if (bytes.isNotEmpty()) tags.feed(String(bytes, Charsets.UTF_8)).takeIf { it.isNotEmpty() }?.let { emit(it) }
            }
            tags.flush().takeIf { it.isNotEmpty() }?.let { emit(it) }
            finished = true
            lastStats = GenerationStats(tokens, (System.nanoTime() - decodeStart) / 1e9)
        } finally {
            usedTokens = LlamaCppNative.nativeTokenCount(h)
            // Same contract as LiteRtGemmaEngine: a failed or cancelled turn starts over next time.
            if (!finished) this@LlamaCppEngine.sessionKey = null
        }
    }.flowOn(dispatcher)

    override fun takeGenerationStats(): GenerationStats? = lastStats.also { lastStats = null }

    override fun remainingTokens(sessionKey: String): Int? {
        if (handle == 0L || this.sessionKey != sessionKey) return null
        return contextSize - usedTokens
    }

    override fun resetSession() {
        // The next generate() starts a new chat; the native side still reuses any matching KV prefix.
        sessionKey = null
    }

    override fun close() {
        val h = handle
        handle = 0L
        sessionKey = null
        // Queue the free behind any in-flight native call, and wait briefly so memory is released
        // before the next model loads.
        runCatching {
            executor.submit { if (h != 0L) LlamaCppNative.nativeFree(h) }.get(10, TimeUnit.SECONDS)
        }
        executor.shutdown()
    }
}

fun isGgufModel(name: String): Boolean = name.lowercase(java.util.Locale.US).endsWith(".gguf")
