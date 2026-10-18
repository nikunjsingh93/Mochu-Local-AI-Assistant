package com.nikunj.gemmabuilder

import kotlinx.coroutines.flow.Flow
import java.util.Locale

fun isCodeFile(path: String): Boolean {
    val lower = path.lowercase(Locale.US)
    return lower.endsWith(".html") ||
        lower.endsWith(".htm") ||
        lower.endsWith(".css") ||
        lower.endsWith(".js") ||
        lower.endsWith(".mjs") ||
        lower.endsWith(".json") ||
        lower.endsWith(".txt") ||
        lower.endsWith(".md") ||
        lower.endsWith(".xml")
}

fun isPreviewableImageFile(path: String): Boolean {
    val lower = path.lowercase(Locale.US)
    return lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".bmp") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".heic") ||
        lower.endsWith(".heif")
}

fun isAudioFilePath(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
    return isAudioExtension(ext)
}

data class ChatMessage(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<String> = emptyList(),
    val statsInline: String? = null
)

data class ChatConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>
)

data class ConversationInfo(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int
)

data class BuilderUiState(
    val prompt: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<ConversationInfo> = emptyList(),
    val activeConversationId: String? = null,
    val sidebarOpen: Boolean = false,
    val engineLabel: String = "Not loaded",
    val status: String = "Import a .litertlm or .gguf model to begin.",
    val modelName: String = "Gemma model",
    val modelSupportsMultimodal: Boolean = false,
    val multimodalBackendReady: Boolean = false,
    val activeRoutingMode: String = "text",
    val modelReady: Boolean = false,
    val gemmaLoaded: Boolean = false,
    val isBusy: Boolean = false,
    val previewVersion: Int = 0,
    val tab: Int = 0,
    val indexHtmlPath: String? = null,
    val selectedCodePath: String = "index.html",
    val currentCode: String = "",
    val files: List<String> = emptyList(),
    val lastWrittenPaths: List<String> = emptyList(),
    val lastRawResponse: String = "",
    val streamingCode: String = "",
    val previewFullscreen: Boolean = false,
    val workPanelCollapsed: Boolean = false,
    val chatFontScale: Float = 1.2f,
    val codeFontScale: Float = 1f,
    val contextSizeChars: Int = 24000,
    val backendPreference: String = "gpu",
    val speculativeDecodingEnabled: Boolean = false,
    val estimatedFileContextChars: Int = 0,
    val pendingAttachments: List<String> = emptyList(),
    val generationMetrics: String? = null,
    val previewImagePath: String? = null,
    val modelDownloads: Map<String, ModelDownloadState> = emptyMap(),
    val allowMobileDataDownloads: Boolean = false,
    val activeModelPath: String? = null,
    /** Shown once after the system killed the app for low memory. */
    val memoryNotice: String? = null
)

enum class DownloadPhase { NotDownloaded, Queued, WaitingForNetwork, Downloading, Verifying, Downloaded, Failed }

data class ModelDownloadState(
    val phase: DownloadPhase = DownloadPhase.NotDownloaded,
    val progress: Float = 0f,
    val message: String? = null,
    val partialBytes: Long = 0L
)

data class WriteFileAction(val path: String, val content: String)

interface BuilderEngine {
    /** Streams a reply to [message], continuing the open session for [sessionKey] if there is one. */
    fun generate(sessionKey: String, message: String): Flow<String>
    /** Tokens left in the open session for [sessionKey], or null when no reusable session is open. */
    fun remainingTokens(sessionKey: String): Int? = null
    /** Drops the open session so the next [generate] starts a fresh conversation. */
    fun resetSession() {}
    fun close() {}
}
