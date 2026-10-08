package com.foldpod.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.util.Locale

/** Only artwork supplied by the session; recording IDs and playback URIs are never cover sources. */
@Composable
internal fun QueueArtwork(bitmap: Bitmap?, uri: String?, modifier: Modifier = Modifier) {
    val square = modifier.aspectRatio(1f)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = square,
        )
        return
    }
    val model = remember(uri) {
        uri?.takeIf { supplied ->
            Uri.parse(supplied).scheme?.lowercase(Locale.ROOT) in setOf("https", "content", "android.resource")
        }
    }
    val placeholder = remember { MusicArtworkPlaceholder() }
    // AsyncImage resolves the decode size from these square constraints and shares Coil's bounded caches.
    AsyncImage(
        model = model,
        contentDescription = null,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        modifier = square,
    )
}

private class MusicArtworkPlaceholder : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(Color(0xFFE5E5E5))
        val ink = Color(0xFF969696)
        drawLine(ink, Offset(size.width * 0.57f, size.height * 0.29f),
            Offset(size.width * 0.57f, size.height * 0.66f), strokeWidth = size.width * 0.055f)
        drawLine(ink, Offset(size.width * 0.57f, size.height * 0.29f),
            Offset(size.width * 0.72f, size.height * 0.34f), strokeWidth = size.width * 0.055f)
        drawOval(ink, topLeft = Offset(size.width * 0.33f, size.height * 0.59f),
            size = Size(size.width * 0.27f, size.height * 0.16f))
    }
}
