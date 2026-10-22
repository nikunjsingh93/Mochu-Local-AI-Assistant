package com.nikunj.gemmabuilder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

// Keeps a Chat or Code reply running when the user leaves the app, and tells them when it's done.

/** Whether an app screen is on top, so a finished reply only notifies when the user is elsewhere. */
object AppVisibility {
    @Volatile var visible = false
}

/** A quiet "Writing a reply…" notification that keeps the process alive while the model works. */
class ReplyKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_WORKING, "Replies in progress", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 11, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_stat_mochu)
            .setContentTitle("Mochu is writing a reply…")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        runCatching { startForeground(NOTIFICATION_WORKING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
        instance = this
        // The reply may have ended while the service was starting.
        if (stopWanted) stopNow()
        return START_NOT_STICKY
    }

    private fun stopNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_WORKING = "reply_working"
        private const val CHANNEL_DONE = "reply_done"
        private const val NOTIFICATION_WORKING = 7320
        private const val NOTIFICATION_DONE = 7321

        @Volatile private var instance: ReplyKeepAliveService? = null
        @Volatile private var stopWanted = false
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var pendingStart: Runnable? = null

        /** Called as a reply starts; the notification only appears if it takes more than a moment. */
        fun started(context: Context) {
            stopWanted = false
            val app = context.applicationContext
            val start = Runnable {
                pendingStart = null
                // Only while the app is on screen: Android doesn't allow starting it from the background.
                if (AppVisibility.visible) runCatching { app.startForegroundService(Intent(app, ReplyKeepAliveService::class.java)) }
            }
            pendingStart = start
            handler.postDelayed(start, 1_500)
        }

        /** Called when the reply ends; posts "reply ready" when the user has left the app. */
        fun finished(context: Context, chatId: String?, preview: String?) {
            pendingStart?.let(handler::removeCallbacks)
            pendingStart = null
            stopWanted = true
            handler.post { instance?.stopNow() }
            if (AppVisibility.visible || preview.isNullOrBlank()) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished replies", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 12,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    chatId?.let { putExtra(EXTRA_OPEN_CHAT, it) }
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = preview.replace(Regex("""[*_#`>]"""), "").trim().take(300)
            val notification = Notification.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_mochu)
                .setContentTitle("Mochu replied")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_DONE, notification) }
        }
    }
}
