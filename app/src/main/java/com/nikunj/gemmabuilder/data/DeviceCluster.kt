package com.nikunj.gemmabuilder

import kotlin.math.roundToInt
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

// Combining devices: other phones and tablets running Mochu lend their memory, so a GGUF model too
// big for one device can run across several (llama.cpp's RPC backend, see llama_bridge.cpp).
//
// llama.cpp's RPC has no password, so it never listens on the network directly. On a worker it
// listens only on the device itself, behind a "gate" that lets a connection through only after the
// 4-digit pairing code. On the main device llama.cpp talks to a local relay that sends the code first.

private const val TAG = "DeviceCluster"
private const val GATE_PORT = 50052
private const val RPC_PORT = 50053
private const val SERVICE_TYPE = "_mochu-cluster._tcp."
private const val HELLO = "MOCHU1"

/** A worker the main device has paired with. */
data class ClusterDevice(val id: String, val name: String, val host: String, val port: Int, val code: String)

/** A worker found on the network (not necessarily paired). */
data class NearbyDevice(val id: String, val name: String, val host: String, val port: Int, val freeMb: Long)

/** Copies [input] to [output] until either side closes. */
private fun pipe(input: InputStream, output: OutputStream, counter: java.util.concurrent.atomic.AtomicLong? = null, onEnd: () -> Unit) {
    Thread({
        val buffer = ByteArray(64 * 1024)
        runCatching {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                output.flush()
                counter?.addAndGet(n.toLong())
            }
        }
        onEnd()
    }, "cluster-pipe").apply { isDaemon = true }.start()
}

/** Joins two sockets both ways; closes both when one side ends. */
private fun join(a: Socket, b: Socket) = joinThen(a, b) {}

/** [join], then [onEnd] once, when the pair has closed. [sent] counts bytes going from [a] to [b]. */
private fun joinThen(a: Socket, b: Socket, sent: java.util.concurrent.atomic.AtomicLong? = null, onEnd: () -> Unit) {
    val ended = java.util.concurrent.atomic.AtomicBoolean(false)
    val close = {
        runCatching { a.close() }; runCatching { b.close() }
        if (ended.compareAndSet(false, true)) onEnd()
        Unit
    }
    pipe(a.getInputStream(), b.getOutputStream(), sent, close)
    pipe(b.getInputStream(), a.getOutputStream(), null, close)
}

private fun readLine(input: InputStream, max: Int = 128): String {
    val sb = StringBuilder()
    while (sb.length < max) {
        val c = input.read()
        if (c < 0 || c == '\n'.code) break
        sb.append(c.toChar())
    }
    return sb.toString().trim()
}

/** Fast cores this process may use: ggml threads wait for the slowest every step, so only those. */
fun fastCoreCount(): Int = runCatching {
    val allowed = File("/proc/self/status").readLines().firstOrNull { it.startsWith("Cpus_allowed_list:") }
        ?.substringAfter(':')?.trim()?.split(',')
        ?.flatMap { range -> range.split('-').let { (it.first().trim().toInt()..it.last().trim().toInt()).toList() } }
        ?: (0 until Runtime.getRuntime().availableProcessors()).toList()
    val freq = allowed.associateWith { cpu ->
        File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull() ?: 0L
    }
    val top = freq.values.maxOrNull() ?: 0L
    (if (top > 0) freq.count { it.value >= top * 0.8 } else allowed.size / 2).coerceIn(2, 6)
}.getOrDefault(4)

@Volatile private var measuredBandwidth = 0.0

/** This device's memory speed in GB/s, measured once (about 0.3 s) with the fast cores. */
fun memoryBandwidth(context: Context): Double {
    if (measuredBandwidth <= 0.0) {
        LlamaCppNative.ensureInitialized(context.applicationInfo.nativeLibraryDir)
        measuredBandwidth = runCatching { LlamaCppNative.nativeMemBandwidth(fastCoreCount()) }.getOrDefault(0.0)
    }
    return measuredBandwidth
}

/**
 * How the model is split, given each device's [weights] (speed, memory, or equal) and [capacityMb]
 * (what it can safely hold): shares follow the weights, except that no device is given more than
 * it can hold; the rest goes to the others. [modelMb] 0 skips the check.
 */
