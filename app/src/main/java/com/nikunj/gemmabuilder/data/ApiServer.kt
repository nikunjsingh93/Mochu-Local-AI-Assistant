package com.nikunj.gemmabuilder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.UUID

// Local API server: other devices on the same Wi-Fi use the phone's model through an
// OpenAI-compatible endpoint (GET /v1/models, POST /v1/chat/completions, with streaming).

const val API_SERVER_PORT = 8080

data class ApiServerState(val running: Boolean = false, val address: String? = null, val requests: Int = 0, val lastError: String? = null)

object ApiServerStatus {
    private val _state = MutableStateFlow(ApiServerState())
    val state: StateFlow<ApiServerState> = _state.asStateFlow()
    internal fun set(value: ApiServerState) { _state.value = value }
    internal fun update(change: (ApiServerState) -> ApiServerState) { _state.value = change(_state.value) }
}

/** The key clients send as "Authorization: Bearer <key>": 4 random digits at first, changeable in the app. */
fun apiServerKey(context: Context): String {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    // Keys from v1.8.0 were long random strings; those get a short one instead.
    prefs.getString("apiServerKey", null)?.takeIf { isValidApiServerKey(it) && !it.startsWith("mochu-") }?.let { return it }
    val key = (1000 + SecureRandom().nextInt(9000)).toString()
    prefs.edit().putString("apiServerKey", key).apply()
    return key
}

fun isValidApiServerKey(key: String): Boolean = key.length in 4..32 && key.all { it.isLetterOrDigit() }

fun saveApiServerKey(context: Context, key: String) {
    if (isValidApiServerKey(key)) context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("apiServerKey", key).apply()
}

/** The phone's address on the local network (Wi-Fi or hotspot), or null when offline. */
fun localNetworkAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }?.hostAddress
}.getOrNull()

/** Turns an OpenAI messages array into the system prompt and the one message Mochu's engines take. */
fun openAiMessagesToPrompt(messages: JSONArray): Pair<String, String> {
    val system = StringBuilder()
    val turns = mutableListOf<Pair<String, String>>()
    for (i in 0 until messages.length()) {
        val msg = messages.optJSONObject(i) ?: continue
        val role = msg.optString("role")
        val content = when (val c = msg.opt("content")) {
            is String -> c
            // Multi-part content: keep the text parts (images aren't supported over the API).
            is JSONArray -> (0 until c.length()).mapNotNull { j -> c.optJSONObject(j)?.takeIf { it.optString("type") == "text" }?.optString("text") }.joinToString("\n")
            else -> ""
        }
        if (role == "system" || role == "developer") {
            if (system.isNotEmpty()) system.append("\n\n")
            system.append(content)
        } else if (content.isNotBlank()) {
            turns += (if (role == "assistant") "Assistant" else "User") to content
        }
    }
    val systemPrompt = system.toString().ifBlank { "You are a helpful assistant." }
    val last = turns.lastOrNull()?.second.orEmpty()
    val message = if (turns.size <= 1) last else buildString {
        append("Conversation so far:\n")
        turns.dropLast(1).forEach { (who, text) -> append("$who: $text\n") }
        append("\nReply to the user's latest message:\n$last")
    }
    return systemPrompt to message
}

/** Removes <think>…</think> reasoning so API clients get the answer only. */
fun stripThinking(text: String): String = splitThinking(text).let { if (it.thinking.isNotEmpty() || it.stillThinking) it.answer else text }

class ApiServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = Mutex()
    private var server: ServerSocket? = null
    // A model the server loaded itself (when the app had none loaded); closed when the server stops.
    private var ownEngine: BuilderEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (server == null) startServer()
        return START_STICKY
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Local API server", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, ApiServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mochu)
            .setContentTitle("Mochu API server is on")
            .setContentText("http://${localNetworkAddress() ?: "phone"}:$API_SERVER_PORT/v1")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startServer() {
        val socket = runCatching { ServerSocket(API_SERVER_PORT) }.getOrElse {
            ApiServerStatus.set(ApiServerState(lastError = "Port $API_SERVER_PORT is in use."))
            stopSelf()
            return
        }
        server = socket
        ApiServerStatus.set(ApiServerState(running = true, address = localNetworkAddress()?.let { "http://$it:$API_SERVER_PORT/v1" }))
        scope.launch {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                scope.launch { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
    }

    override fun onDestroy() {
        runCatching { server?.close() }
        server = null
        scope.cancel()
        ownEngine?.let { engine -> runCatching { engine.close() } }
        ownEngine = null
        ApiServerStatus.set(ApiServerState())
        super.onDestroy()
    }

    private fun handle(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val requestLine = readLine(input) ?: return
        val (method, rawPath) = requestLine.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
        val path = rawPath.substringBefore('?')
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        if (method == "OPTIONS") return respond(out, 204, "")
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) String(readExactly(input, length.coerceAtMost(4_000_000)), Charsets.UTF_8) else ""

        if (path == "/" || path == "/v1") {
            return respond(out, 200, JSONObject().put("name", "Mochu AI local API").put("endpoints", JSONArray(listOf("/v1/models", "/v1/chat/completions"))).toString())
        }
        val key = apiServerKey(this)
        if (headers["authorization"]?.removePrefix("Bearer ")?.trim() != key) {
            return respond(out, 401, error("Missing or wrong API key. Send the key from Mochu's Settings as: Authorization: Bearer <key>", "invalid_api_key"))
        }
        ApiServerStatus.update { it.copy(requests = it.requests + 1) }
        when {
            method == "GET" && path == "/v1/models" -> {
                val model = JSONObject().put("id", modelId()).put("object", "model").put("created", System.currentTimeMillis() / 1000).put("owned_by", "mochu")
                respond(out, 200, JSONObject().put("object", "list").put("data", JSONArray().put(model)).toString())
            }
            method == "POST" && path == "/v1/chat/completions" -> chatCompletions(out, body)
            else -> respond(out, 404, error("Unknown endpoint $method $path", "not_found"))
        }
    }

    private fun modelId(): String = savedModelName(this).substringBeforeLast('.')

    private fun chatCompletions(out: OutputStream, body: String) {
        val request = runCatching { JSONObject(body) }.getOrNull()
            ?: return respond(out, 400, error("The request body isn't valid JSON.", "invalid_request_error"))
        val messages = request.optJSONArray("messages")
            ?: return respond(out, 400, error("\"messages\" is required.", "invalid_request_error"))
        val stream = request.optBoolean("stream", false)
        val (systemPrompt, message) = openAiMessagesToPrompt(messages)
        if (message.isBlank()) return respond(out, 400, error("No user message to answer.", "invalid_request_error"))
        if (EngineHolder.foregroundBusy || EngineHolder.backgroundRunning || generation.isLocked) {
            return respond(out, 503, error("The model is busy with another request. Try again in a moment.", "server_busy"))
        }
        runBlocking {
            generation.withLock {
                EngineHolder.backgroundRunning = true
                try {
                    val engine = EngineHolder.loaded ?: ownEngine ?: loadModelForTask(this@ApiServerService)?.also { ownEngine = it }
                    if (engine == null) {
                        respond(out, 503, error("No model is downloaded in Mochu yet.", "model_not_loaded"))
                        return@withLock
                    }
                    val id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)
                    val created = System.currentTimeMillis() / 1000
                    val sessionKey = "api:$id"
                    if (stream) {
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n${CORS}\r\n".toByteArray())
                        out.flush()
                        val raw = StringBuilder()
                        var sent = 0
                        fun chunk(delta: JSONObject, finish: String?) = JSONObject()
                            .put("id", id).put("object", "chat.completion.chunk").put("created", created).put("model", modelId())
                            .put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", delta).put("finish_reason", finish ?: JSONObject.NULL)))
                        out.write("data: ${chunk(JSONObject().put("role", "assistant").put("content", ""), null)}\n\n".toByteArray())
                        engine.generate(sessionKey, message, systemPrompt).collect { piece ->
                            raw.append(piece)
                            // Send only the answer: hold back reasoning until it closes.
                            val split = splitThinking(raw.toString())
                            val answer = if (split.stillThinking) "" else split.answer
                            if (answer.length > sent) {
                                out.write("data: ${chunk(JSONObject().put("content", answer.substring(sent)), null)}\n\n".toByteArray())
                                out.flush()
                                sent = answer.length
                            }
                        }
                        out.write("data: ${chunk(JSONObject(), "stop")}\n\ndata: [DONE]\n\n".toByteArray())
                        out.flush()
                    } else {
                        val raw = StringBuilder()
                        engine.generate(sessionKey, message, systemPrompt).collect { raw.append(it) }
                        val answer = stripThinking(raw.toString()).trim()
                        val response = JSONObject()
                            .put("id", id).put("object", "chat.completion").put("created", created).put("model", modelId())
                            .put("choices", JSONArray().put(JSONObject().put("index", 0)
                                .put("message", JSONObject().put("role", "assistant").put("content", answer)).put("finish_reason", "stop")))
                            .put("usage", JSONObject().put("prompt_tokens", (systemPrompt.length + message.length) / 4).put("completion_tokens", answer.length / 4)
                                .put("total_tokens", (systemPrompt.length + message.length + answer.length) / 4))
                        respond(out, 200, response.toString())
                    }
                } catch (e: SocketException) {
                    // The client went away mid-reply.
                } catch (e: Exception) {
                    ApiServerStatus.update { it.copy(lastError = e.message) }
                    runCatching { respond(out, 500, error(e.message ?: "Generation failed.", "server_error")) }
                } finally {
                    EngineHolder.backgroundRunning = false
                }
            }
        }
    }

    private fun error(message: String, code: String) =
        JSONObject().put("error", JSONObject().put("message", message).put("type", code).put("code", code)).toString()

    private fun respond(out: OutputStream, status: Int, body: String) {
        val reason = when (status) { 200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 503 -> "Service Unavailable"; else -> "Error" }
        val bytes = body.toByteArray()
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n${CORS}\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (line.isEmpty()) null else line.toString()
            if (b == '\n'.code) return line.toString().trimEnd('\r')
            line.append(b.toChar())
            if (line.length > 16_384) return null
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n < 0) break
            read += n
        }
        return buf.copyOf(read)
    }

    companion object {
        private const val CHANNEL = "api_server"
        private const val NOTIFICATION_ID = 7310
        private const val ACTION_STOP = "com.nikunj.mochu.STOP_API_SERVER"
        private const val CORS = "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Authorization, Content-Type\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\n"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ApiServerService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ApiServerService::class.java).setAction(ACTION_STOP))
        }
    }
}
