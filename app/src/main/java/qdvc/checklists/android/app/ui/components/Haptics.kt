package qdvc.checklists.android.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Short tactile confirmations for the actions that change what the app is showing.
 *
 * Routed through [View.performHapticFeedback] rather than the [android.os.Vibrator]
 * for two reasons: it honours the system's own touch-feedback setting, so a person
 * who has turned haptics off is not overridden, and it needs no `VIBRATE`
 * permission. It also degrades quietly on a device with no vibrator.
 *
 * Get one with [rememberHaptics].
 */
class Haptics(private val view: View) {

    /** A light tick for a tap that navigates or commits something. */
    fun tap() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /**
     * The moment a dragged item is picked up. Uses the platform's gesture-start
     * effect where available, falling back to the long-press effect — which is
     * apt anyway, since the drag begins on a long press.
     */
    fun pickUp() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    )

    /** The moment a dragged item is released. */
    fun drop() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
    )

    private fun perform(constant: Int) {
        // Deliberately without FLAG_IGNORE_GLOBAL_SETTING: if haptics are off,
        // they stay off.
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