fun fitSharesToMemory(weights: List<Double>, capacityMb: List<Double>, modelMb: Double): List<Double> {
    if (modelMb <= 0 || weights.isEmpty()) return weights
    val share = DoubleArray(weights.size)
    val fixed = BooleanArray(weights.size)
    var remaining = modelMb
    repeat(weights.size) {
        val open = weights.indices.filter { !fixed[it] }
        val weightSum = open.sumOf { weights[it].coerceAtLeast(0.0) }.takeIf { it > 0 } ?: return@repeat
        var anyCapped = false
        for (i in open) {
            val want = remaining * weights[i].coerceAtLeast(0.0) / weightSum
            if (want > capacityMb[i]) { share[i] = capacityMb[i]; fixed[i] = true; anyCapped = true }
        }
        if (!anyCapped) {
            open.forEach { share[it] = remaining * weights[it].coerceAtLeast(0.0) / weightSum }
            return share.toList()
        }
        remaining = modelMb - share.indices.filter { fixed[it] }.sumOf { share[it] }
    }
    // Everyone is full: the model doesn't fit; split by room so each holds what it can.
    return if (share.sum() > 0) share.toList() else capacityMb
}

/** The device's total RAM in MB (what Android reports, a little under the advertised size). */
fun totalMemoryMb(context: Context): Long {
    val info = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
    return info.totalMem / (1024 * 1024)
}

fun freeMemoryMb(context: Context): Long {
    val info = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
    return info.availMem / (1024 * 1024)
}

// ---------------------------------------------------------------------------------------------
// Worker: "Share this device"
// ---------------------------------------------------------------------------------------------

/**
 * Lends this device's memory and CPU to another Mochu. Runs in its own process (":cluster") because
 * llama.cpp's server loop can't be stopped: Stop ends the process.
 */
