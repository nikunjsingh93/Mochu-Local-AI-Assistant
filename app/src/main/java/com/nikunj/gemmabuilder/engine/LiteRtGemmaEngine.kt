package com.nikunj.gemmabuilder

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.graphics.pdf.PdfRenderer
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewAssetLoader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.coroutines.resume


// LiteRT-LM engine for .litertlm models, plus the multimodal stub and the demo engine.

class LiteRtGemmaEngine(
    private val context: Context,
    private val modelPath: String,
    private val backendPreference: String,
    private val speculativeDecodingEnabled: Boolean,
    private val maxNumTokens: Int
) : BuilderEngine {
    private data class LoadAttempt(val backend: String, val speculativeDecoding: Boolean, val maxNumTokens: Int?)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationKey: String? = null
    private var conversationPrompt: String? = null
    private var conversationThinking = false
    private var tokenLimit: Int? = null
    @Volatile private var lastStats: GenerationStats? = null

    /** Backend the model actually loaded on, e.g. "GPU" or "CPU". */
    var activeBackend: String = backendPreference.uppercase(Locale.US)
        private set

    /** Why the preferred setup was not used, or null when it loaded as configured. */
    var fallbackReason: String? = null
        private set

    @OptIn(ExperimentalApi::class)
    suspend fun load() = withContext(Dispatchers.IO) {
        val preferred = backendPreference.lowercase(Locale.US)
        // Try the requested setup first, then progressively safer ones: without speculative decoding,
        // with the model's default context window, and finally on CPU.
        val attempts = buildList {
            add(LoadAttempt(preferred, speculativeDecodingEnabled, maxNumTokens))
            add(LoadAttempt(preferred, false, maxNumTokens))
            add(LoadAttempt(preferred, false, null))
            if (preferred != "cpu") {
                add(LoadAttempt("cpu", false, maxNumTokens))
                add(LoadAttempt("cpu", false, null))
            }
        }.distinct()

        var firstError: Throwable? = null
        for (attempt in attempts) {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = when (attempt.backend) {
                    "gpu" -> Backend.GPU()
                    "npu" -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
                    else -> Backend.CPU()
                },
                maxNumTokens = attempt.maxNumTokens,
                cacheDir = context.cacheDir.absolutePath,
                // Half-precision GPU activations garble digits in long prompts (a web page summary
                // turned "June 16, 2026" into "June6, 2, 026"); full precision keeps them exact.
                activationDataType = if (attempt.backend == "gpu") ActivationDataType.FLOAT32 else null
            )
            val candidate = Engine(config)
            val result = runCatching {
                try {
                    ExperimentalFlags.enableSpeculativeDecoding = attempt.speculativeDecoding
                    // Benchmark counters give exact decode token counts and speed per reply.
                    ExperimentalFlags.enableBenchmark = true
                    candidate.initialize()
                } finally {
                    ExperimentalFlags.enableSpeculativeDecoding = null
                    ExperimentalFlags.enableBenchmark = false
                }
            }
            if (result.isSuccess) {
                engine = candidate
                tokenLimit = attempt.maxNumTokens
                activeBackend = attempt.backend.uppercase(Locale.US)
                fallbackReason = if (attempt == attempts.first()) null else firstError?.message ?: "unknown error"
                return@withContext
            }
            if (firstError == null) firstError = result.exceptionOrNull()
            runCatching { if (candidate.isInitialized()) candidate.close() }
        }
        throw firstError ?: IllegalStateException("Model load failed.")
    }

    private fun newConversationConfig(systemPrompt: String, thinking: Boolean) = ConversationConfig(
        systemInstruction = Contents.of(systemPrompt),
        samplerConfig = SamplerConfig(
            topK = 20,
            topP = 0.9,
            temperature = 0.35
        ),
        // Leave the other half of the window for the prompt and earlier turns.
        maxOutputToken = tokenLimit?.let { it / 2 },
        // Reasoning streams on its own channel, which generate() passes on as <think>...</think>.
        // Studio turns it off: its file-writing replies are long enough already.
        thinkingConfig = ThinkingConfig(enableThinking = thinking)
    )

    override fun generate(sessionKey: String, message: String, systemPrompt: String, thinking: Boolean): Flow<String> = flow {
        val loadedEngine = engine ?: run {
            emit("Model is not loaded.")
            return@flow
        }
        val active = conversation?.takeIf {
            conversationKey == sessionKey && conversationPrompt == systemPrompt && conversationThinking == thinking && it.isAlive
        } ?: run {
            resetSession()
            loadedEngine.createConversation(newConversationConfig(systemPrompt, thinking)).also {
                conversation = it
                conversationKey = sessionKey
                conversationPrompt = systemPrompt
                conversationThinking = thinking
            }
        }
        var finished = false
        var chunks = 0
        var firstChunkAt = 0L
        lastStats = null
        try {
            var inThought = false
            active.sendMessageAsync(message).collect { token ->
                if (chunks++ == 0) firstChunkAt = System.nanoTime()
                val thought = token.channels.values.joinToString("")
                if (thought.isNotEmpty()) {
                    if (!inThought) emit("<think>")
                    inThought = true
                    emit(thought)
                }
                val text = token.toString()
                if (text.isNotEmpty()) {
                    if (inThought) emit("</think>")
                    inThought = false
                    emit(text)
                }
            }
            if (inThought) emit("</think>")
            finished = true
            @OptIn(ExperimentalApi::class)
            val benchmark = runCatching { active.getBenchmarkInfo() }.getOrNull()
            lastStats = if (benchmark != null && benchmark.lastDecodeTokensPerSecond > 0) {
                GenerationStats(benchmark.lastDecodeTokenCount, benchmark.lastDecodeTokenCount / benchmark.lastDecodeTokensPerSecond)
            } else if (chunks > 1) {
                // Without benchmark counters, one streamed chunk is roughly one token.
                GenerationStats(chunks, (System.nanoTime() - firstChunkAt) / 1e9)
            } else {
                null
            }
        } finally {
            // A failed or cancelled turn leaves a partial reply in the KV cache, so start over next time.
            if (!finished) resetSession()
        }
    }

    override fun takeGenerationStats(): GenerationStats? = lastStats.also { lastStats = null }

    override fun remainingTokens(sessionKey: String): Int? {
        val limit = tokenLimit ?: return null
        val active = conversation?.takeIf { conversationKey == sessionKey && it.isAlive } ?: return null
        return runCatching { limit - active.getTokenCount() }.getOrNull()
    }

    override fun resetSession() {
        conversation?.let { open ->
            runCatching { open.cancelProcess() }
            runCatching { open.close() }
        }
        conversation = null
        conversationKey = null
    }

    override fun close() {
        resetSession()
        runCatching { engine?.close() }
        engine = null
    }
}

class MediaPipeMultimodalEngine(
    private val context: Context,
    private val modelPath: String
) {
    fun load() = Unit
    fun isReady(): Boolean = false
    fun generateResponse(prompt: String, root: File, attachmentPaths: List<String>): String = ""
    fun close() = Unit
}

class DemoBuilderEngine : BuilderEngine {
    override fun generate(sessionKey: String, message: String, systemPrompt: String, thinking: Boolean): Flow<String> = flow {
        val lower = message.lowercase(Locale.US)
        val html = when {
            "calculator" in lower -> calculatorHtml()
            "racing" in lower || "ps1" in lower || "game" in lower -> racingMenuHtml()
            else -> genericAppHtml(message)
        }
        emit(
            """
<action name="write_file">
<path>index.html</path>
<content>
$html
</content>
</action>
            """.trimIndent()
        )
    }
}


