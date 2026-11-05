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
    /** [kvType]: 0 F16, 1 Q8_0. */
    external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int, kvType: Int): Long
    /** {bytes per token, fixed bytes} of the KV cache llama.cpp allocated at load. */
    external fun nativeKvBytes(handle: Long): LongArray
    /** Adds [text] to the reply as if the model wrote it (used to end thinking at its budget). */
    external fun nativeInject(handle: Long, text: String): Boolean
    external fun nativeContextSize(handle: Long): Int
    external fun nativeDescribe(handle: Long): String
    external fun nativeStartChat(handle: Long, systemPrompt: String)
    external fun nativeSendUser(handle: Long, userText: String, maxOutput: Int, thinking: Boolean, images: Array<String>): Int
    external fun nativeLoadVision(handle: Long, mmprojPath: String, nThreads: Int): Boolean
    external fun nativeNextToken(handle: Long): ByteArray?
    external fun nativeTokenCount(handle: Long): Int
    external fun nativeFree(handle: Long)

    // Combining devices (see DeviceCluster.kt). An empty list turns it off for the next load.
    external fun nativeSetCluster(endpoints: Array<String>, weights: FloatArray, localWeight: Float)
    /** "12 of 24 layers here, 12 on other devices" after a load that used them; else "". */
    external fun nativeClusterNote(): String
    /** Worker: serves this device's CPU at [endpoint] until the process ends; returns why it stopped. */
    external fun nativeServe(endpoint: String, cacheDir: String, nThreads: Int): String
    /** A device holding part of the model stopped answering (the model must be reloaded). */
    external fun nativeClusterLost(): Boolean
    external fun nativeClusterClearLost()
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

    /** Other devices holding part of the model; 0 when it runs here alone. */
    var sharedWith = 0
        private set

    /** A device holding part of the model went away; this engine can't be used again. */
    @Volatile var lostDevice = false
        private set

    private fun deviceGone(): Nothing {
        lostDevice = true
        DeviceCluster.onLost()
        error("A device sharing this model stopped responding. Send your message again to continue with the devices that are still sharing.")
    }

    /** e.g. "qwen3 4B Q4_K - Medium · android_armv8.6_1". */
    var description: String = ""
        private set

    suspend fun load() = withContext(dispatcher) {
        val variant = LlamaCppNative.ensureInitialized(context.applicationInfo.nativeLibraryDir)
        require(variant.isNotEmpty()) { "No llama.cpp CPU backend could be loaded on this device." }
        // Paired devices that answer lend their memory; none means this device alone.
        val devices = DeviceCluster.prepareForLoad(context)
        // Leave a couple of cores for the UI; big.LITTLE phones gain little past four threads.
        // With other devices every step waits for the slowest thread, so only fast cores are used.
        val threads = if (devices > 0) fastCoreCount() else (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)
        val kvType = savedKvCacheType(context)
        val loaded = LlamaCppNative.nativeLoad(modelPath, maxNumTokens, threads, kvType.ordinal)
        require(loaded != 0L) {
            if (devices > 0) "llama.cpp could not load this model across your devices. Check they're still sharing, or turn off Combine devices."
            else "llama.cpp could not load this .gguf model (unsupported architecture or not enough memory)."
        }
        sharedWith = devices
        handle = loaded
        contextSize = LlamaCppNative.nativeContextSize(loaded)
        // Remember the real context memory so the Models page can estimate other window sizes.
        LlamaCppNative.nativeKvBytes(loaded).takeIf { it.size == 2 && it[0] > 0 }?.let { (perToken, fixed) ->
            val perTokenF16 = (perToken / kvType.ratio).toLong()
            saveMeasuredKv(context, java.io.File(modelPath).name, perTokenF16, fixed)
        }
        // A failed projector only costs photos; the model still works for text.
        if (visionPath != null && java.io.File(visionPath).exists()) {
            supportsImages = runCatching { LlamaCppNative.nativeLoadVision(loaded, visionPath, threads) }.getOrDefault(false)
        }
        val cpuLabel = variant.removePrefix("libggml-cpu-").removeSuffix(".so")
        description = "${LlamaCppNative.nativeDescribe(loaded)} · $cpuLabel" +
            if (devices > 0) " · ${devices + 1} devices (${LlamaCppNative.nativeClusterNote()})" else ""
    }

    // Reasoning models here (Qwen3, Bonsai) write <think>...</think> into the text themselves.
    override fun generate(sessionKey: String, message: String, systemPrompt: String, thinking: Boolean, images: List<String>, thinkingBudget: Int): Flow<String> = flow {
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
        // Every device holding part of the model must still be there, or llama.cpp would wait on it.
        if (sharedWith > 0 && (lostDevice || !DeviceCluster.activeStillThere())) deviceGone()
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
            // Thinking past its budget is closed off so the model moves on to the answer.
            var inThought = false
            var thoughtTokens = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val bytes = LlamaCppNative.nativeNextToken(h)
                if (sharedWith > 0 && LlamaCppNative.nativeClusterLost()) deviceGone()
                if (bytes == null) break
                tokens++
                val text = if (bytes.isNotEmpty()) tags.feed(String(bytes, Charsets.UTF_8)) else ""
                if (text.isNotEmpty()) emit(text)
                if ("<think>" in text) inThought = true
                if ("</think>" in text) inThought = false
                if (inThought && thinkingBudget > 0 && ++thoughtTokens > thinkingBudget) {
                    val close = "\n</think>\n\n"
                    if (LlamaCppNative.nativeInject(h, close)) emit(close)
                    inThought = false
                    thoughtTokens = Int.MIN_VALUE
                }
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
        if (sharedWith > 0) DeviceCluster.releaseWifi()
        // Queue the free behind any in-flight native call, and wait briefly so memory is released
        // before the next model loads.
        runCatching {
            executor.submit { if (h != 0L) LlamaCppNative.nativeFree(h) }.get(10, TimeUnit.SECONDS)
        }
        executor.shutdown()
    }
}

fun isGgufModel(name: String): Boolean = name.lowercase(java.util.Locale.US).endsWith(".gguf")
