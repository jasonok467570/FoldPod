package com.foldpod.app.ui

import android.media.AudioManager
import com.foldpod.app.media.NowPlayingState
import kotlin.math.max
import kotlin.math.min

internal fun currentVolumePercent(
    audio: AudioManager
): Int {

    val maxVolume =
        audio.getStreamMaxVolume(
            AudioManager.STREAM_MUSIC
        )

    if (maxVolume <= 0) {
        return 0
    }


    val currentVolume =
        audio.getStreamVolume(
            AudioManager.STREAM_MUSIC
        )


    return (
        currentVolume.toFloat() /
            maxVolume.toFloat() *
            100f
    )
        .toInt()
        .coerceIn(
            0,
            100,
        )
}


internal fun estimatedPosition(
    state: NowPlayingState,
    nowElapsed: Long,
): Long {

    val base =
        max(
            0L,
            state.positionMs,
        )


    if (
        !state.isPlaying ||
        state.updateElapsedRealtimeMs <=
        0L
    ) {

        return base
    }


    val elapsed =
        max(
            0L,

            nowElapsed -
                    state.updateElapsedRealtimeMs,
        )


    val estimate =
        base +
                (
                        elapsed *
                                state.playbackSpeed
                        )
                    .toLong()


    return if (
        state.durationMs > 0L
    ) {

        min(
            estimate,
            state.durationMs,
        )

    } else {

        max(
            0L,
            estimate,
        )
    }
}


internal fun formatTime(
    ms: Long
): String {

    val totalSeconds =
        max(
            0L,
            ms
        ) / 1_000L


    val minutes =
        totalSeconds /
                60L


    val seconds =
        totalSeconds %
                60L


    return "%d:%02d".format(
        minutes,
        seconds,
    )
}


internal fun mediaSourceName(
    packageName: String?
): String {

    return when (
        packageName
    ) {

        "com.spotify.music" ->
            "Spotify"

        "com.google.android.apps.youtube.music" ->
            "YouTube Music"

        "com.google.android.youtube" ->
            "YouTube"

        null ->
            "No source"

        else ->
            packageName
                .substringAfterLast(
                    '.'
                )
    }
}
