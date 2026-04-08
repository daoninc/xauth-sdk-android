package com.daon.fido.sdk.sample.kt.capture.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import com.daon.fido.sdk.sample.kt.R
import com.daon.sdk.voiceauthenticator.controller.VoiceController
import kotlin.random.Random

/**
 * UI for the voice capture process.
 *
 * @param onNavigateUp: Callback function to handle navigation when passcode capture is complete.
 * @param voiceController: The controller to use for the voice capture process.
 */
@Composable
fun VoiceScreen(onNavigateUp: () -> Unit, voiceController: VoiceController) {

    when {
        voiceController.configuration.isEnrol() -> {
            // Display the registration screen if in enrollment mode
            RegisterVoiceScreen(onNavigateUp, voiceController = voiceController)
        }
        else -> {
            // Display the authentication screen if in authentication mode
            AuthenticateVoiceScreen(onNavigateUp, voiceController = voiceController)
        }
    }
}

@Composable
fun VoiceVisualizer(modifier: Modifier = Modifier, isAnimating: Boolean) {
    val heightDivider by
        animateFloatAsState(
            targetValue = if (isAnimating) 1f else 6f,
            animationSpec = tween(1000, easing = LinearEasing),
        )
    val infiniteAnimation = rememberInfiniteTransition()
    val animations = mutableListOf<State<Float>>()
    val random = remember { Random(System.currentTimeMillis()) }

    repeat(15) {
        val durationMillis = random.nextInt(500, 2000)
        animations +=
            infiniteAnimation.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
    }
    val maxLinesCount = 50
    val initialMultipliers = remember {
        mutableListOf<Float>().apply { repeat(maxLinesCount) { this += random.nextFloat() } }
    }
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val canvasCenterY = canvasHeight / 2
        val barWidthFloat = 10F
        val gapWidthFloat = 5F

        // Calculate the number of bars that can be drawn on the canvas coerce to maxLinesCount(100)
        val count =
            (canvasWidth / (barWidthFloat + gapWidthFloat)).toInt().coerceAtMost(maxLinesCount)
        // calculate the startOffset for the first bar
        val animatedVolumeWidth = count * (barWidthFloat + gapWidthFloat)
        var startOffset = (canvasWidth - animatedVolumeWidth) / 2

        // barMinHeight when no audio
        val barMinHeight = 0f
        val barMaxHeight = canvasHeight / 2f / heightDivider

        repeat(count) { index ->
            val currentSize = animations[index % animations.size].value
            var barHeightPercent = initialMultipliers[index] + currentSize
            if (barHeightPercent > 1.0f) {
                val diff = barHeightPercent - 1.0f
                barHeightPercent = 1.0f - diff
            }
            val barHeight = lerpF(barMinHeight, barMaxHeight, barHeightPercent)
            drawLine(
                color = Color.Gray,
                start = Offset(startOffset, canvasCenterY - barHeight / 2),
                end = Offset(startOffset, canvasCenterY + barHeight / 2),
                strokeWidth = barWidthFloat,
                cap = StrokeCap.Round,
            )
            startOffset += barWidthFloat + gapWidthFloat
        }
    }
}

@Composable
fun PlayPauseButton(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    viewModel: VoiceViewModel,
    enabled: Boolean = true,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color =
                    when {
                        !enabled -> Color.Gray.copy(alpha = 0.4f)
                        isPlaying -> Color.Red
                        else -> Color.DarkGray
                    },
                radius = size.minDimension / 2,
            )
        }
        IconButton(
            onClick = { viewModel.toggleRecording() },
            modifier = Modifier.align(Alignment.Center),
            enabled = enabled,
        ) {
            Icon(
                painter =
                    if (isPlaying) painterResource(id = R.drawable.pause_24px)
                    else painterResource(id = R.drawable.play_arrow_24px),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

fun lerpF(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}
