package com.nikunj.gemmabuilder

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Keeps the CPU running while a reply or scheduled task is being generated, so it finishes with the
 * screen off. Held only while work runs, by name ("reply", "task"), and released when the last one
 * ends. As a safety net, each hold lasts 30 minutes and is renewed every 10 while work continues,
 * up to 2 hours in total, so a stuck generation can never keep the phone awake for long.
 */
object WorkWakeLock {
    private const val HOLD_MS = 30 * 60_000L
    private const val RENEW_MS = 10 * 60_000L
    private const val MAX_TOTAL_MS = 2 * 60 * 60_000L

    private val handler = Handler(Looper.getMainLooper())
    private val holders = mutableSetOf<String>()
    private var lock: PowerManager.WakeLock? = null
    private var since = 0L

    private val renew = object : Runnable {
        override fun run() {
            synchronized(this@WorkWakeLock) {
                val held = lock ?: return
                if (holders.isEmpty() || System.currentTimeMillis() - since > MAX_TOTAL_MS) {
                    if (held.isHeld) held.release()
                    holders.clear()
                    return
                }
                held.acquire(HOLD_MS)
            }
            handler.postDelayed(this, RENEW_MS)
        }
    }

    @Synchronized
    fun acquire(context: Context, holder: String) {
        val wasIdle = holders.isEmpty()
        holders += holder
        if (!wasIdle) return
        val l = lock ?: context.applicationContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mochu:generating")
            .apply { setReferenceCounted(false) }
            .also { lock = it }
        since = System.currentTimeMillis()
        runCatching { l.acquire(HOLD_MS) }
        handler.removeCallbacks(renew)
        handler.postDelayed(renew, RENEW_MS)
    }

    @Synchronized
    fun release(holder: String) {
        holders -= holder
        if (holders.isNotEmpty()) return
        handler.removeCallbacks(renew)
        lock?.takeIf { it.isHeld }?.let { runCatching { it.release() } }
    }
}
