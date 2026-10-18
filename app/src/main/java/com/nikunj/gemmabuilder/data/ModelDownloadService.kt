package com.nikunj.gemmabuilder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Live download state shared between [ModelDownloadService] and the ViewModel. */
object ModelDownloadTracker {
    /** Only models with a queued, running, or failed download appear here. */
    val states = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())

    /** Emits a model id once its file is downloaded and verified. */
    val completed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun set(id: String, state: ModelDownloadState?) {
        states.update { if (state == null) it - id else it + (id to state) }
    }
}

/**
 * Downloads catalog models one at a time with HTTP range resume.
 *
 * Runs as a user-initiated foreground service instead of DownloadManager: DownloadManager jobs can be
 * deferred indefinitely while the phone is warm or busy, which leaves users staring at "Starting…".
 */
class ModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<String>()
    @Volatile private var current: String? = null
    private var currentJob: Job? = null
    private var worker: Job? = null
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService(), even for a cancel.
        startInForeground(notification("Preparing download…", progress = null))
        val id = intent?.getStringExtra(EXTRA_MODEL_ID)
        when (intent?.action) {
            ACTION_START -> if (id != null) enqueue(id)
            ACTION_CANCEL -> if (id != null) cancel(id)
        }
        if (worker?.isActive != true && synchronized(pending) { pending.isEmpty() }) stopSelf()
        return START_NOT_STICKY
    }

    private fun enqueue(id: String) {
        val added = synchronized(pending) {
            if (id == current || id in pending) false else pending.addLast(id).let { true }
        }
        if (added) ModelDownloadTracker.set(id, ModelDownloadState(DownloadPhase.Queued, message = "Queued…"))
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val next = synchronized(pending) { pending.removeFirstOrNull() } ?: break
                val model = MODEL_CATALOG.firstOrNull { it.id == next } ?: continue
                current = next
                currentJob = launch { download(model) }
                currentJob?.join()
                current = null
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancel(id: String) {
        synchronized(pending) { pending.remove(id) }
        if (current == id) currentJob?.cancel()
        MODEL_CATALOG.firstOrNull { it.id == id }?.let { partialModelFile(this, it).delete() }
        ModelDownloadTracker.set(id, null)
    }

    private suspend fun download(model: CatalogModel) {
        val partial = partialModelFile(this, model)
        partial.parentFile?.mkdirs()
        var failures = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!networkAllowed()) {
                    report(model, DownloadPhase.WaitingForNetwork, partial.length(), if (savedAllowMobileDataDownloads(this)) "Waiting for network" else "Waiting for Wi-Fi")
                    delay(3000)
                    continue
                }
                try {
                    transfer(model)
                    break
                } catch (e: IOException) {
                    failures++
                    if (failures > 8) {
                        ModelDownloadTracker.set(model.id, ModelDownloadState(DownloadPhase.Failed, message = "Download failed: ${e.message ?: "network error"}. Tap Resume to try again."))
                        return
                    }
                    report(model, DownloadPhase.WaitingForNetwork, partial.length(), "Connection lost, retrying…")
                    delay(minOf(30_000L, 2_000L * failures))
                }
            }

            report(model, DownloadPhase.Verifying, model.sizeBytes, "Checking file…")
            val verified = partial.length() == model.sizeBytes && sha256Of(partial) == model.sha256
            if (!verified) {
                partial.delete()
                ModelDownloadTracker.set(model.id, ModelDownloadState(DownloadPhase.Failed, message = "The file didn't match its expected fingerprint and was deleted. Try again."))
                return
            }
            val target = downloadedModelFile(this, model)
            target.delete()
            if (!partial.renameTo(target)) {
                ModelDownloadTracker.set(model.id, ModelDownloadState(DownloadPhase.Failed, message = "Could not save the downloaded file."))
                return
            }
            ModelDownloadTracker.set(model.id, null)
            ModelDownloadTracker.completed.tryEmit(model.id)
        } catch (e: CancellationException) {
            // Cancelled by the user or the system; a kept .part file lets the user resume later.
            ModelDownloadTracker.set(model.id, null)
            throw e
        }
    }

    /** Downloads the rest of the file, resuming from whatever is already in the .part file. */
    private suspend fun transfer(model: CatalogModel) {
        val partial = partialModelFile(this, model)
        val existing = if (partial.exists()) partial.length() else 0L
        if (existing == model.sizeBytes) return
        if (existing > model.sizeBytes) partial.delete()

        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OnDeviceStudio")
            if (existing in 1 until model.sizeBytes) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val append = when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false
                416 -> {
                    partial.delete()
                    throw IOException("server rejected the resume point")
                }
                else -> throw IOException("HTTP $code")
            }
            var downloaded = if (append) existing else 0L
            FileOutputStream(partial, append).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        report(model, DownloadPhase.Downloading, downloaded, null)
                    }
                }
            }
            if (downloaded < model.sizeBytes) throw IOException("connection closed early")
        } finally {
            connection.disconnect()
        }
    }

    private fun networkAllowed(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return savedAllowMobileDataDownloads(this) || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun report(model: CatalogModel, phase: DownloadPhase, bytes: Long, message: String?) {
        val now = System.currentTimeMillis()
        // Progress ticks are frequent; keep UI and notification updates to a few per second.
        if (phase == DownloadPhase.Downloading && now - lastNotifyAt < 500) return
        lastNotifyAt = now
        val progress = (bytes.toFloat() / model.sizeBytes).coerceIn(0f, 1f)
        val detail = message ?: "${formatBytes(bytes)} of ${formatBytes(model.sizeBytes)}"
        ModelDownloadTracker.set(model.id, ModelDownloadState(phase, progress, detail, partialBytes = bytes))
        val indeterminate = phase == DownloadPhase.Verifying || phase == DownloadPhase.WaitingForNetwork
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification("${model.name} · $detail", if (indeterminate) null else (progress * 100).toInt()))
    }

    private fun startInForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(text: String, progress: Int?): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading model")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setProgress(100, progress ?: 0, progress == null)
            .build()
    }

    // Android 15+ caps dataSync services; stop cleanly and let the user resume from the .part file.
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.nikunj.gemmabuilder.action.DOWNLOAD_MODEL"
        private const val ACTION_CANCEL = "com.nikunj.gemmabuilder.action.CANCEL_MODEL_DOWNLOAD"
        private const val EXTRA_MODEL_ID = "modelId"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context, modelId: String) = send(context, ACTION_START, modelId)

        fun cancel(context: Context, modelId: String) = send(context, ACTION_CANCEL, modelId)

        private fun send(context: Context, action: String, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(action)
                .putExtra(EXTRA_MODEL_ID, modelId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
