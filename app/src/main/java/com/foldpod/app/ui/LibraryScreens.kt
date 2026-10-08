package com.foldpod.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldpod.app.library.ProviderLibraryState

@Composable
internal fun ConnectionsScreen(
    spotify: ProviderLibraryState,
    youtube: ProviderLibraryState,
    selectedIndex: Int,
    enabled: Boolean,
    onAction: (Int) -> Unit,
) {
    val rows = listOf(
        "Spotify Connect" to "Playlist access + playback control · Premium",
        "Spotify Refresh" to connectionLabel(spotify),
        "Spotify Disconnect" to "Remove saved connection",
        "YouTube Connect" to "Google account · read-only",
        "YouTube Refresh" to connectionLabel(youtube),
        "YouTube Disconnect" to "Clear account connection",
        "YouTube Add Playlist" to "Paste a shared YouTube / Music link",
    )
    val listState = rememberLazyListState()
    ClassicKeepSelectionVisible(listState, selectedIndex, rows.size)
    ClassicLcd("Connections") {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            items(rows.size) { index ->
                Box(Modifier.clickable(enabled = enabled) { onAction(index) }) {
                    ClassicListRow(rows[index].first, rows[index].second, selectedIndex == index)
                }
            }
        }
        LibraryErrors(spotify, youtube)
        ClassicListFooter("Wheel · Select   Center · Apply   MENU · Back")
    }
}

@Composable
internal fun LibraryPlaylistBrowserScreen(
    playlists: List<PlaylistSummary>,
    selectedIndex: Int,
    providerName: String,
    favorites: Set<String>,
    library: ProviderLibraryState?,
) {
    val listState = rememberLazyListState()
    val safeIndex = selectedIndex.coerceIn(0, playlists.lastIndex.coerceAtLeast(0))
    ClassicKeepSelectionVisible(listState, safeIndex, playlists.size)
    ClassicLcd(providerName) {
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(10.dp), contentAlignment = Alignment.Center) {
                Text(if (library?.loading == true) "Loading account playlists…" else
                    "No playlists available. Settings → Connections links your account.",
                    color = Color(0xFF666666), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            val selected = playlists[safeIndex]
            val countLabel = if (selected.id.startsWith("current:")) "${selected.trackCount} tracks" else selected.subtitle
            ClassicSplitPane(selected.artwork, selected.title, countLabel, selected.artworkUri) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(playlists.size, key = { playlists[it].id }) { index ->
                        val playlist = playlists[index]
                        ClassicListRow(playlist.title, playlist.subtitle, index == safeIndex,
                            artwork = playlist.artwork, artworkUri = playlist.artworkUri, showArtwork = true,
                            trailing = if (playlist.id in favorites) "★ ›" else "›")
                    }
                }
            }
        }
        library?.error?.let { message ->
            Text(message, Modifier.padding(horizontal = 6.dp), color = Color(0xFFB3261E), fontSize = 9.sp, maxLines = 2)
        }
        ClassicListFooter(if (playlists.getOrNull(safeIndex)?.id?.startsWith("current:") == false)
            "Hold Center · Favorite" else "Current queue · Center · Open")
    }
}

@Composable
internal fun LibraryTracksScreen(library: ProviderLibraryState, selectedIndex: Int, canOpenSelectedTrack: Boolean) {
    val listState = rememberLazyListState()
    val safeIndex = selectedIndex.coerceIn(0, library.tracks.lastIndex.coerceAtLeast(0))
    ClassicKeepSelectionVisible(listState, safeIndex, library.tracks.size)
    ClassicLcd(library.selectedPlaylist?.name ?: "Account Playlist") {
        if (library.tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(10.dp), contentAlignment = Alignment.Center) {
                Text(library.error ?: if (library.loading) "Loading playlist items…" else "No playlist items available.",
                    color = Color(0xFF666666), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            val selected = library.tracks[safeIndex]
            ClassicSplitPane(null, selected.name, selected.artist, selected.artworkUrl, selected.album) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    // Index keys preserve duplicate provider track IDs and unavailable positions.
                    items(library.tracks.size) { index ->
                        val track = library.tracks[index]
                        val detail = when {
                            track.isLocal -> "Local item · read-only"
                            !track.available -> "Unavailable item · read-only"
                            else -> listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
                        }
                        ClassicListRow(track.name, detail, index == safeIndex, artworkUri = track.artworkUrl, showArtwork = true)
                    }
                }
            }
        }
        ClassicMarqueeText(library.accountScopeNote, color = Color(0xFF666666), fontSize = 9.sp, lineHeight = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp))
        ClassicListFooter(if (canOpenSelectedTrack) "Center · Play" else
            if (library.tracks.isEmpty()) "Read-only · MENU · Back" else
            "${safeIndex + 1} of ${library.tracks.size} imported items · Read-only")
    }
}

@Composable
private fun LibraryErrors(vararg libraries: ProviderLibraryState) {
    val errors = libraries.mapNotNull { library ->
        library.error?.let { "${if (library.providerId == "spotify") "Spotify" else "YouTube"}: $it" }
    }
    if (errors.isNotEmpty()) {
        // One compact line reserves list space; marquee and text semantics expose full details.
        ClassicMarqueeText(errors.joinToString(" · "), color = Color(0xFFB3261E), fontSize = 9.sp, lineHeight = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun connectionLabel(library: ProviderLibraryState): String = when {
    library.loading -> "Loading…"
    library.connected -> "${library.playlists.size} playlists imported"
    else -> "Not connected"
}
