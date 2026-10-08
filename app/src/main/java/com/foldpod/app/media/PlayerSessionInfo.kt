package com.foldpod.app.media

data class PlayerSessionInfo(
    val packageName: String,
    val appName: String,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val isSelected: Boolean = false,
    val hasSession: Boolean = true,
)
