package com.foldpod.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WheelRotationPolicyTest {
    @Test fun rotationConsumesFiniteStepsInBothDirectionsAndKeepsRemainder() {
        val step = 0.25f
        for (sign in listOf(-1, 1)) {
            var accumulated = sign * 0.875f
            val directions = mutableListOf<Int>()
            // Hard bound fails instead of hanging if actual UI consumption regresses.
            repeat(10) {
                val consumed = consumeWheelRotationStep(accumulated, step) ?: return@repeat
                assertTrue(abs(consumed.remainingRadians) < abs(accumulated))
                accumulated = consumed.remainingRadians
                directions += consumed.direction
            }
            assertEquals(listOf(sign, sign, sign), directions)
            assertEquals(sign * 0.125f, accumulated, 0f)
            assertNull(consumeWheelRotationStep(accumulated, step))
        }
    }

    @Test fun exactOneStepCannotRepeatCallbackWithoutMoreRotation() {
        for (sign in listOf(-1, 1)) {
            val consumed = consumeWheelRotationStep(sign * 0.25f, 0.25f)!!
            assertEquals(sign, consumed.direction)
            assertEquals(0f, consumed.remainingRadians, 0f)
            assertNull(consumeWheelRotationStep(consumed.remainingRadians, 0.25f))
            val next = consumeWheelRotationStep(consumed.remainingRadians + sign * 0.25f, 0.25f)!!
            assertEquals(sign, next.direction)
        }
    }

    @Test fun subthresholdAndNonfiniteMovementNeverEmitsStep() {
        assertNull(consumeWheelRotationStep(0.125f, 0.25f))
        assertNull(consumeWheelRotationStep(-0.125f, 0.25f))
        assertNull(consumeWheelRotationStep(Float.NaN, 0.25f))
        assertNull(consumeWheelRotationStep(Float.POSITIVE_INFINITY, 0.25f))
    }
}
