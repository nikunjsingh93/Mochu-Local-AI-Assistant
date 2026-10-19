package com.nikunj.gemmabuilder

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.io.File
import java.util.Locale

// Large models running on the GPU hold a copy of their weights in memory that Android can't page
// out, and the KV cache grows with the context window. Picking too big a window gets the whole app
// killed by the low-memory killer, so the window is sized to the phone and corrected after a kill.

/**
 * Context window to load [modelFile] with: the window chosen for it on the Models page. It is only
 * lowered after the phone actually ran out of memory with it (see [learnFromLowMemoryKill]), and then
 * the saved choice is lowered too, so the slider always shows what is used.
 */
fun safeTokenWindow(context: Context, modelFile: File, requested: Int): Int = requested

/**
 * Version of how models use memory (precision, where the image encoder runs). Bumped when that changes,
 * so a kill under the old setup doesn't shrink the context window under the new one.
 */
private const val MEMORY_PROFILE = "2"

/** Records what is loaded so a low-memory kill can be traced back to it on the next launch. */
fun recordLoadedModel(context: Context, modelFile: File, tokenWindow: Int) {
    runCatching {
        context.getSystemService(ActivityManager::class.java)
            .setProcessStateSummary("${modelFile.name}|$tokenWindow|$MEMORY_PROFILE".toByteArray(Charsets.UTF_8).take(128).toByteArray())
    }
}

/**
 * If the previous run was killed for low memory while a model was loaded, lowers that model's
 * window for next time and returns a message for the user; otherwise null.
 */
fun learnFromLowMemoryKill(context: Context): String? {
    val manager = context.getSystemService(ActivityManager::class.java)
    val last = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() }
        .getOrNull() ?: return null
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    // Some phones (Samsung) report a low-memory kill of the app on screen as a plain SIGKILL.
    val totalKb = deviceRamGb(context) * 1_048_576
    val killedOnScreenForMemory = last.reason == ApplicationExitInfo.REASON_SIGNALED && last.status == 9 &&
        last.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE && last.rss > totalKb * 0.4
    if (last.reason != ApplicationExitInfo.REASON_LOW_MEMORY && !killedOnScreenForMemory) return null
    if (last.timestamp <= prefs.getLong("lastHandledExitTimestamp", 0L)) return null
    prefs.edit().putLong("lastHandledExitTimestamp", last.timestamp).apply()

    val summary = last.processStateSummary?.toString(Charsets.UTF_8) ?: return null
    val parts = summary.split('|')
    // A kill under an older memory setup says nothing about the current one.
    if (parts.getOrNull(2) != MEMORY_PROFILE) return null
    val modelName = parts[0]
    val window = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (modelName.isBlank()) return null
    val newCap = maxOf(4096, window / 2)
    if (newCap < window) {
        saveModelContextTokens(context, modelName, newCap)
        return "The phone ran out of memory running $modelName with a ${formatTokens(window)}-token context. " +
            "It will now use ${formatTokens(newCap)} tokens. Closing other apps also helps."
    }
    return "The phone ran out of memory running $modelName even at ${formatTokens(window)} tokens. " +
        "Try closing other apps or a smaller model."
}

/**
 * True when opening a full web page in a WebView (often several hundred MB for big shops) could push
 * the phone into killing the app, which already holds the model.
 */
fun tooLowOnMemoryForWebView(context: Context): Boolean {
    val info = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
    return info.lowMemory || info.availMem < 1_500_000_000L
}

fun formatTokens(tokens: Int): String = String.format(Locale.US, "%,d", tokens)
