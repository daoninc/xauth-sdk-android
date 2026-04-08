package com.daon.fido.sdk.sample.kt.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/** Maximum time in milliseconds between consecutive taps to count as a multi-tap. */
private const val TAP_TIMEOUT_MS = 500L

/** Maximum distance in pixels between press and release to count as a tap (not a drag). */
private const val TAP_SLOP_PX = 40f

/** Number of taps required to open the developer settings. */
private const val REQUIRED_TAPS = 2

/**
 * Wraps content with a triple-tap gesture detector that opens the [DevSettingsBottomSheet].
 *
 * The gesture detection uses [PointerEventPass.Initial] so that pointer events are observed but
 * **not consumed** — child composables (buttons, text fields, etc.) continue to receive all touch
 * events normally.
 *
 * @param devSettings Current settings loaded from prefs/config.
 * @param onServerSettingsSaveAndRestart Called with the edited settings when "Save & Restart" is
 *   tapped.
 * @param onImmediateSettingChange Called when a toggle/dropdown changes with the pref key and new
 *   value.
 * @param content The screen content to wrap.
 */
@Composable
fun DevSettingsWrapper(
    devSettings: DevSettings,
    onServerSettingsSaveAndRestart: (DevSettings) -> Unit,
    onImmediateSettingChange: (key: String, value: Any) -> Unit,
    content: @Composable () -> Unit,
) {
    var showDevSettings by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    // Track the press position to distinguish taps from drags
                    var pressX = 0f
                    var pressY = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)

                        // Only consider single-pointer events
                        if (event.changes.size != 1) continue
                        val change = event.changes.first()

                        when (event.type) {
                            PointerEventType.Press -> {
                                pressX = change.position.x
                                pressY = change.position.y
                            }
                            PointerEventType.Release -> {
                                // Check that the pointer didn't move far (i.e. it's a tap, not a
                                // drag)
                                val dx = change.position.x - pressX
                                val dy = change.position.y - pressY
                                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                if (distance < TAP_SLOP_PX) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < TAP_TIMEOUT_MS) {
                                        tapCount++
                                        if (tapCount >= REQUIRED_TAPS) {
                                            showDevSettings = true
                                            tapCount = 0
                                        }
                                    } else {
                                        // Timeout expired — this tap starts a new sequence
                                        tapCount = 1
                                    }
                                    lastTapTime = now
                                }
                            }
                        }
                        // Do NOT consume the event — children must still receive it
                    }
                }
            }
    ) {
        content()

        DevSettingsBottomSheet(
            isVisible = showDevSettings,
            onDismiss = { showDevSettings = false },
            devSettings = devSettings,
            onServerSettingsSaveAndRestart = { settings ->
                showDevSettings = false
                onServerSettingsSaveAndRestart(settings)
            },
            onImmediateSettingChange = onImmediateSettingChange,
        )
    }
}