class ClusterWorkerService : Service() {
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    @Volatile private var peer: String? = null
    @Volatile private var peerName: String? = null
    /** Open connections from the main device (llama.cpp may open more than one). */
    private val peerConnections = java.util.concurrent.atomic.AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        running = true
        val code = pairingCode(this)
        startForeground(NOTIFICATION_ID, notification("Ready · pairing code $code"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        holdLocks()
        writeStatus("starting", code)
        Thread({
            val variant = LlamaCppNative.ensureInitialized(applicationInfo.nativeLibraryDir)
            if (variant.isEmpty()) {
                writeStatus("error", code, error = "No llama.cpp CPU backend on this device.")
                stopSharing()
                return@Thread
            }
            memoryBandwidth(this)
            startGate(code)
            advertise()
            writeStatus("running", code)
            val cache = File(filesDir, "cluster_cache").apply { mkdirs() }
            // Only on this device: the gate is the only way in from the network.
            val error = LlamaCppNative.nativeServe("127.0.0.1:$RPC_PORT", cache.absolutePath, fastCoreCount())
            Log.e(TAG, "Sharing stopped: $error")
            writeStatus("error", code, error = error)
            stopSharing()
        }, "cluster-worker").start()
        return START_NOT_STICKY
    }

    /** Lets through connections that start with the pairing code; answers memory questions. */
    private fun startGate(code: String) {
        val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(GATE_PORT)) }
        Thread({
            while (running) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                Thread({
                    runCatching {
                        client.tcpNoDelay = true
                        client.soTimeout = 5_000
                        val parts = readLine(client.getInputStream()).split(' ')
                        val out = client.getOutputStream()
                        if (parts.size < 2 || parts[0] != HELLO || parts[1] != code) {
                            out.write("NO\n".toByteArray()); client.close(); return@runCatching
                        }
                        if (parts.getOrNull(2) == "INFO") {
                            out.write("OK ${freeMemoryMb(this)} ${Build.MODEL.replace(' ', '_')} ${totalMemoryMb(this)} ${String.format(java.util.Locale.US, "%.1f", memoryBandwidth(this))}\n".toByteArray()); client.close(); return@runCatching
                        }
                        // One main device at a time: another one is turned away while this one is connected.
                        val from = client.inetAddress.hostAddress
                        if (peer != null && peer != from && peerConnections.get() > 0) {
                            out.write("BUSY\n".toByteArray()); client.close(); return@runCatching
                        }
                        out.write("OK\n".toByteArray())
                        client.soTimeout = 0
                        val rpc = Socket("127.0.0.1", RPC_PORT).apply { tcpNoDelay = true }
                        peer = from
                        parts.getOrNull(3)?.takeIf { parts.getOrNull(2) == "CONNECT" }?.let { peerName = it.replace('_', ' ') }
                        val model = parts.getOrNull(4)?.takeIf { parts.getOrNull(2) == "CONNECT" } ?: "Unknown model"
                        if (peerConnections.get() == 0) watchCache(model)
                        peerConnections.incrementAndGet()
                        writeStatus("running", code)
                        updateNotification("Connected to ${peerName ?: peer}")
                        joinThen(client, rpc) {
                            // The main device let go (model unloaded, or it left): ready for any device again.
                            if (peerConnections.decrementAndGet() == 0) {
                                peer = null
                                peerName = null
                                writeStatus("running", code)
                                updateNotification("Ready · pairing code $code")
                            }
                        }
                    }.onFailure { runCatching { client.close() } }
                }, "cluster-gate").apply { isDaemon = true }.start()
            }
        }, "cluster-accept").apply { isDaemon = true }.start()
    }

    /** While a main device is connected, labels new cache files with the model it's loading. */
    private fun watchCache(model: String) {
        Thread({
            do {
                Thread.sleep(5_000)
                ClusterCache.label(this, model)
            } while (peerConnections.get() > 0 && running)
            ClusterCache.label(this, model)
        }, "cluster-cache").apply { isDaemon = true }.start()
    }

    private fun advertise() {
        val info = NsdServiceInfo().apply {
            serviceName = "Mochu ${Build.MODEL}"
            serviceType = SERVICE_TYPE
            port = GATE_PORT
            setAttribute("id", deviceId(this@ClusterWorkerService))
            setAttribute("ram", freeMemoryMb(this@ClusterWorkerService).toString())
            setAttribute("model", Build.MODEL)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.w(TAG, "Advertising failed ($code)") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        nsd = getSystemService(NsdManager::class.java)
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener); registration = listener }
    }

    private fun holdLocks() {
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mochu:sharing").apply { acquire() }
        // A dozing Wi-Fi radio adds tens of milliseconds to every token.
        @Suppress("DEPRECATION")
        wifiLock = applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Mochu:sharing").apply { acquire() }
    }

    private fun stopSharing() {
        running = false
        runCatching { registration?.let { nsd?.unregisterService(it) } }
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        if (readStatus(this).optString("state") != "error") writeStatus("stopped", pairingCode(this))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        // The native server can't be stopped any other way.
        Process.killProcess(Process.myPid())
    }

    private fun writeStatus(state: String, code: String, error: String? = null) {
        val json = JSONObject().put("state", state).put("code", code).put("freeMb", freeMemoryMb(this)).put("totalMb", totalMemoryMb(this))
        peer?.let { json.put("peer", it) }
        peerName?.let { json.put("peerName", it) }
        error?.let { json.put("error", it) }
        statusFile(this).writeText(json.toString())
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Sharing this device", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 31, Intent(this, ClusterWorkerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 32, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mochu)
            .setContentTitle("Sharing this device with Mochu")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text)) }
    }

    companion object {
        const val ACTION_STOP = "com.nikunj.mochu.STOP_SHARING"
        private const val CHANNEL = "cluster_worker"
        private const val NOTIFICATION_ID = 7330
        @Volatile private var running = false

        fun statusFile(context: Context) = File(context.filesDir, "cluster_worker_status.json")

        /** The worker's status, written by its own process: state, code, peer, freeMb, error. */
        fun readStatus(context: Context): JSONObject {
            val status = statusFile(context).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                ?: return JSONObject().put("state", "stopped")
            // The file outlives the process (an app update or a kill ends sharing): trust it only while it runs.
            val state = status.optString("state")
            if ((state == "running" || state == "starting") && !workerAlive(context)) return JSONObject().put("state", "stopped")
            return status
        }

        /** Whether the sharing process (":cluster") is running. */
        private fun workerAlive(context: Context): Boolean =
            context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty()
                .any { it.processName == context.packageName + ":cluster" }

        fun start(context: Context) {
            statusFile(context).delete()
            context.startForegroundService(Intent(context, ClusterWorkerService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ClusterWorkerService::class.java).setAction(ACTION_STOP))
        }

        /** This device's 4-digit pairing code; a new one each time sharing starts from scratch. */
        @Synchronized
        fun pairingCode(context: Context): String {
            val file = File(context.filesDir, "cluster_code")
            file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.length == 4 }?.let { return it }
            return String.format(java.util.Locale.US, "%04d", java.security.SecureRandom().nextInt(10_000)).also { file.writeText(it) }
        }

        fun newPairingCode(context: Context) { File(context.filesDir, "cluster_code").delete() }

        /** A lasting ID, so the main device finds this one again when its address changes. */
        @Synchronized
        fun deviceId(context: Context): String {
            val file = File(context.filesDir, "cluster_device_id")
            file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            return java.util.UUID.randomUUID().toString().also { file.writeText(it) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Main device: pairing, finding workers, and the relays llama.cpp connects through
// ---------------------------------------------------------------------------------------------

object DeviceCluster {
    private val _nearby = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearby: StateFlow<List<NearbyDevice>> = _nearby.asStateFlow()
    private var discovery: NsdManager.DiscoveryListener? = null
    private val relays = ConcurrentHashMap<String, ServerSocket>()
    private var wifiLock: WifiManager.WifiLock? = null
    /** The devices the loaded model uses. */
    @Volatile private var active: List<ClusterDevice> = emptyList()
        set(value) { field = value; _connected.value = value.map { it.id } }
    private val _connected = MutableStateFlow<List<String>>(emptyList())
    private val _loadingOn = MutableStateFlow<List<String>>(emptyList())
    /** Names of the other devices a model is loading onto right now (empty when not loading). */
    val loadingOn: StateFlow<List<String>> = _loadingOn.asStateFlow()

    /** How far each other device has got receiving its part of a model being loaded. */
    data class SendProgress(val name: String, val sentBytes: Long, val totalBytes: Long, val layers: Int)

    private val sentBytes = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    private val _sending = MutableStateFlow<List<SendProgress>>(emptyList())
    /** Updated every half second while a model loads across devices. */
    val sending: StateFlow<List<SendProgress>> = _sending.asStateFlow()
    @Volatile private var progressThread: Thread? = null

    /** Watches the relays' byte counts against each device's share until the load finishes. */
    private fun watchSending(plan: List<Triple<ClusterDevice, Long, Int>>) {
        plan.forEach { (d, _, _) -> sentBytes.getOrPut(d.id) { java.util.concurrent.atomic.AtomicLong() }.set(0) }
        progressThread = Thread({
            while (_loadingOn.value.isNotEmpty()) {
                _sending.value = plan.map { (d, total, layers) -> SendProgress(d.name, sentBytes[d.id]?.get() ?: 0L, total, layers) }
                runCatching { Thread.sleep(500) }
            }
            _sending.value = emptyList()
        }, "cluster-progress").apply { isDaemon = true; start() }
    }

    fun loadFinished() { _loadingOn.value = emptyList() }

    /** IDs of the devices holding part of the loaded model, for the Models page. */
    val connected: StateFlow<List<String>> = _connected.asStateFlow()

    /** The loaded model no longer uses other devices (unloaded, or switched to another model). */
    fun onUnloaded() {
        active = emptyList()
        releaseWifi()
    }
    /** The devices the model was using when one dropped out. */
    @Volatile private var lastLost: List<ClusterDevice> = emptyList()

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether GGUF models should use paired devices. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("clusterEnabled", false)

    fun setEnabled(context: Context, on: Boolean) { prefs(context).edit().putBoolean("clusterEnabled", on).apply() }

    fun paired(context: Context): List<ClusterDevice> = runCatching {
        val array = JSONArray(prefs(context).getString("clusterDevices", "[]"))
        (0 until array.length()).map { i ->
            array.getJSONObject(i).let { ClusterDevice(it.getString("id"), it.getString("name"), it.getString("host"), it.getInt("port"), it.getString("code")) }
        }
    }.getOrDefault(emptyList())

    private fun savePaired(context: Context, devices: List<ClusterDevice>) {
        val array = JSONArray()
        devices.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("port", it.port).put("code", it.code)) }
        prefs(context).edit().putString("clusterDevices", array.toString()).apply()
    }

    fun forget(context: Context, id: String) = savePaired(context, paired(context).filterNot { it.id == id })

    /**
     * Asks a worker for its free memory with [code]: the free MB, or null if it's unreachable,
     * or -1 if it refused the code. Runs on a background thread.
     */
    fun ask(host: String, port: Int, code: String): Long? = askMemory(host, port, code)?.first

    /**
     * Free and total memory (MB) of a worker: null if it's unreachable; free is -1 if it refused
     * the code; total is 0 for a worker too old to report it. Runs on a background thread.
     */
    fun askMemory(host: String, port: Int, code: String): Pair<Long, Long>? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 3_000)
            s.soTimeout = 4_000
            s.tcpNoDelay = true
            s.getOutputStream().write("$HELLO $code INFO\n".toByteArray())
            val reply = readLine(s.getInputStream())
            if (!reply.startsWith("OK")) return@runCatching -1L to 0L
            val parts = reply.split(' ')
            (parts.getOrNull(1)?.toLongOrNull() ?: 0L) to (parts.getOrNull(3)?.toLongOrNull() ?: 0L)
        }
    }.getOrNull()

