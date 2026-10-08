package com.foldpod.app.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt


@Composable
fun ClickWheel(
    modifier: Modifier = Modifier,

    wheelColor: Color,
    centerColor: Color,
    accentColor: Color,

    /*
     * 한 step으로 인식할 원형 회전 각도.
     * Settings slider(1~10)에서 계산된 값을 전달한다.
     */
    stepDegrees: Float = 13f,

    wheelTickHapticsEnabled: Boolean = true,
    buttonHapticsEnabled: Boolean = true,
    accelerationEnabled: Boolean = true,

    onWheelStep: (Int) -> Unit,

    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onCenter: () -> Unit,

    onInteraction: () -> Unit,
    controlsLocked: Boolean = false,
    onMenuLongPress: (() -> Unit)? = null,
    onCenterLongPress: (() -> Unit)? = null,
    onSeekHoldStep: ((Int) -> Unit)? = null,
) {

    val view = LocalView.current

    val currentWheelStep by rememberUpdatedState(onWheelStep)
    val currentMenu by rememberUpdatedState(onMenu)
    val currentPrevious by rememberUpdatedState(onPrevious)
    val currentNext by rememberUpdatedState(onNext)
    val currentPlayPause by rememberUpdatedState(onPlayPause)
    val currentCenter by rememberUpdatedState(onCenter)
    val currentInteraction by rememberUpdatedState(onInteraction)
    val currentLocked by rememberUpdatedState(controlsLocked)
    val currentMenuLongPress by rememberUpdatedState(onMenuLongPress)
    val currentCenterLongPress by rememberUpdatedState(onCenterLongPress)
    val currentSeekHoldStep by rememberUpdatedState(onSeekHoldStep)

    val currentWheelHaptics by rememberUpdatedState(wheelTickHapticsEnabled)
    val currentButtonHaptics by rememberUpdatedState(buttonHapticsEnabled)
    val currentAccelerationEnabled by rememberUpdatedState(accelerationEnabled)

    var pressedControl by remember {
        mutableStateOf(WheelControl.NONE)
    }

    val centerScale by animateFloatAsState(
        targetValue =
            if (pressedControl == WheelControl.CENTER) 0.94f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "centerButtonScale",
    )

    val wheelScale by animateFloatAsState(
        targetValue =
            if (
                pressedControl != WheelControl.NONE &&
                pressedControl != WheelControl.CENTER
            ) 0.992f else 1f,
        animationSpec = tween(durationMillis = 70),
        label = "wheelPressScale",
    )

    val iconColor =
        lerp(
            accentColor,
            Color.White,
            0.55f,
        )

    val safeStepDegrees =
        stepDegrees.coerceIn(
            4f,
            30f,
        )


    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = wheelScale
                    scaleY = wheelScale
                }
                .pointerInput(
                    safeStepDegrees,
                    wheelTickHapticsEnabled,
                    buttonHapticsEnabled,
                    accelerationEnabled,
                ) {

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        currentInteraction()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outer = min(size.width, size.height).toFloat() / 2f
                        val inner = outer * 0.35f
                        val start = down.position
                        val startRadius = distance(start, center)
                        val initialControl = when {
                            startRadius > outer * 1.08f -> WheelControl.NONE
                            startRadius <= inner -> WheelControl.CENTER
                            else -> controlForPoint(start, center)
                        }
                        val lockedAtDown = currentLocked
                        val longEnabled = when (initialControl) {
                            WheelControl.MENU -> currentMenuLongPress != null
                            WheelControl.CENTER -> currentCenterLongPress != null
                            WheelControl.PREVIOUS, WheelControl.NEXT -> currentSeekHoldStep != null
                            else -> false
                        }
                        val press = WheelPressPolicy(initialControl, lockedAtDown, longEnabled)
                        pressedControl = if (!lockedAtDown || initialControl == WheelControl.MENU) {
                            initialControl
                        } else WheelControl.NONE
                        val startedAt = SystemClock.uptimeMillis()
                        var lastAngle = angle(start, center)
                        var accumulated = 0f
                        var totalTravel = 0f
                        var lastPos = start
                        var rotating = false
                        var holdStarted = false
                        var cancelled = down.isConsumed || initialControl == WheelControl.NONE
                        val stepRadians = Math.toRadians(safeStepDegrees.toDouble()).toFloat()
                        var lastStepTimeMs = 0L
                        var rapidStepStreak = 0

                        fun haptic() {
                            if (currentButtonHaptics) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                        fun dispatchHold(action: WheelPressAction) {
                            when (action) {
                                WheelPressAction.LONG_PRESS -> {
                                    holdStarted = true
                                    currentInteraction()
                                    if (initialControl == WheelControl.MENU) currentMenuLongPress?.invoke()
                                    else currentCenterLongPress?.invoke()
                                    haptic()
                                }
                                WheelPressAction.SEEK_STEP -> {
                                    holdStarted = true
                                    currentInteraction()
                                    currentSeekHoldStep?.invoke(if (initialControl == WheelControl.PREVIOUS) -1 else 1)
                                }
                                else -> Unit
                            }
                        }

                        try {
                            while (!cancelled) {
                                // AwaitPointerEventScope's timeout keeps this restricted coroutine legal.
                                val event = withTimeoutOrNull(50L) { awaitPointerEvent() }
                                if (event == null) {
                                    if (currentLocked != lockedAtDown && !(holdStarted && initialControl == WheelControl.MENU)) break
                                    dispatchHold(press.advance(SystemClock.uptimeMillis() - startedAt))
                                    if (holdStarted) currentInteraction()
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) {
                                    cancelled = true
                                    break
                                }
                                val pos = change.position
                                totalTravel += distance(pos, lastPos)
                                lastPos = pos
                                val radius = distance(pos, center)
                                val sameButton = if (initialControl == WheelControl.CENTER) radius <= inner
                                    else radius >= inner && radius <= outer * 1.08f && controlForPoint(pos, center) == initialControl
                                if (holdStarted && (!sameButton || totalTravel > outer * 0.09f)) {
                                    cancelled = true
                                    break
                                }
                                if (currentLocked != lockedAtDown && !(holdStarted && initialControl == WheelControl.MENU)) {
                                    cancelled = true
                                    break
                                }
                                if (!change.pressed) {
                                    if (totalTravel > outer * 0.09f) press.cancel()
                                    // A release arriving after the deadline is still a hold even without a timer tick.
                                    if (sameButton && !rotating) dispatchHold(press.advance(SystemClock.uptimeMillis() - startedAt))
                                    if (press.release() == WheelPressAction.SHORT_PRESS && sameButton && !currentLocked) {
                                        when (initialControl) {
                                            WheelControl.CENTER -> currentCenter()
                                            WheelControl.MENU -> currentMenu()
                                            WheelControl.PREVIOUS -> currentPrevious()
                                            WheelControl.NEXT -> currentNext()
                                            WheelControl.PLAY_PAUSE -> currentPlayPause()
                                            else -> Unit
                                        }
                                        haptic()
                                    }
                                    change.consume()
                                    break
                                }
                                currentInteraction()
                                if (lockedAtDown) {
                                    if (!sameButton || totalTravel > outer * 0.09f) cancelled = true
                                    else dispatchHold(press.advance(SystemClock.uptimeMillis() - startedAt))
                                    change.consume()
                                    continue
                                }
                                if (initialControl == WheelControl.CENTER) {
                                    if (!sameButton || totalTravel > outer * 0.09f) cancelled = true
                                    else dispatchHold(press.advance(SystemClock.uptimeMillis() - startedAt))
                                    change.consume()
                                    continue
                                }
                                if (!sameButton || totalTravel > outer * 0.09f) {
                                    press.cancel()
                                    rotating = true
                                    pressedControl = WheelControl.NONE
                                }
                                if (!holdStarted && radius >= inner && radius <= outer * 1.08f) {
                                    val currentAngle = angle(pos, center)
                                    var delta = currentAngle - lastAngle
                                    if (delta > PI) delta -= (2 * PI).toFloat()
                                    if (delta < -PI) delta += (2 * PI).toFloat()
                                    lastAngle = currentAngle
                                    accumulated += delta
                                    while (true) {
                                        val rotationStep = consumeWheelRotationStep(accumulated, stepRadians) ?: break
                                        accumulated = rotationStep.remainingRadians
                                        rotating = true
                                        press.cancel()
                                        pressedControl = WheelControl.NONE
                                        val direction = rotationStep.direction

                                        val nowMs =
                                            SystemClock.uptimeMillis()

                                        val deltaMs =
                                            if (lastStepTimeMs > 0L) {
                                                nowMs - lastStepTimeMs
                                            } else {
                                                Long.MAX_VALUE
                                            }

                                        rapidStepStreak =
                                            if (deltaMs < 95L) {
                                                (rapidStepStreak + 1)
                                                    .coerceAtMost(6)
                                            } else {
                                                0
                                            }

                                        val multiplier =
                                            if (!currentAccelerationEnabled) {
                                                1
                                            } else {
                                                when {
                                                    rapidStepStreak >= 5 -> 3
                                                    rapidStepStreak >= 2 -> 2
                                                    else -> 1
                                                }
                                            }

                                        repeat(multiplier) {
                                            currentWheelStep(
                                                direction
                                            )
                                        }

                                        lastStepTimeMs =
                                            nowMs

                                        if (currentWheelHaptics) {
                                            view.performHapticFeedback(
                                                HapticFeedbackConstants.CLOCK_TICK
                                            )
                                        }

                                    }
                                }
                                if (!rotating) dispatchHold(press.advance(SystemClock.uptimeMillis() - startedAt))
                                change.consume()
                            }
                        } finally {
                            press.cancel()
                            pressedControl = WheelControl.NONE
                        }
                    }
                }
    ) {

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            val radius =
                min(
                    size.width,
                    size.height
                ) / 2f

            drawCircle(
                color = wheelColor,
                radius = radius,
            )

            drawCircle(
                color =
                    lerp(
                        wheelColor,
                        Color.White,
                        0.035f,
                    ),
                radius = radius * 0.96f,
            )

            drawCircle(
                color = centerColor,
                radius = radius * 0.35f * centerScale,
            )

            drawCircle(
                color =
                    Color.White.copy(
                        alpha = 0.08f
                    ),
                radius = radius * 0.985f,
                style =
                    Stroke(
                        width = radius * 0.018f
                    ),
            )

            drawCircle(
                color =
                    accentColor.copy(
                        alpha =
                            if (
                                pressedControl ==
                                WheelControl.CENTER
                            ) 0.22f else 0.10f
                    ),
                radius =
                    radius * 0.35f * centerScale,
                style =
                    Stroke(
                        width = radius * 0.014f
                    ),
            )
        }


        Text(
            text = "MENU",
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
            color =
                iconColor.copy(
                    alpha =
                        if (
                            pressedControl ==
                            WheelControl.MENU
                        ) 0.52f else 1f
                ),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )


        Text(
            text = "◀◀",
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp),
            color =
                iconColor.copy(
                    alpha =
                        if (
                            pressedControl ==
                            WheelControl.PREVIOUS
                        ) 0.52f else 1f
                ),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )


        Text(
            text = "▶▶",
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
            color =
                iconColor.copy(
                    alpha =
                        if (
                            pressedControl ==
                            WheelControl.NEXT
                        ) 0.52f else 1f
                ),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )


        Text(
            text = "▶Ⅱ",
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            color =
                iconColor.copy(
                    alpha =
                        if (
                            pressedControl ==
                            WheelControl.PLAY_PAUSE
                        ) 0.52f else 1f
                ),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}


private fun controlForPoint(
    point: Offset,
    center: Offset,
): WheelControl {

    val dx =
        point.x - center.x

    val dy =
        point.y - center.y

    return if (abs(dx) > abs(dy)) {
        if (dx < 0) {
            WheelControl.PREVIOUS
        } else {
            WheelControl.NEXT
        }
    } else {
        if (dy < 0) {
            WheelControl.MENU
        } else {
            WheelControl.PLAY_PAUSE
        }
    }
}


private fun distance(
    a: Offset,
    b: Offset,
): Float {

    val dx = a.x - b.x
    val dy = a.y - b.y

    return sqrt(
        dx * dx +
            dy * dy
    )
}


private fun angle(
    point: Offset,
    center: Offset,
): Float {

    return atan2(
        point.y - center.y,
        point.x - center.x,
    )
}
