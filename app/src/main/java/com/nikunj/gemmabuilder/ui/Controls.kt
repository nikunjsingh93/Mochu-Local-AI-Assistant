package com.nikunj.gemmabuilder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