/** What a worker reports: free and total memory (MB) and memory speed (GB/s, 0 when unknown). */
    data class WorkerInfo(val freeMb: Long, val totalMb: Long, val bandwidth: Double)

    /** A worker's [WorkerInfo]; null if unreachable or it refused the code. Background thread. */
    fun askInfo(host: String, port: Int, code: String): WorkerInfo? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 3_000)
            s.soTimeout = 4_000
            s.tcpNoDelay = true
            s.getOutputStream().write("$HELLO $code INFO\n".toByteArray())
            val reply = readLine(s.getInputStream())
            if (!reply.startsWith("OK")) return@runCatching null
            val p = reply.split(' ')
            WorkerInfo(p.getOrNull(1)?.toLongOrNull() ?: 0L, p.getOrNull(3)?.toLongOrNull() ?: 0L, p.getOrNull(4)?.toDoubleOrNull() ?: 0.0)
        }
    }.getOrNull()

    /** How layers are split: "speed" (default), "memory", "even" or "manual". */
    fun balanceMode(context: Context): String = prefs(context).getString("clusterBalance", "speed") ?: "speed"

    fun setBalanceMode(context: Context, mode: String) { prefs(context).edit().putString("clusterBalance", mode).apply() }

    /** Manual shares by device ID ("local" for this device); 1 when not set. */
    fun manualShares(context: Context): Map<String, Float> = runCatching {
        val o = JSONObject(prefs(context).getString("clusterShares", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getDouble(it).toFloat() }
    }.getOrDefault(emptyMap())

    fun setManualShare(context: Context, id: String, share: Float) {
        val o = JSONObject(manualShares(context).mapValues { it.value.toDouble() })
        o.put(id, share.toDouble())
        prefs(context).edit().putString("clusterShares", o.toString()).apply()
    }

    /**
     * Each device's share of the model for [mode]: speed, free memory, equal, or the manual
     * sliders. The automatic modes never give a device more than ~80% of its free memory.
     */
    fun shares(mode: String, ids: List<String>, freeMb: List<Long>, bandwidth: List<Double>, manual: Map<String, Float>, modelMb: Double): List<Double> {
        val raw = when (mode) {
            "memory" -> freeMb.map { it.toDouble() }
            "even" -> ids.map { 1.0 }
            "manual" -> ids.map { (manual[it] ?: 1f).toDouble().coerceAtLeast(0.01) }
            // Speed, falling back to memory when a device couldn't measure it.
            else -> if (bandwidth.all { it > 0 }) bandwidth else freeMb.map { it.toDouble() }
        }
        return if (mode == "manual") raw else fitSharesToMemory(raw, freeMb.map { it * 0.8 }, modelMb)
    }

    /** Pairs with [device] using [code]; returns an error message, or null when paired. */
    fun pair(context: Context, device: NearbyDevice, code: String): String? {
        val free = ask(device.host, device.port, code) ?: return "Couldn't reach ${device.name}. Is sharing still on?"
        if (free < 0) return "Wrong code. Check the 4 digits on ${device.name}."
        savePaired(context, paired(context).filterNot { it.id == device.id } + ClusterDevice(device.id, device.name, device.host, device.port, code))
        return null
    }

    /** Looks for devices sharing on this network; [nearby] updates as they're found. */
    fun startDiscovery(context: Context) {
        if (discovery != null) return
        val nsd = context.getSystemService(NsdManager::class.java)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { discovery = null }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) {
                _nearby.value = _nearby.value.filterNot { it.name == info.serviceName.removePrefix("Mochu ") }
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val attrs = info.attributes
                            val id = attrs["id"]?.toString(Charsets.UTF_8) ?: host
                            // Don't list this device itself.
                            if (id == ClusterWorkerService.deviceId(context)) return
                            val device = NearbyDevice(
                                id = id,
                                name = attrs["model"]?.toString(Charsets.UTF_8) ?: info.serviceName.removePrefix("Mochu "),
                                host = host,
                                port = info.port,
                                freeMb = attrs["ram"]?.toString(Charsets.UTF_8)?.toLongOrNull() ?: 0L
                            )
                            _nearby.value = _nearby.value.filterNot { it.id == id } + device
                            // A paired device that moved to a new address is found again by its ID.
                            val paired = paired(context)
                            paired.firstOrNull { it.id == id && (it.host != host || it.port != info.port) }?.let { old ->
                                savePaired(context, paired.map { if (it.id == id) old.copy(host = host, port = info.port) else it })
                            }
                        }
                    })
                }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { discovery = null }
    }

    fun stopDiscovery(context: Context) {
        discovery?.let { runCatching { context.getSystemService(NsdManager::class.java).stopServiceDiscovery(it) } }
        discovery = null
    }

    /** A local port llama.cpp connects to; each connection is passed to [device] after its code. */
    private fun relayFor(device: ClusterDevice): Int {
        relays[device.id]?.takeIf { !it.isClosed }?.let { return it.localPort }
        // IPv4 on purpose: llama.cpp connects to 127.0.0.1 only, and Android's loopback can be ::1.
        val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        relays[device.id] = server
        Thread({
            while (!server.isClosed) {
                val local = runCatching { server.accept() }.getOrNull() ?: break
                Thread({
                    runCatching {
                        local.tcpNoDelay = true
                        val remote = Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(device.host, device.port), 5_000) }
                        remote.getOutputStream().write("$HELLO ${device.code} CONNECT ${Build.MODEL.replace(' ', '_')} $loadingModel\n".toByteArray())
                        remote.soTimeout = 5_000
                        if (!readLine(remote.getInputStream()).startsWith("OK")) error("refused")
                        remote.soTimeout = 0
                        joinThen(local, remote, sentBytes.getOrPut(device.id) { java.util.concurrent.atomic.AtomicLong() }) {}
                    }.onFailure { runCatching { local.close() } }
                }, "cluster-relay").apply { isDaemon = true }.start()
            }
        }, "cluster-relay-accept").apply { isDaemon = true }.start()
        return server.localPort
    }

    /**
     * Before loading a GGUF model: when combining is on, checks every paired device and tells
     * llama.cpp to use the ones that answer. Returns how many are used (0 = this device only).
     * Runs on a background thread.
     */
    /** File name of the model being loaded, sent to workers so they can label what they cache. */
    @Volatile private var loadingModel = "model"

    fun prepareForLoad(context: Context, modelName: String = "model", modelBytes: Long = 0, modelPath: String? = null): Int {
        loadingModel = modelName.replace(Regex("""\s+"""), "_")
        val devices = if (enabled(context)) paired(context) else emptyList()
        val ready = devices.mapNotNull { d -> askInfo(d.host, d.port, d.code)?.takeIf { it.freeMb > 0 }?.let { d to it } }
        active = ready.map { it.first }
        LlamaCppNative.nativeClusterClearLost()
        if (ready.isEmpty()) {
            LlamaCppNative.nativeSetCluster(emptyArray(), FloatArray(0), 1f)
            releaseWifi()
            return 0
        }
        _loadingOn.value = ready.map { it.first.name }
        val endpoints = ready.map { (d, _) -> "127.0.0.1:${relayFor(d)}" }.toTypedArray()
        // This device first, then each worker: shares by the chosen balance mode.
        val mode = balanceMode(context)
        val split = shares(
            mode,
            ids = listOf("local") + ready.map { it.first.id },
            freeMb = listOf(freeMemoryMb(context)) + ready.map { it.second.freeMb },
            bandwidth = listOf(if (mode == "speed") memoryBandwidth(context) else 0.0) + ready.map { it.second.bandwidth },
            manual = manualShares(context),
            modelMb = modelBytes / 1048576.0
        )
        LlamaCppNative.nativeSetCluster(endpoints, split.drop(1).map { it.toFloat() }.toFloatArray(), split.first().toFloat())
        // Each device's expected part of the file and of the layers, for the loading progress.
        val splitSum = split.sum().takeIf { it > 0 } ?: 1.0
        val layerCount = modelPath?.let { LlamaCppNative.nativeBlockCount(it) }?.takeIf { it > 0 } ?: 0
        watchSending(ready.mapIndexed { i, (d, _) ->
            val part = split[i + 1] / splitSum
            Triple(d, (modelBytes * part).toLong(), (layerCount * part).roundToInt())
        })
        holdWifi(context)
        return ready.size
    }

    /** Before a reply: whether every device the model uses still answers (a quick check each). */
    fun activeStillThere(): Boolean = active.all { d -> (ask(d.host, d.port, d.code) ?: -1L) >= 0 }

    /**
     * After a device dropped out: llama.cpp keeps a connection per address, so new relay ports make
     * the next load connect afresh.
     */
    fun onLost() {
        relays.values.forEach { runCatching { it.close() } }
        relays.clear()
        if (active.isNotEmpty()) lastLost = active
        active = emptyList()
        releaseWifi()
    }

    /** Names of the devices that dropped out (or that didn't answer), for messages. */
    fun lostNames(): String = lastLost.filter { (ask(it.host, it.port, it.code) ?: -1L) < 0 }.ifEmpty { lastLost }
        .joinToString(" and ") { it.name }.ifBlank { "A device" }

    /**
     * After a device dropped out: waits up to [timeoutMs] for every device the model was using to
     * answer again (a Wi-Fi blip, a screen that briefly turned off). Runs on a background thread.
     */
    fun waitForLost(timeoutMs: Long): Boolean {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            if (lastLost.isNotEmpty() && lastLost.all { (ask(it.host, it.port, it.code) ?: -1L) >= 0 }) return true
            Thread.sleep(1_000)
        }
        return false
    }

    private fun holdWifi(context: Context) {
        if (wifiLock?.isHeld == true) return
        @Suppress("DEPRECATION")
        wifiLock = context.applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Mochu:cluster").apply { acquire() }
    }

    fun releaseWifi() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }
}

