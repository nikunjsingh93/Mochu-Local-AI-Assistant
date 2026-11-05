package com.nikunj.gemmabuilder

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
private fun pipe(input: InputStream, output: OutputStream, onEnd: () -> Unit) {
    Thread({
        val buffer = ByteArray(64 * 1024)
        runCatching {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                output.flush()
            }
        }
        onEnd()
    }, "cluster-pipe").apply { isDaemon = true }.start()
}

/** Joins two sockets both ways; closes both when one side ends. */
private fun join(a: Socket, b: Socket) {
    val close = { runCatching { a.close() }; runCatching { b.close() }; Unit }
    pipe(a.getInputStream(), b.getOutputStream(), close)
    pipe(b.getInputStream(), a.getOutputStream(), close)
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
                            out.write("OK ${freeMemoryMb(this)} ${Build.MODEL.replace(' ', '_')}\n".toByteArray()); client.close(); return@runCatching
                        }
                        out.write("OK\n".toByteArray())
                        client.soTimeout = 0
                        val rpc = Socket("127.0.0.1", RPC_PORT).apply { tcpNoDelay = true }
                        peer = client.inetAddress.hostAddress
                        writeStatus("running", code)
                        updateNotification("Sharing with ${peer} · code $code")
                        join(client, rpc)
                    }.onFailure { runCatching { client.close() } }
                }, "cluster-gate").apply { isDaemon = true }.start()
            }
        }, "cluster-accept").apply { isDaemon = true }.start()
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
        val json = JSONObject().put("state", state).put("code", code).put("freeMb", freeMemoryMb(this))
        peer?.let { json.put("peer", it) }
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
        fun readStatus(context: Context): JSONObject =
            statusFile(context).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                ?: JSONObject().put("state", "stopped")

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
    fun ask(host: String, port: Int, code: String): Long? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 3_000)
            s.soTimeout = 4_000
            s.tcpNoDelay = true
            s.getOutputStream().write("$HELLO $code INFO\n".toByteArray())
            val reply = readLine(s.getInputStream())
            if (reply.startsWith("OK")) reply.split(' ').getOrNull(1)?.toLongOrNull() ?: 0L else -1L
        }
    }.getOrNull()

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
                        remote.getOutputStream().write("$HELLO ${device.code}\n".toByteArray())
                        remote.soTimeout = 5_000
                        if (!readLine(remote.getInputStream()).startsWith("OK")) error("refused")
                        remote.soTimeout = 0
                        join(local, remote)
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
    fun prepareForLoad(context: Context): Int {
        val devices = if (enabled(context)) paired(context) else emptyList()
        val ready = devices.mapNotNull { d -> ask(d.host, d.port, d.code)?.takeIf { it > 0 }?.let { d to it } }
        active = ready.map { it.first }
        LlamaCppNative.nativeClusterClearLost()
        if (ready.isEmpty()) {
            LlamaCppNative.nativeSetCluster(emptyArray(), FloatArray(0), 1f)
            releaseWifi()
            return 0
        }
        val endpoints = ready.map { (d, _) -> "127.0.0.1:${relayFor(d)}" }.toTypedArray()
        // Shares follow free memory: a device with more room holds more layers.
        val weights = ready.map { (_, freeMb) -> freeMb.toFloat() }.toFloatArray()
        LlamaCppNative.nativeSetCluster(endpoints, weights, freeMemoryMb(context).toFloat())
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
        active = emptyList()
        releaseWifi()
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
