package com.nikunj.gemmabuilder

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

// A model on the user's own server (Ollama, LM Studio, llama.cpp server, vLLM, another Mochu...),
// reached through its OpenAI-compatible API on the local network.

data class RemoteModelConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** Context window the user typed in; 0 asks the server. */
    val contextTokens: Int = 0
)

private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

fun savedRemoteModelConfig(context: Context): RemoteModelConfig? {
    val p = prefs(context)
    val url = p.getString("remoteBaseUrl", null) ?: return null
    val model = p.getString("remoteModel", null) ?: return null
    return RemoteModelConfig(url, p.getString("remoteApiKey", "").orEmpty(), model, p.getInt("remoteContextTokens", 0))
}

fun saveRemoteModelConfig(context: Context, config: RemoteModelConfig) {
    prefs(context).edit().putString("remoteBaseUrl", config.baseUrl).putString("remoteApiKey", config.apiKey).putString("remoteModel", config.model)
        .putInt("remoteContextTokens", config.contextTokens).apply()
}

/** True when Chat and Code use the remote model instead of one on the phone. */
fun remoteModelActive(context: Context): Boolean = prefs(context).getBoolean("remoteActive", false) && savedRemoteModelConfig(context) != null

/** Server addresses outside the local network the user has allowed. */
fun allowedRemoteHosts(context: Context): Set<String> = prefs(context).getStringSet("remoteAllowedHosts", emptySet()).orEmpty()

fun allowRemoteHost(context: Context, baseUrl: String) {
    prefs(context).edit().putStringSet("remoteAllowedHosts", allowedRemoteHosts(context) + baseUrl).apply()
}

/** Opt-in: when the server can't be reached, the phone's own model answers instead. Off by default. */
fun remoteFallbackEnabled(context: Context): Boolean = prefs(context).getBoolean("remoteFallback", false)

fun setRemoteFallbackEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean("remoteFallback", enabled).apply()
}

/**
 * Whether the server answers within a couple of seconds. Any reply counts (even a refused key), except
 * 502/503/504: a proxy like nginx answers those when the model server behind it is down.
 */
fun remoteReachable(config: RemoteModelConfig): Boolean = runCatching {
    val c = (URL("${config.baseUrl}/models").openConnection() as HttpURLConnection).apply {
        connectTimeout = 2_000
        readTimeout = 3_000
        if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
    }
    try { c.responseCode.let { it > 0 && it !in 502..504 } } finally { c.disconnect() }
}.getOrDefault(false)

fun setRemoteModelActive(context: Context, active: Boolean) {
    prefs(context).edit().putBoolean("remoteActive", active).apply()
}

/** "http://192.168.1.20:11434" or "...:1234/v1" → the API root ending in /v1. */
fun normalizeBaseUrl(input: String): String {
    var url = input.trim().trimEnd('/')
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        // Addresses on your network usually serve plain http; domains like api.openai.com use https.
        val host = url.substringBefore('/').substringBefore(':').lowercase()
        val local = host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || Regex("""\d+\.\d+\.\d+\.\d+""").matches(host)
        url = (if (local) "http://" else "https://") + url
    }
    if (!url.substringAfter("://").contains("/")) url += "/v1"
    return url
}

/**
 * Only servers on the user's own network: localhost, private ranges (192.168, 10, 172.16-31),
 * Tailscale (100.64/10, *.ts.net) and *.local names. Keeps "your data stays on your devices" true.
 */
fun isLocalNetworkHost(baseUrl: String): Boolean {
    val host = runCatching { URI(baseUrl).host }.getOrNull()?.lowercase() ?: return false
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".ts.net") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
    val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
    val b = address.address.map { it.toInt() and 0xff }
    return address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
        (b.size == 4 && b[0] == 100 && b[1] in 64..127)
}

/** Model ids the server offers (GET /models). */
suspend fun listRemoteModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
    val connection = (URL("$baseUrl/models").openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 10_000
        if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
    }
    try {
        val code = connection.responseCode
        if (code == 401 || code == 403) error("The server refused the API key.")
        if (code !in 200..299) error("The server answered $code. Check the address (it should end in /v1).")
        val body = connection.inputStream.bufferedReader().readText()
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
    } finally {
        connection.disconnect()
    }
}

class RemoteEngine(val config: RemoteModelConfig) : BuilderEngine {
    @Volatile private var lastStats: GenerationStats? = null

    /** Tokens the last request used (prompt + reply), as the server counted them; null if it didn't say. */
    @Volatile var lastContextTokens: Int? = null
        private set

    /** False once the server refused stream_options, so it isn't sent again. */
    @Volatile private var askForUsage = true

    // Most servers accept OpenAI image parts; ones that don't answer with an error the user sees.
    override val supportsImages: Boolean get() = true

