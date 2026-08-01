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

    // Roughly, the platform maps these to three intensities:
    //   CONTEXT_CLICK / CLOCK_TICK -> a faint tick
    //   VIRTUAL_KEY / KEYBOARD_TAP -> a normal click, as used for button presses
    //   LONG_PRESS                 -> a heavy click
    // If a sensation below needs more or less weight, move it a rung.

    /**
     * A tap that navigates or commits something. Uses the normal button-press
     * click rather than the faint tick, which was too easy to miss.
     */
    fun tap() = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    /**
     * One notch of a continuous gesture — an item passing its neighbour while
     * being dragged, or a swipe crossing the point where releasing would act.
     * Deliberately the faintest effect available, since it repeats.
     */
    fun step() = perform(HapticFeedbackConstants.CLOCK_TICK)

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

    /**
     * An action going through: a confirmation accepted, or a mode entered. Weighty
     * enough to distinguish committing from merely tapping.
     */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    )

    /** Work being thrown away — discarding a draft, or a refusal. */
    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
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