/**
 * What other devices' models left in this device's cache (llama.cpp names files by content hash),
 * grouped by model so each can be deleted on its own.
 */
object ClusterCache {
    data class Entry(val model: String, val bytes: Long, val files: List<File>)

    fun dir(context: Context) = File(context.filesDir, "cluster_cache")
    private fun index(context: Context) = File(context.filesDir, "cluster_cache_index.json")

    private fun readIndex(context: Context): JSONObject =
        runCatching { JSONObject(index(context).readText()) }.getOrDefault(JSONObject())

    /** Gives every cache file without a model the name [model]. */
    @Synchronized
    fun label(context: Context, model: String) {
        val idx = readIndex(context)
        var changed = false
        dir(context).walkTopDown().filter { it.isFile }.forEach { f ->
            val key = f.relativeTo(dir(context)).path
            if (!idx.has(key)) { idx.put(key, model); changed = true }
        }
        if (changed) runCatching { index(context).writeText(idx.toString()) }
    }

    /** The cached models, biggest first; files from before labeling count as "Earlier models". */
    fun list(context: Context): List<Entry> {
        val idx = readIndex(context)
        return dir(context).walkTopDown().filter { it.isFile }
            .groupBy { f -> idx.optString(f.relativeTo(dir(context)).path).ifBlank { "Earlier models" } }
            .map { (model, files) -> Entry(model, files.sumOf { it.length() }, files) }
            .sortedByDescending { it.bytes }
    }

    @Synchronized
    fun delete(context: Context, entry: Entry) {
        val idx = readIndex(context)
        entry.files.forEach { f -> f.delete(); idx.remove(f.relativeTo(dir(context)).path) }
        runCatching { index(context).writeText(idx.toString()) }
    }
}
