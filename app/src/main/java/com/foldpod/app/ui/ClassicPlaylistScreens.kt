package com.foldpod.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldpod.app.media.NowPlayingState
import com.foldpod.app.library.YouTubeLibraryRepository
import kotlinx.coroutines.flow.first

internal val ClassicSelectionBlue = Color(0xFF1769D2)
private val ClassicText = Color(0xFF111111)

internal data class PlaylistSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val artwork: Bitmap?,
    val trackCount: Int,
    val artworkUri: String? = null,
)

internal data class PlaylistProviderSummary(
    val id: String,
    val title: String,
    val playlistCount: Int,
)

internal fun playlistProviderId(packageName: String?): String? = when (packageName) {
    null -> null
    "com.spotify.music" -> "spotify"
    "com.google.android.apps.youtube.music" -> YouTubeLibraryRepository.PROVIDER_ID
    else -> packageName
}

@Composable
internal fun ClassicPlaylistProvidersScreen(providers: List<PlaylistProviderSummary>, selectedIndex: Int) {
    val listState = rememberLazyListState()
    val safeIndex = selectedIndex.coerceIn(0, providers.lastIndex.coerceAtLeast(0))
    ClassicKeepSelectionVisible(listState, safeIndex, providers.size)
    ClassicLcd("Playlists") {
        if (providers.isEmpty()) {
            ClassicEmptyMessage("Link accounts in Settings → Connections, or open a music player.")
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(providers.size, key = { providers[it].id }) { index ->
                    val provider = providers[index]
                    ClassicListRow(provider.title, "${provider.playlistCount} playlists", index == safeIndex, trailing = "›")
                }
            }
            ClassicListFooter("${safeIndex + 1} of ${providers.size}")
        }
    }
}

@Composable
internal fun ClassicPlaylistBrowserScreen(
    playlists: List<PlaylistSummary>,
    selectedIndex: Int,
    sourceName: String,
    scopeLabel: String? = null,
) {
    val listState = rememberLazyListState()
    val safeIndex = selectedIndex.coerceIn(0, (playlists.size - 1).coerceAtLeast(0))
    ClassicKeepSelectionVisible(listState, safeIndex, playlists.size)
    ClassicLcd(title = "Playlists", sourceName = sourceName) {
        if (playlists.isEmpty()) {
            ClassicEmptyMessage("No playlists available from this player.")
        } else {
            val selected = playlists[safeIndex]
            ClassicSplitPane(
                previewArtwork = selected.artwork,
                previewArtworkUri = selected.artworkUri,
                previewTitle = selected.title,
                previewSubtitle = "${selected.trackCount} tracks",
            ) {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(playlists.size, key = { playlists[it].id }) { index ->
                            val playlist = playlists[index]
                            ClassicListRow(
                                title = playlist.title,
                                subtitle = playlist.subtitle.ifBlank { "${playlist.trackCount} tracks" },
                                selected = index == safeIndex,
                                artwork = playlist.artwork,
                                artworkUri = playlist.artworkUri,
                                showArtwork = true,
                                trailing = "›",
                            )
                        }
                    }
                    ClassicListFooter(scopeLabel ?: "${safeIndex + 1} of ${playlists.size}")
                }
            }
        }
    }
}

@Composable
internal fun ClassicPlaylistTracksScreen(
    state: NowPlayingState,
    selectedIndex: Int,
    theme: AlbumTheme,
) {
    val listState = rememberLazyListState()
    val safeIndex = selectedIndex.coerceIn(0, (state.queue.size - 1).coerceAtLeast(0))
    ClassicKeepSelectionVisible(listState, safeIndex, state.queue.size, state.queueRevision)
    ClassicLcd(title = state.queueTitle.ifBlank { "Current Playlist" }) {
        if (state.queue.isEmpty()) {
            ClassicEmptyMessage("This player isn't exposing its current queue.")
        } else {
            val item = state.queue[safeIndex]
            val selectedArtwork = item.artwork ?: state.artwork.takeIf { item.isActive && item.artworkUri.isNullOrBlank() }
            ClassicSplitPane(
                previewArtwork = selectedArtwork,
                previewArtworkUri = item.artworkUri,
                previewTitle = item.title,
                previewSubtitle = item.subtitle,
                previewAlbum = state.album.takeIf { item.isActive }.orEmpty(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(state.queue.size) { index ->
                            val track = state.queue[index]
                            ClassicListRow(
                                title = track.title,
                                subtitle = track.subtitle,
                                selected = index == safeIndex,
                                artwork = track.artwork ?: state.artwork.takeIf { track.isActive && track.artworkUri.isNullOrBlank() },
                                artworkUri = track.artworkUri,
                                showArtwork = true,
                                trailing = if (track.isActive) "▶" else "",
                            )
                        }
                    }
                    ClassicListFooter("${safeIndex + 1} of ${state.queue.size}")
                }
            }
        }
    }
}

