package com.foldpod.app.ui

import kotlin.math.abs

internal data class WheelRotationStep(val direction: Int, val remainingRadians: Float)

/** Consumes exactly one angular step before the UI sends any volume/list callback. */
internal fun consumeWheelRotationStep(accumulatedRadians: Float, stepRadians: Float): WheelRotationStep? {
    require(stepRadians.isFinite() && stepRadians > 0f)
    if (!accumulatedRadians.isFinite() || abs(accumulatedRadians) < stepRadians) return null
    val direction = if (accumulatedRadians > 0f) 1 else -1
    return WheelRotationStep(direction, accumulatedRadians - direction * stepRadians)
}
