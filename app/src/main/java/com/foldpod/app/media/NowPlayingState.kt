package com.foldpod.app.media

import android.graphics.Bitmap

data class NowPlayingState(
    val packageName: String? = null,

    val title: String = "Nothing playing",
    val artist: String = "Start a music app",
    val album: String = "",

    val artwork: Bitmap? = null,

    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val updateElapsedRealtimeMs: Long = 0L,
    val playbackSpeed: Float = 0f,

    val isPlaying: Boolean = false,
    val hasSession: Boolean = false,
    val canSeek: Boolean = false,
    val customActions: List<MediaSessionAction> = emptyList(),

    val sessionGeneration: Long = 0L,
    val queueRevision: Long = 0L,
    val queueTitle: String = "",
    val queue: List<QueueItem> = emptyList(),
)
