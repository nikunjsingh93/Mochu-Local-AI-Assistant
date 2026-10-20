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
    val statsInline: String? = null,
    /** What the assistant did to produce this reply (searches, pages read), shown as an activity log. */
    val activity: List<ActivityStep> = emptyList(),
    /** The model's reasoning before it answered, shown collapsed above the reply. */
    val thinking: String = "",
    val thinkingSeconds: Int = 0
)

/** One entry in a reply's activity log. [kind] is one of the ACTIVITY_* constants. */
data class ActivityStep(val kind: String, val text: String, val url: String? = null)

const val ACTIVITY_SEARCH = "search"
const val ACTIVITY_READ = "read"
const val ACTIVITY_SKIP = "skip"
const val ACTIVITY_NOTE = "note"

data class ChatConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    /** Model-written summary of the first [summarizedCount] messages, used once they no longer fit. */
    val summary: String = "",
    val summarizedCount: Int = 0,
    /** [CHAT_KIND_ASSISTANT] for general chat, [CHAT_KIND_STUDIO] for a web-app project. */
    val kind: String = CHAT_KIND_STUDIO,
    /** Code chats in the same project share its files; blank means the chat is its own project. */
    val projectId: String = ""
) {
    /** The folder this chat's files live in (projects/<projectKey>). */
    val projectKey: String get() = projectId.ifBlank { id }
}

/** A general-purpose assistant chat (the Chat space). */
const val CHAT_KIND_ASSISTANT = "assistant"
/** A web-app project with files and a live preview (the Studio space). Chats saved before kinds existed are this kind. */
const val CHAT_KIND_STUDIO = "studio"

data class ConversationInfo(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val kind: String = CHAT_KIND_STUDIO,
    val projectKey: String = id
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
    val contextTokens: Int = 16384,
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
    val memoryNotice: String? = null,
    /** Window the loaded model actually got; null when no model is loaded. */
    val contextWindowTokens: Int? = null,
    /** Tokens held in the current chat session, when the engine can tell. */
    val contextUsedTokens: Int? = null,
    /** Generation speed of the most recent reply. */
    val lastTokensPerSecond: Double? = null,
    /** True when older messages of the active chat were folded into a summary. */
    val chatSummarized: Boolean = false,
    /** Which space the active chat belongs to: [CHAT_KIND_ASSISTANT] or [CHAT_KIND_STUDIO]. */
    val activeKind: String = CHAT_KIND_ASSISTANT,
    /** Assistant reply text streamed so far, or null when no assistant reply is being generated. */
    val streamingReply: String? = null,
    /** Reasoning streamed so far for the reply being generated, or null when the model isn't thinking. */
    val streamingThinking: String? = null,
    /** How long the model thought, once it has finished thinking and started answering. */
    val streamingThinkingSeconds: Int = 0,
    /** True while a model is being loaded into memory. */
    val modelLoading: Boolean = false,
    /** Context window chosen per downloaded model (file name → tokens), for the Models page sliders. */
    val modelContextTokens: Map<String, Int> = emptyMap(),
    /** Names the user gave Code projects (project key → name). */
    val projectNames: Map<String, String> = emptyMap(),
    /** Photos attached to the Chat message being written (absolute paths in app storage). */
    val chatImages: List<String> = emptyList(),
    /** Facts the user asked Chat to remember, added to its system prompt when [memoryEnabled]. */
    val memories: List<String> = emptyList(),
    val memoryEnabled: Boolean = true,
    /** Reads a reply aloud automatically when the message was spoken with the mic. */
    val speakVoiceReplies: Boolean = true,
    /** Timestamp of the reply being read aloud, or null. */
    val speakingMessageId: Long? = null,
    /** Lets models that support it reason before answering in Chat. */
    val thinkingEnabled: Boolean = true,
    /** How hard Chat thinks when thinking is on: 1 quick, 2 balanced, 3 deep. */
    val thinkingLevel: Int = 3,
    /** True while Chat is creating an image (a placeholder shows where it will appear). */
    val imageGenerating: Boolean = false,
    /** Number type GGUF models keep their KV cache in. */
    val kvCacheType: KvCacheType = KvCacheType.F16,
    /** Steps of the web research running right now, shown live above the streaming reply. */
    val liveActivity: List<ActivityStep> = emptyList(),
    /** Chat composer's "search the web" toggle. */
    val webSearchEnabled: Boolean = true,
    /** Shows the one-time web access consent dialog. */
    val webConsentPending: Boolean = false,
    /** URL the Browser tab should open next (set by tapping a source link). */
    val browserUrl: String? = null
)

enum class DownloadPhase { NotDownloaded, Queued, WaitingForNetwork, Downloading, Verifying, Downloaded, Failed }

data class ModelDownloadState(
    val phase: DownloadPhase = DownloadPhase.NotDownloaded,
    val progress: Float = 0f,
    val message: String? = null,
    val partialBytes: Long = 0L
)

data class WriteFileAction(val path: String, val content: String)

/** Tokens produced while decoding a reply (excluding prompt processing) and the time it took. */
data class GenerationStats(val outputTokens: Int, val decodeSeconds: Double)

interface BuilderEngine {
    /**
     * Streams a reply to [message], continuing the open session for [sessionKey] if there is one.
     * A session is tied to its [systemPrompt]: Studio projects and assistant chats use different ones.
     * With [thinking], models that can reason first stream it wrapped in <think>...</think>.
     */
    fun generate(
        sessionKey: String,
        message: String,
        systemPrompt: String = BUILD_SYSTEM_PROMPT,
        thinking: Boolean = false,
        images: List<String> = emptyList(),
        /** Most tokens the model may think for; -1 is no limit. */
        thinkingBudget: Int = -1
    ): Flow<String>
    /** Whether [generate] can take [images] (absolute paths of photos to send with the message). */
    val supportsImages: Boolean get() = false
    /** Tokens left in the open session for [sessionKey], or null when no reusable session is open. */
    fun remainingTokens(sessionKey: String): Int? = null
    /** Drops the open session so the next [generate] starts a fresh conversation. */
    fun resetSession() {}
    /** Speed of the last completed [generate] call, cleared once read; null if unknown. */
    fun takeGenerationStats(): GenerationStats? = null
    fun close() {}
}
