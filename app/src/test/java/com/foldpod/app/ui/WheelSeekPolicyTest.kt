package com.foldpod.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelSeekPolicyTest {
    @Test fun noWheelMovementFollowsPlaybackClock() {
        assertEquals(10_000L, WheelSeekPolicy.preview(10_000L, 0L, 60_000L))
        assertEquals(12_000L, WheelSeekPolicy.preview(12_000L, 0L, 60_000L))
    }

    @Test fun wheelMovementAccumulatesRelativeToLiveClock() {
        val first = WheelSeekPolicy.accumulate(0L, 5_000L, 10_000L, 60_000L)
        val second = WheelSeekPolicy.accumulate(first, 5_000L, 11_000L, 60_000L)
        assertEquals(10_000L, second)
        assertEquals(22_000L, WheelSeekPolicy.preview(12_000L, second, 60_000L))
    }

    @Test fun oppositeWheelStepsCancelToNoPendingSeek() {
        val pending = WheelSeekPolicy.accumulate(0L, 5_000L, 10_000L, 60_000L)
        assertEquals(0L, WheelSeekPolicy.accumulate(pending, -5_000L, 10_400L, 60_000L))
    }

    @Test fun forwardMovementIsBoundedByDuration() {
        assertEquals(2_000L, WheelSeekPolicy.accumulate(0L, 5_000L, 58_000L, 60_000L))
        assertEquals(60_000L, WheelSeekPolicy.preview(59_000L, 2_000L, 60_000L))
        assertEquals(0L, WheelSeekPolicy.accumulate(0L, 5_000L, 60_000L, 60_000L))
    }

    @Test fun backwardsMovementIsBoundedByStart() {
        assertEquals(-2_000L, WheelSeekPolicy.accumulate(0L, -5_000L, 2_000L, 60_000L))
        assertEquals(0L, WheelSeekPolicy.preview(1_000L, -2_000L, 60_000L))
        assertEquals(0L, WheelSeekPolicy.accumulate(0L, -5_000L, 0L, 60_000L))
    }

    @Test fun unknownDurationStillAllowsForwardSeek() {
        assertEquals(5_000L, WheelSeekPolicy.accumulate(0L, 5_000L, 60_000L, 0L))
        assertEquals(66_000L, WheelSeekPolicy.preview(61_000L, 5_000L, 0L))
    }

    @Test fun configuredStepUsesWholeSeconds() {
        for (seconds in listOf(1, 5, 60)) {
            assertEquals(seconds * 1_000L,
                WheelSeekPolicy.accumulate(0L, seconds * 1_000L, 100_000L, 300_000L))
        }
    }

    @Test fun clearingAfterCommitKeepsClockWithoutReplayingDelta() {
        val pending = WheelSeekPolicy.accumulate(0L, 5_000L, 10_000L, 60_000L)
        assertEquals(15_400L, WheelSeekPolicy.preview(10_400L, pending, 60_000L))
        assertEquals(16_000L, WheelSeekPolicy.preview(16_000L, 0L, 60_000L))
    }
}