    override fun generate(sessionKey: String, message: String, systemPrompt: String, thinking: Boolean, images: List<String>, thinkingBudget: Int): Flow<String> = flow {
        val userContent: Any = if (images.isEmpty()) message else JSONArray().apply {
            images.forEach { path ->
                val data = Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$data")))
            }
            put(JSONObject().put("type", "text").put("text", message))
        }
        fun body(withUsage: Boolean) = JSONObject()
            .put("model", config.model)
            .put("stream", true)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userContent)))
            // The final chunk then carries usage: the real size of what the server holds for this chat.
            .apply { if (withUsage) put("stream_options", JSONObject().put("include_usage", true)) }
        fun open() = (URL("${config.baseUrl}/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            // Long thinking on a slow server can pause the stream for a while.
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        val started = System.nanoTime()
        var pieces = 0
        var inThought = false
        lastContextTokens = null
        var connection = open()
        try {
            connection.outputStream.use { it.write(body(askForUsage).toString().toByteArray()) }
            // A strict server may reject stream_options: ask once more without it, and stop asking.
            if (askForUsage && connection.responseCode == 400) {
                connection.disconnect()
                askForUsage = false
                connection = open()
                connection.outputStream.use { it.write(body(false).toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                val message = runCatching { JSONObject(detail).optJSONObject("error")?.optString("message") }.getOrNull()
                error("Your server answered $code${message?.let { ": $it" } ?: ""}")
            }
            connection.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    currentCoroutineContext().ensureActive()
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    chunk.optJSONObject("usage")?.let { usage ->
                        val total = usage.optInt("total_tokens", 0).takeIf { it > 0 }
                            ?: (usage.optInt("prompt_tokens", 0) + usage.optInt("completion_tokens", 0)).takeIf { it > 0 }
                        if (total != null) lastContextTokens = total
                    }
                    val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                    // Reasoning comes as reasoning_content (llama.cpp, vLLM, DeepSeek) or reasoning (Ollama, LM Studio).
                    val reasoning = delta.optString("reasoning_content").ifEmpty { delta.optString("reasoning") }.takeUnless { it == "null" }.orEmpty()
                    val content = delta.optString("content").takeUnless { it == "null" }.orEmpty()
                    if (reasoning.isNotEmpty()) {
                        if (!inThought) emit("<think>")
                        inThought = true
                        emit(reasoning)
                        pieces++
                    }
                    if (content.isNotEmpty()) {
                        if (inThought) emit("</think>")
                        inThought = false
                        emit(content)
                        pieces++
                    }
                }
            }
            if (inThought) emit("</think>")
            lastStats = GenerationStats(pieces, (System.nanoTime() - started) / 1e9)
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    override fun takeGenerationStats(): GenerationStats? = lastStats.also { lastStats = null }
}

/** Whether a server's model name is [model]: exact, or the same apart from an "org/" prefix or case. */
fun sameRemoteModel(a: String?, model: String): Boolean {
    if (a.isNullOrBlank()) return false
    fun bare(s: String) = s.substringAfterLast('/').lowercase()
    return a == model || a.equals(model, ignoreCase = true) || bare(a) == bare(model)
}

/**
 * The context window for [config]'s model: the size the user typed, or what the server says:
 * Unsloth Studio (/api/inference/status), vLLM and OpenRouter (/models fields), llama.cpp (/props
 * or /models meta), LM Studio (/api/v0/models), Ollama (/api/show). Null when unknown.
 */
fun detectRemoteContext(config: RemoteModelConfig): Int? {
    if (config.contextTokens > 0) return config.contextTokens
    val root = config.baseUrl.removeSuffix("/v1")
    fun get(url: String, body: String? = null): JSONObject? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 4_000
            if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            if (body != null) { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) null else JSONObject(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }.getOrNull()
    // Unsloth Studio: the loaded model's runtime context (it loads a model on its first request).
    get("$root/api/inference/status")?.let { s ->
        val loaded = s.optJSONArray("loaded")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val isOurs = sameRemoteModel(s.optString("model_identifier"), config.model) ||
            sameRemoteModel(s.optString("active_model"), config.model) || loaded.any { sameRemoteModel(it, config.model) }
        if (isOurs) s.optInt("context_length", 0).takeIf { it > 0 }?.let { return it }
    }
    get("${config.baseUrl}/models")?.optJSONArray("data")?.let { data ->
        (0 until data.length()).mapNotNull { data.optJSONObject(it) }.firstOrNull { sameRemoteModel(it.optString("id"), config.model) }?.let { m ->
            listOf("max_model_len", "context_length", "context_window", "loaded_context_length").forEach { key -> m.optInt(key, 0).takeIf { it > 0 }?.let { return it } }
            // llama.cpp server: the running window (n_ctx) when listed, else the model's own (n_ctx_train).
            m.optJSONObject("meta")?.let { meta -> (meta.optInt("n_ctx", 0).takeIf { it > 0 } ?: meta.optInt("n_ctx_train", 0).takeIf { it > 0 })?.let { return it } }
        }
    }
    get("$root/props")?.let { p ->
        (p.optJSONObject("default_generation_settings")?.optInt("n_ctx", 0) ?: 0).takeIf { it > 0 }?.let { return it }
        p.optInt("n_ctx", 0).takeIf { it > 0 }?.let { return it }
    }
    get("$root/api/v0/models/${config.model}")?.let { m ->
        (m.optInt("loaded_context_length", 0).takeIf { it > 0 } ?: m.optInt("max_context_length", 0).takeIf { it > 0 })?.let { return it }
    }
    get("$root/api/show", JSONObject().put("model", config.model).toString())?.let { s ->
        // Ollama runs with num_ctx when set, otherwise its default (4096), not the model's maximum.
        Regex("""num_ctx\s+(\d+)""").find(s.optString("parameters"))?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 4096
    }
    return null
}