/** Shared LCD and status header for every interactive page. */
@Composable
internal fun ClassicLcd(
    title: String,
    sourceName: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    ProvideTextStyle(LocalTextStyle.current.copy(
        fontFamily = ClassicFontFamily,
        fontWeight = FontWeight.Medium,
    )) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFFFAFAFA), Color(0xFFD1D1D1))))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClassicMarqueeText(title, color = ClassicText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                if (sourceName == "▶" || sourceName == "Ⅱ") {
                    Spacer(Modifier.width(6.dp))
                    Text(sourceName, color = Color(0xFF555555), fontSize = 9.sp,
                        maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                ClassicStatusIndicators()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF888888)))
            content()
        }
    }
}

@Composable
internal fun ColumnScope.ClassicSplitPane(
    previewArtwork: Bitmap?,
    previewTitle: String,
    previewSubtitle: String = "",
    previewArtworkUri: String? = null,
    previewAlbum: String = "",
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val textHeight = with(LocalDensity.current) {
            (13.sp * 1.25f).toDp() +
                (if (previewSubtitle.isNotBlank()) (11.sp * 1.25f).toDp() + 2.dp else 0.dp) +
                (if (previewAlbum.isNotBlank()) (11.sp * 1.25f).toDp() + 2.dp else 0.dp)
        }
        val showPreview = maxWidth >= 300.dp && maxHeight >= maxOf(140.dp, textHeight + 28.dp)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(if (showPreview) 0.58f else 1f).fillMaxHeight()) { content() }
            if (showPreview) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFD0D0D0)))
                BoxWithConstraints(Modifier.weight(0.42f).fillMaxHeight()) {
                    val artworkSize = minOf(maxWidth - 18.dp, maxHeight - textHeight - 28.dp).coerceAtLeast(0.dp)
                    Column(
                        Modifier.fillMaxSize().padding(9.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ClassicArtwork(previewArtwork, previewArtworkUri, Modifier.size(artworkSize))
                        Spacer(Modifier.height(6.dp))
                        ClassicMarqueeText(previewTitle, color = ClassicText, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        if (previewAlbum.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            ClassicMarqueeText(previewAlbum, color = Color(0xFF666666), fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                        if (previewSubtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            ClassicMarqueeText(previewSubtitle, color = Color(0xFF666666), fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClassicListRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    artwork: Bitmap? = null,
    showArtwork: Boolean = false,
    trailing: String = "",
    artworkUri: String? = null,
) {
    val rowHeight = with(LocalDensity.current) {
        maxOf(40.dp, (15.sp * 1.25f).toDp() +
            (if (!subtitle.isNullOrBlank()) (11.sp * 1.25f).toDp() else 0.dp) + 10.dp)
    }
    Row(
        Modifier.fillMaxWidth().height(rowHeight)
            .background(if (selected) ClassicSelectionBlue else Color.White)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            ClassicArtwork(artwork, artworkUri, Modifier.size(32.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            ClassicMarqueeText(title, color = if (selected) Color.White else ClassicText, fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(), enabled = selected)
            if (!subtitle.isNullOrBlank()) {
                ClassicMarqueeText(subtitle, color = if (selected) Color.White.copy(alpha = 0.85f) else Color(0xFF666666),
                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(), enabled = selected)
            }
        }
        if (trailing.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(trailing, color = if (selected) Color.White else ClassicText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ClassicArtwork(artwork: Bitmap?, artworkUri: String?, modifier: Modifier) {
    QueueArtwork(
        bitmap = artwork,
        uri = artworkUri,
        modifier = modifier.background(Color(0xFFE4E4E4)).border(1.dp, Color(0xFFC0C0C0)),
    )
}

@Composable
internal fun ClassicListFooter(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F1F1)).padding(horizontal = 6.dp, vertical = 3.dp),
        color = Color(0xFF555555), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ColumnScope.ClassicEmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().weight(1f).padding(14.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF666666), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ClassicKeepSelectionVisible(
    listState: LazyListState,
    selectedIndex: Int,
    rowCount: Int,
    revision: Long = 0L,
) {
    LaunchedEffect(listState, selectedIndex, rowCount, revision) {
        if (rowCount == 0) return@LaunchedEffect
        val layout = snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (visible == null || visible.offset < layout.viewportStartOffset ||
            visible.offset + visible.size > layout.viewportEndOffset) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
}
