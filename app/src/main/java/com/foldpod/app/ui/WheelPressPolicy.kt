package com.foldpod.app.ui

internal enum class WheelControl { NONE, CENTER, MENU, PREVIOUS, NEXT, PLAY_PAUSE }
internal enum class WheelPressAction { NONE, SHORT_PRESS, LONG_PRESS, SEEK_STEP }

/** One pointer's button lifecycle. Rotation/cancellation permanently disarms its button. */
internal class WheelPressPolicy(
    val control: WheelControl,
    private val locked: Boolean,
    private val longPressEnabled: Boolean,
) {
    private var cancelled = false
    private var held = false
    private var nextSeekAtMs = 450L

    fun cancel() { cancelled = true }

    fun advance(elapsedMs: Long): WheelPressAction {
        if (cancelled || !longPressEnabled || (locked && control != WheelControl.MENU)) {
            return WheelPressAction.NONE
        }
        return when (control) {
            WheelControl.PREVIOUS, WheelControl.NEXT -> {
                if (elapsedMs >= nextSeekAtMs) {
                    held = true
                    // Never catch up missed repeats with a burst of seek commands.
                    nextSeekAtMs = elapsedMs + 250L
                    WheelPressAction.SEEK_STEP
                } else WheelPressAction.NONE
            }
            WheelControl.MENU, WheelControl.CENTER -> {
                val threshold = if (control == WheelControl.MENU) 1_000L else 650L
                if (!held && elapsedMs >= threshold) {
                    held = true
                    WheelPressAction.LONG_PRESS
                } else WheelPressAction.NONE
            }
            else -> WheelPressAction.NONE
        }
    }

    fun release(): WheelPressAction {
        val result = if (!cancelled && !held && !locked && control != WheelControl.NONE) {
            WheelPressAction.SHORT_PRESS
        } else WheelPressAction.NONE
        cancelled = true
        return result
    }
}
