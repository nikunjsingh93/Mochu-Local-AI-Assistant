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
 * Context window to load [modelFile] with: the user's setting, limited by what this phone's RAM can
 * hold next to the model, and by any smaller limit learned from an earlier low-memory kill.
 */
fun safeTokenWindow(context: Context, modelFile: File, requested: Int): Int {
    val modelGb = modelFile.length() / 1_000_000_000.0
    // Measured on an 11 GB phone: Gemma 4 E4B (3.7 GB) on GPU is killed at 16K tokens but runs at 8K.
    val headroomGb = deviceRamGb(context) - modelGb * 1.25
    val ramCap = when {
        headroomGb >= 9 -> 32768
        headroomGb >= 7 -> 16384
        headroomGb >= 4 -> 8192
        else -> 4096
    }
    val learnedCap = savedLearnedTokenCap(context, modelFile.name) ?: Int.MAX_VALUE
    return minOf(requested, ramCap, learnedCap)
}

/** Records what is loaded so a low-memory kill can be traced back to it on the next launch. */
fun recordLoadedModel(context: Context, modelFile: File, tokenWindow: Int) {
    runCatching {
        context.getSystemService(ActivityManager::class.java)
            .setProcessStateSummary("${modelFile.name}|$tokenWindow".toByteArray(Charsets.UTF_8).take(128).toByteArray())
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
    val modelName = summary.substringBefore('|')
    val window = summary.substringAfter('|').toIntOrNull() ?: return null
    if (modelName.isBlank()) return null
    val newCap = maxOf(4096, window / 2)
    if (newCap < window) {
        prefs.edit().putInt("tokenCap_$modelName", newCap).apply()
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

private fun savedLearnedTokenCap(context: Context, modelName: String): Int? =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getInt("tokenCap_$modelName", -1)
        .takeIf { it > 0 }

fun formatTokens(tokens: Int): String = String.format(Locale.US, "%,d", tokens)
