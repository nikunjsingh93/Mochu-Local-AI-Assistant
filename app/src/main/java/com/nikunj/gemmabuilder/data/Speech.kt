package com.nikunj.gemmabuilder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

// Voice for Chat: speech-to-text for the mic button and text-to-speech for reading replies aloud.
// Both prefer the phone's on-device engines.

/** Reply text as it should be spoken: no Markdown, links, citations, sources list or warnings. */
fun speakableText(markdown: String): String = markdown
    .substringBefore("\n**Sources**")
    .lines()
    .filterNot { it.trimStart().startsWith("> ⚠️") || it.trim().startsWith("```") }
    .joinToString("\n")
    .replace(Regex("""\[([^\]]+)]\((https?://[^)]+)\)"""), "$1")
    .replace(Regex("""\[\d+(?:,\s*\d+)*]"""), "")
    .replace(Regex("""^\s*#{1,6}\s*""", RegexOption.MULTILINE), "")
    .replace(Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE), "")
    .replace(Regex("""^\s*>\s?""", RegexOption.MULTILINE), "")
    .replace("**", "")
    .replace(Regex("""(?<![\w*])[*_](\S(?:.*?\S)?)[*_](?![\w*])"""), "$1")
    .replace("`", "")
    .replace("|", " ")
    .replace(Regex("""[ \t]+"""), " ")
    .lines()
    .joinToString("\n") { it.trim() }
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()

/** Splits [text] into pieces the TTS engine accepts, breaking at paragraph or sentence ends. */
fun speechChunks(text: String, maxChars: Int): List<String> {
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    for (sentence in text.split(Regex("""(?<=[.!?])\s+|\n+"""))) {
        val piece = sentence.trim()
        if (piece.isEmpty()) continue
        if (current.length + piece.length + 1 > maxChars && current.isNotEmpty()) {
            chunks += current.toString()
            current.clear()
        }
        // A single sentence longer than the limit is cut hard.
        piece.chunked(maxChars).forEachIndexed { i, part ->
            if (i > 0) { chunks += current.toString(); current.clear() }
            if (current.isNotEmpty()) current.append(' ')
            current.append(part)
        }
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}

/** Reads replies aloud. [speakingId] is the id of the reply being read, or null. */
class Speaker(context: Context) {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<Long, String>? = null
    private val _speakingId = MutableStateFlow<Long?>(null)
    val speakingId: StateFlow<Long?> = _speakingId.asStateFlow()

    private fun engine(): TextToSpeech = tts ?: TextToSpeech(app) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            pending?.let { (id, text) -> speakNow(id, text) }
        } else {
            _speakingId.value = null
        }
        pending = null
    }.also { created ->
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                if (utteranceId.endsWith(":last")) _speakingId.value = null
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { _speakingId.value = null }
            override fun onStop(utteranceId: String, interrupted: Boolean) = Unit
        })
        tts = created
    }

    fun speak(id: Long, markdown: String) {
        val text = speakableText(markdown)
        if (text.isBlank()) return
        _speakingId.value = id
        val engine = engine()
        if (ready) speakNow(id, text) else pending = id to text
    }

    private fun speakNow(id: Long, text: String) {
        val engine = tts ?: return
        engine.stop()
        val max = (TextToSpeech.getMaxSpeechInputLength() - 50).coerceIn(200, 3500)
        val chunks = speechChunks(text, max)
        chunks.forEachIndexed { i, chunk ->
            val utterance = if (i == chunks.lastIndex) "$id:$i:last" else "$id:$i"
            engine.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, utterance)
        }
    }

    fun stop() {
        pending = null
        tts?.stop()
        _speakingId.value = null
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        _speakingId.value = null
    }
}

/**
 * Speech-to-text for the mic button: partial text while you speak, the final text when you pause.
 * Uses the on-device recognizer when the phone has one. Call from the main thread.
 */
class VoiceInput(context: Context) {
    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(app) || SpeechRecognizer.isOnDeviceRecognitionAvailable(app)

    fun start(onLevel: (Float) -> Unit, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        stop()
        val created = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        } else {
            SpeechRecognizer.createSpeechRecognizer(app)
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice input needs a speech pack for this language (Settings → Google → Speech)."
                        else -> "Voice input stopped (error $error)."
                    }
                )
                stop()
            }
            override fun onResults(results: Bundle?) {
                onFinal(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                stop()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
        )
    }

    /** Stops listening; any speech so far still arrives in onFinal. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }
}
