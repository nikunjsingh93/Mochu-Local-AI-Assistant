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

data class RemoteModelConfig(val baseUrl: String, val apiKey: String, val model: String)

private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

fun savedRemoteModelConfig(context: Context): RemoteModelConfig? {
    val p = prefs(context)
    val url = p.getString("remoteBaseUrl", null) ?: return null
    val model = p.getString("remoteModel", null) ?: return null
    return RemoteModelConfig(url, p.getString("remoteApiKey", "").orEmpty(), model)
}

fun saveRemoteModelConfig(context: Context, config: RemoteModelConfig) {
    prefs(context).edit().putString("remoteBaseUrl", config.baseUrl).putString("remoteApiKey", config.apiKey).putString("remoteModel", config.model).apply()
}

/** True when Chat and Code use the remote model instead of one on the phone. */
fun remoteModelActive(context: Context): Boolean = prefs(context).getBoolean("remoteActive", false) && savedRemoteModelConfig(context) != null

/** Server addresses outside the local network the user has allowed. */
fun allowedRemoteHosts(context: Context): Set<String> = prefs(context).getStringSet("remoteAllowedHosts", emptySet()).orEmpty()

fun allowRemoteHost(context: Context, baseUrl: String) {
    prefs(context).edit().putStringSet("remoteAllowedHosts", allowedRemoteHosts(context) + baseUrl).apply()
}

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

class RemoteEngine(private val config: RemoteModelConfig) : BuilderEngine {
    @Volatile private var lastStats: GenerationStats? = null

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
        val body = JSONObject()
            .put("model", config.model)
            .put("stream", true)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userContent)))
        val connection = (URL("${config.baseUrl}/chat/completions").openConnection() as HttpURLConnection).apply {
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
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
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
                    val delta = runCatching { JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") }.getOrNull() ?: continue
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

/**
 * The server's context window for [config]'s model, when it says: llama.cpp (/props), LM Studio
 * (/api/v0/models), vLLM and OpenRouter (/models fields), Ollama (/api/show). Null when unknown.
 */
fun detectRemoteContext(config: RemoteModelConfig): Int? {
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
    get("${config.baseUrl}/models")?.optJSONArray("data")?.let { data ->
        (0 until data.length()).mapNotNull { data.optJSONObject(it) }.firstOrNull { it.optString("id") == config.model }?.let { m ->
            listOf("max_model_len", "context_length", "context_window", "loaded_context_length").forEach { key -> m.optInt(key, 0).takeIf { it > 0 }?.let { return it } }
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
