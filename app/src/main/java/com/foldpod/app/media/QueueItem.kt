package com.foldpod.app.media

import android.graphics.Bitmap

data class QueueItem(
    val id: Long,
    val title: String,
    val subtitle: String = "",
    val artwork: Bitmap? = null,
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val isActive: Boolean = false,
    val artworkUri: String? = null,
)

fun QueueItem.identity() = QueueRowIdentity(id, mediaId, mediaUri, title, subtitle)
