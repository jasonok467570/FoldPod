package com.foldpod.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldpod.app.media.NowPlayingState
import kotlin.math.max

@Composable
internal fun NowPlayingScreen(
    state: NowPlayingState,
    nowElapsed: Long,
    theme: AlbumTheme,
    controlMode: NowPlayingControlMode,
    seekPreviewMs: Long,
    seekStepSeconds: Int,
    controlMessage: String?,
) {
    val lcdTheme = theme.copy(accent = ClassicSelectionBlue)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Visual approximation from Apple's guide p.24: bar outer height ~5% of LCD.
        val barHeight = maxHeight * 0.05f
        val timeSize = (maxHeight.value * 0.06f).coerceIn(12f, 14f).sp
        val controlHeight = with(LocalDensity.current) {
            maxOf(64.dp, (timeSize * 1.25f).toDp() + 14.sp.toDp() + 14.sp.toDp() + 10.dp)
        }
        ClassicLcd(title = "Now Playing", sourceName = if (state.isPlaying) "▶" else "Ⅱ") {
            Column(Modifier.fillMaxWidth().weight(1f).padding(7.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val artworkSize = minOf(maxHeight, maxWidth * 0.42f)
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(state, Modifier.size(artworkSize))
                        Spacer(Modifier.width(12.dp))
                        TrackText(state, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Spacer(Modifier.height(10.dp))
                // One bounded slot retains exclusive normal/seek panels even with larger text.
                Column(Modifier.fillMaxWidth().height(controlHeight), verticalArrangement = Arrangement.Bottom) {
                    when (controlMode) {
                        NowPlayingControlMode.NORMAL -> Progress(state, nowElapsed, lcdTheme, barHeight, timeSize)
                        NowPlayingControlMode.SEEK -> SeekControlPanel(seekPreviewMs, state.durationMs, lcdTheme, barHeight, timeSize, seekStepSeconds)
                    }
                    Spacer(Modifier.height(3.dp))
                    ClassicMarqueeText(
                        text = controlMessage.orEmpty(),
                        color = lcdTheme.accent.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun Artwork(
    state: NowPlayingState,
    modifier: Modifier = Modifier,
) {

    val art =
        state.artwork


    Box(
        modifier =
            modifier
                .aspectRatio(1f)

                .clip(
                    RoundedCornerShape(
                        3.dp
                    )
                )

                .background(
                    Color.Black.copy(
                        alpha = 0.04f
                    )
                ),

        contentAlignment =
            Alignment.Center,
    ) {

        if (
            art != null
        ) {

            Image(
                bitmap =
                    art.asImageBitmap(),

                contentDescription =
                    "Album artwork",

                modifier =
                    Modifier.fillMaxSize(),

                contentScale =
                    ContentScale.Crop,
            )

        } else {

            Text(
                text =
                    "♪",

                color =
                    Color.Black.copy(
                        alpha = 0.40f
                    ),

                fontSize =
                    64.sp,
            )
        }
    }
}

@Composable
internal fun TrackText(state: NowPlayingState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val hasAlbum = state.album.isNotBlank()
        // Keep every metadata line inside the space above the fixed control slot,
        // including when system font scaling increases their measured height.
        val preferredHeightPx = with(density) {
            23.sp.toPx() + 18.sp.toPx() + 3.dp.toPx() +
                if (hasAlbum) 17.sp.toPx() + 3.dp.toPx() else 0f
        }
        // Leave room for the individual lines rounding their heights to whole pixels.
        val availableHeightPx = (with(density) { maxHeight.toPx() } - 3f).coerceAtLeast(0f)
        val scale = (availableHeightPx / preferredHeightPx).coerceIn(0f, 1f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            ClassicMarqueeText(
                text = state.title,
                color = Color.Black,
                fontSize = (18f * scale).sp,
                lineHeight = (23f * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasAlbum) {
                Spacer(Modifier.height(3.dp * scale))
                ClassicMarqueeText(
                    text = state.album,
                    color = Color.Black.copy(alpha = 0.55f),
                    fontSize = (13f * scale).sp,
                    lineHeight = (17f * scale).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(3.dp * scale))
            ClassicMarqueeText(
                text = state.artist,
                color = Color.Black.copy(alpha = 0.65f),
                fontSize = (14f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SeekControlPanel(
    positionMs: Long,
    durationMs: Long,
    theme: AlbumTheme,
    barHeight: Dp = 10.dp,
    timeSize: TextUnit = 13.sp,
    seekStepSeconds: Int = 5,
) {
    val duration = max(durationMs, 0L)
    val position = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
    val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SEEK", color = theme.accent, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        ClassicMarqueeText("Wheel · ±${seekStepSeconds}s   Center · Confirm", color = Color(0xFF555555),
            fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    ClassicProgressRow(position, duration, fraction, theme, barHeight, timeSize)
}

@Composable
internal fun SimpleProgressBar(fraction: Float, theme: AlbumTheme, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Box(modifier.height(height).clip(RoundedCornerShape(3.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFFD4D4D4), Color(0xFFF3F3F3))))
        .border(1.dp, Color(0xFF999999), RoundedCornerShape(3.dp)).padding(1.dp)) {
        if (safeFraction > 0f) {
            Box(Modifier.fillMaxWidth(safeFraction).fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Color(0xFF7AB8FF), theme.accent))))
        }
    }
}

@Composable
internal fun Progress(
    state: NowPlayingState,
    nowElapsed: Long,
    theme: AlbumTheme,
    barHeight: Dp = 10.dp,
    timeSize: TextUnit = 13.sp,
) {
    val position = estimatedPosition(state, nowElapsed)
    val duration = max(state.durationMs, 0L)
    val targetFraction = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val progressFraction by animateFloatAsState(targetValue = targetFraction,
        animationSpec = tween(durationMillis = 850, easing = LinearEasing), label = "playbackProgress")
    ClassicProgressRow(position, duration, progressFraction, theme, barHeight, timeSize)
}

@Composable
private fun ClassicProgressRow(
    position: Long,
    duration: Long,
    fraction: Float,
    theme: AlbumTheme,
    barHeight: Dp,
    timeSize: TextUnit,
) {
    val remaining = max(duration - position, 0L)
    val elapsedText = formatTime(position)
    val remainingText = if (duration > 0L) "-${formatTime(remaining)}" else "0:00"
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val measureStyle = TextStyle(fontFamily = ClassicFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = timeSize, fontFeatureSettings = "tnum")
    val largestTimeWidth = maxOf(
        textMeasurer.measure(elapsedText, measureStyle, softWrap = false, maxLines = 1).size.width,
        textMeasurer.measure(remainingText, measureStyle, softWrap = false, maxLines = 1).size.width,
    ).coerceAtLeast(1)
    // Keep the bar visible at large system font scales or with hour-long recordings.
    val maxTimeWidth = with(LocalDensity.current) {
        (maxWidth.toPx() * 0.36f - 10.dp.toPx()).coerceAtLeast(2f) / 2f
    }
    val fittedTimeSize = with(LocalDensity.current) {
        (timeSize.toPx() * (maxTimeWidth / largestTimeWidth).coerceIn(0f, 1f)).toSp()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(elapsedText, color = Color(0xFF333333), fontSize = fittedTimeSize,
            fontFamily = ClassicFontFamily,
            lineHeight = fittedTimeSize * 1.25f, fontWeight = FontWeight.SemiBold, maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"))
        SimpleProgressBar(fraction, theme, Modifier.weight(1f), barHeight)
        Text(remainingText, color = Color(0xFF333333),
            fontFamily = ClassicFontFamily,
            fontSize = fittedTimeSize, lineHeight = fittedTimeSize * 1.25f, fontWeight = FontWeight.SemiBold, maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"))
    }
    }
}

@Composable
internal fun IdleScreen(
    state: NowPlayingState,
    nowElapsed: Long,
    pixelShiftEnabled: Boolean,
) {

    val shiftPattern =
        listOf(
            0 to 0,
            4 to -3,
            -3 to 4,
            3 to 3,
            -4 to -2,
        )

    val shift =
        if (pixelShiftEnabled) {
            shiftPattern[
                (
                    (nowElapsed / 300_000L) %
                        shiftPattern.size
                    ).toInt()
            ]
        } else {
            0 to 0
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .offset(
                    x = shift.first.dp,
                    y = shift.second.dp,
                ),

        verticalArrangement =
            Arrangement.Center,

        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {


        Text(
            text =
                java.time.LocalTime
                    .now()
                    .format(
                        java.time.format
                            .DateTimeFormatter
                            .ofPattern(
                                "HH:mm"
                            )
                    ),

            color =
                Color(
                    0xFF777777
                ),

            fontSize =
                28.sp,

            fontWeight =
                FontWeight.Light,
        )


        Spacer(
            Modifier.height(
                28.dp
            )
        )


        Text(
            text =
                state.title,

            color =
                Color(
                    0xFFBFBFBF
                ),

            fontSize =
                18.sp,

            fontWeight =
                FontWeight.Medium,

            maxLines =
                1,

            overflow =
                TextOverflow.Ellipsis,
        )


        Text(
            text =
                state.artist,

            color =
                Color(
                    0xFF666666
                ),

            fontSize =
                14.sp,

            maxLines =
                1,

            overflow =
                TextOverflow.Ellipsis,
        )


        Spacer(
            Modifier.height(
                18.dp
            )
        )


        Text(
            text =
                if (
                    state.isPlaying
                ) {

                    "♪"

                } else {

                    "Ⅱ"
                },

            color =
                Color(
                    0xFF777777
                ),

            fontSize =
                32.sp,
        )


        Spacer(
            Modifier.height(
                14.dp
            )
        )


        val duration =
            max(
                state.durationMs,
                0L,
            )


        val position =
            estimatedPosition(
                state,
                nowElapsed,
            )


        Text(
            text =
                "${formatTime(position)}  ·  ${formatTime(duration)}",

            color =
                Color(
                    0xFF555555
                ),

            fontSize =
                12.sp,
        )
    }
}
