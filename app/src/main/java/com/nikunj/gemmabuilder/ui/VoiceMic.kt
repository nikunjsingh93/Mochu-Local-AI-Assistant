package com.nikunj.gemmabuilder

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** State for a composer's mic button: tap to talk, tap again to stop. */
class VoiceMicState internal constructor(val onMic: () -> Unit, private val listeningState: () -> Boolean, private val levelState: () -> Float) {
    val listening: Boolean get() = listeningState()
    val level: Float get() = levelState()
}

/**
 * Speech-to-text for a message box. Partial words go to [onPartial] as you speak; the final text goes
 * to [onFinal] when you pause. Asks for the microphone permission the first time.
 */
@Composable
fun rememberVoiceMic(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onStart: () -> Unit = {}): VoiceMicState {
    val context = LocalContext.current
    val voice = remember { VoiceInput(context) }
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    val partial by rememberUpdatedState(onPartial)
    val final by rememberUpdatedState(onFinal)
    val start by rememberUpdatedState(onStart)
    DisposableEffect(Unit) { onDispose { voice.stop() } }

    fun startListening() {
        start()
        listening = true
        voice.start(
            onLevel = { level = it },
            onPartial = { partial(it) },
            onFinal = { text ->
                listening = false
                if (text.isNotBlank()) final(text)
            },
            onError = { message ->
                listening = false
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else Toast.makeText(context, "Allow the microphone to talk to Mochu.", Toast.LENGTH_SHORT).show()
    }
    return remember {
        VoiceMicState(
            onMic = {
                when {
                    listening -> voice.finish()
                    !voice.available -> Toast.makeText(context, "This phone has no speech recognizer.", Toast.LENGTH_SHORT).show()
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startListening()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            listeningState = { listening },
            levelState = { level }
        )
    }
}
