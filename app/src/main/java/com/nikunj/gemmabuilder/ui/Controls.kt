package com.nikunj.gemmabuilder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Filled red button for confirming a delete. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) { Text(text) }
}

/** Outlined red button for starting a delete. */
@Composable
fun DangerOutlinedButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) { Text(text) }
}

/**
 * A slider that only moves when the thumb itself is dragged. Taps on the track and touches that
 * start elsewhere are ignored, so scrolling a settings page can't change values by accident.
 * With [steps] > 0 the value snaps to evenly spaced stops, like Material's Slider.
 */
@Composable
fun ThumbOnlySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {}
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val density = LocalDensity.current
    val thumbRadius = with(density) { 10.dp.toPx() }
    val touchRadius = with(density) { 28.dp.toPx() }
    val trackHeight = with(density) { 4.dp.toPx() }

    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp)) {
        val width = with(density) { maxWidth.toPx() }
        val usable = (width - thumbRadius * 2).coerceAtLeast(1f)
        val span = valueRange.endInclusive - valueRange.start

        fun fractionOf(v: Float) = if (span == 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f)

        fun valueAt(x: Float): Float {
            var fraction = ((x - thumbRadius) / usable).coerceIn(0f, 1f)
            if (steps > 0) {
                val segments = steps + 1
                fraction = (fraction * segments).roundToInt() / segments.toFloat()
            }
            return valueRange.start + fraction * span
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(width, steps, valueRange) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val thumbX = thumbRadius + fractionOf(currentValue) * usable
                        // Not on the thumb: leave the touch for scrolling.
                        if (abs(down.position.x - thumbX) > touchRadius) return@awaitEachGesture
                        down.consume()
                        drag(down.id) { change ->
                            change.consume()
                            val next = valueAt(change.position.x)
                            if (next != currentValue) currentOnChange(next)
                        }
                        currentOnFinished()
                    }
                }
        ) {
            val y = size.height / 2
            val thumbX = thumbRadius + fractionOf(value) * usable
            drawLine(inactiveColor, Offset(thumbRadius, y), Offset(size.width - thumbRadius, y), trackHeight, StrokeCap.Round)
            drawLine(activeColor, Offset(thumbRadius, y), Offset(thumbX, y), trackHeight, StrokeCap.Round)
            if (steps > 0) {
                for (i in 0..steps + 1) {
                    drawCircle(tickColor, radius = trackHeight / 2, center = Offset(thumbRadius + usable * i / (steps + 1), y))
                }
            }
            drawCircle(activeColor, radius = thumbRadius, center = Offset(thumbX, y))
        }
    }
}

/** Compact "context used / window" readout shown above the chat input whenever a model is selected. */
@Composable
fun ContextMeter(state: BuilderUiState, modifier: Modifier = Modifier) {
    val window = state.contextWindowTokens ?: return
    val used = (state.contextUsedTokens ?: 0).coerceIn(0, window)
    val fraction = used.toFloat() / window
    val nearlyFull = fraction >= 0.8f
    val color = if (nearlyFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Context ${compactTokens(used)} / ${compactTokens(window)} · ${compactTokens(window - used)} left",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                when {
                    nearlyFull -> "Older chat will be summarized"
                    state.chatSummarized -> "Earlier chat summarized"
                    else -> "${(fraction * 100).roundToInt()}%"
                },
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = if (nearlyFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {}
        )
    }
}

/** 3210 -> "3.2K", 16384 -> "16K". */
fun compactTokens(tokens: Int): String = when {
    tokens >= 10_000 -> "${tokens / 1000}K"
    tokens >= 1000 -> String.format(java.util.Locale.US, "%.1fK", tokens / 1000.0)
    else -> tokens.toString()
}

/**
 * Context window picker (4K up to [maxTokens]). Follows the thumb while dragging and reports only on
 * release. [ramEstimate] gives the approximate memory used at a window size, when it is known.
 */
@Composable
fun ContextSlider(
    tokens: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxTokens: Int = 32768,
    ramEstimate: ((Int) -> Long?)? = null
) {
    val options = CONTEXT_TOKEN_OPTIONS.filter { it <= maxTokens }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    val savedIndex = options.indexOf(tokens).let { if (it >= 0) it else options.indexOfLast { o -> o <= tokens }.coerceAtLeast(0) }
    val shown = options[draggingIndex ?: savedIndex]
    Column(modifier) {
        Text("Context window: ${shown / 1024}K tokens", style = MaterialTheme.typography.labelLarge)
        ramEstimate?.invoke(shown)?.let { bytes ->
            Text(
                "About ${String.format(java.util.Locale.US, "%.1f", bytes / 1_073_741_824.0)} GB of RAM at this size",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "How much of a chat the model remembers at once. Bigger uses more memory; if the phone runs out, it is lowered automatically.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ThumbOnlySlider(
            value = (draggingIndex ?: savedIndex).toFloat(),
            onValueChange = { raw -> draggingIndex = kotlin.math.round(raw).toInt().coerceIn(0, options.lastIndex) },
            onValueChangeFinished = {
                draggingIndex?.let { onChange(options[it]) }
                draggingIndex = null
            },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = options.size - 2
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            options.forEach { value ->
                Text(
                    if (value >= 1024 * 1024) "${value / (1024 * 1024)}M" else "${value / 1024}K",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (value == shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
