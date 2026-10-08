package com.foldpod.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelPressPolicyTest {
    @Test fun shortPressDoesNotTriggerHoldAndReleasesOnce() {
        val press = WheelPressPolicy(WheelControl.NEXT, false, true)
        assertEquals(WheelPressAction.NONE, press.advance(449))
        assertEquals(WheelPressAction.SHORT_PRESS, press.release())
        assertEquals(WheelPressAction.NONE, press.release())
    }

    @Test fun seekHoldRepeatsWithoutShortSkipOrCatchupBurst() {
        for (control in listOf(WheelControl.PREVIOUS, WheelControl.NEXT)) {
            val press = WheelPressPolicy(control, false, true)
            assertEquals(WheelPressAction.SEEK_STEP, press.advance(450))
            assertEquals(WheelPressAction.NONE, press.advance(699))
            assertEquals(WheelPressAction.SEEK_STEP, press.advance(700))
            assertEquals(WheelPressAction.SEEK_STEP, press.advance(10_000))
            assertEquals(WheelPressAction.NONE, press.advance(10_000))
            assertEquals(WheelPressAction.NONE, press.release())
        }
    }

    @Test fun menuHoldAndCenterHoldAreOneShotAndSuppressShortAction() {
        for ((control, threshold) in listOf(WheelControl.MENU to 1_000L, WheelControl.CENTER to 650L)) {
            val press = WheelPressPolicy(control, false, true)
            assertEquals(WheelPressAction.NONE, press.advance(threshold - 1))
            assertEquals(WheelPressAction.LONG_PRESS, press.advance(threshold))
            assertEquals(WheelPressAction.NONE, press.advance(threshold + 5_000))
            assertEquals(WheelPressAction.NONE, press.release())
        }
    }

    @Test fun cancelledPointerOrRotationCannotResumeButtonAction() {
        for (control in WheelControl.entries) {
            val press = WheelPressPolicy(control, false, true)
            press.cancel()
            assertEquals(WheelPressAction.NONE, press.advance(10_000))
            assertEquals(WheelPressAction.NONE, press.release())
        }
        val held = WheelPressPolicy(WheelControl.PREVIOUS, false, true)
        assertEquals(WheelPressAction.SEEK_STEP, held.advance(450))
        held.cancel()
        assertEquals(WheelPressAction.NONE, held.advance(700))
        assertEquals(WheelPressAction.NONE, held.release())
    }

    @Test fun lockedControlsOnlyAcceptLongMenuToUnlock() {
        for (control in WheelControl.entries) {
            val press = WheelPressPolicy(control, true, true)
            assertEquals(if (control == WheelControl.MENU) WheelPressAction.LONG_PRESS else WheelPressAction.NONE,
                press.advance(1_000))
            assertEquals(WheelPressAction.NONE, press.release())
        }
        assertEquals(WheelPressAction.NONE, WheelPressPolicy(WheelControl.MENU, true, true).release())
    }

    @Test fun omittedOptionalCallbackPreservesExistingButtonTap() {
        for (control in listOf(WheelControl.MENU, WheelControl.CENTER, WheelControl.PREVIOUS, WheelControl.NEXT)) {
            val press = WheelPressPolicy(control, false, false)
            assertEquals(WheelPressAction.NONE, press.advance(10_000))
            assertEquals(WheelPressAction.SHORT_PRESS, press.release())
        }
    }
}
