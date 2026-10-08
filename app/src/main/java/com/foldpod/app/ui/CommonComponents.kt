package com.foldpod.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldpod.app.media.NowPlayingState
import com.foldpod.app.media.QueueItem

@Composable
internal fun MainMenuScreen(
    selectedIndex: Int,
    sourceName: String,
    playlistSubtitle: String,
    theme: AlbumTheme,
    state: NowPlayingState? = null,
) {
    val listState = rememberLazyListState()
    val safeIndex = selectedIndex.coerceIn(0, MAIN_MENU_ITEMS.lastIndex)
    val selected = MAIN_MENU_ITEMS[safeIndex]
    ClassicKeepSelectionVisible(listState, safeIndex, MAIN_MENU_ITEMS.size)
    ClassicLcd(title = "FoldPod", sourceName = sourceName) {
        ClassicSplitPane(
            previewArtwork = state?.artwork,
            previewTitle = if (selected.action == MainMenuAction.NOW_PLAYING) state?.title ?: selected.title else selected.title,
            previewAlbum = if (selected.action == MainMenuAction.NOW_PLAYING) state?.album.orEmpty() else "",
            previewSubtitle = when (selected.action) {
                MainMenuAction.NOW_PLAYING -> state?.artist.orEmpty()
                MainMenuAction.PLAYLIST -> playlistSubtitle
                MainMenuAction.PLAYER -> sourceName
                else -> ""
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(MAIN_MENU_ITEMS.size) { index ->
                        val item = MAIN_MENU_ITEMS[index]
                        ClassicListRow(
                            title = item.title,
                            selected = index == safeIndex,
                            trailing = "›",
                        )
                    }
                }
                ClassicListFooter(sourceName)
            }
        }
    }
}

@Composable
internal fun PlaylistScreen(
    queue: List<QueueItem>,
    queueTitle: String,
    selectedIndex: Int,
    theme: AlbumTheme,
) {
    val listState =
        rememberLazyListState()

    LaunchedEffect(
        selectedIndex,
        queue.size,
    ) {
        if (queue.isNotEmpty()) {

            val safeIndex =
                selectedIndex.coerceIn(
                    0,
                    queue.lastIndex,
                )

            val visibleItems =
                listState.layoutInfo
                    .visibleItemsInfo

            if (visibleItems.isEmpty()) {

                listState.scrollToItem(
                    safeIndex
                )

            } else {

                val firstVisible =
                    visibleItems.first().index

                val lastVisible =
                    visibleItems.last().index

                when {
                    safeIndex < firstVisible -> {

                        listState.animateScrollToItem(
                            safeIndex
                        )
                    }

                    safeIndex > lastVisible -> {

                        /*
                         * Keep the newly selected row near the bottom of the
                         * viewport instead of scrolling it all the way to the
                         * top. This prevents the list from appearing to run
                         * past the final track.
                         */
                        val visibleCount =
                            visibleItems.size
                                .coerceAtLeast(1)

                        val targetFirst =
                            (
                                safeIndex -
                                    visibleCount +
                                    1
                            )
                                .coerceAtLeast(0)

                        listState.animateScrollToItem(
                            targetFirst
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        MenuHeader(
            title = "Playlist",
            subtitle =
                when {
                    queueTitle.isNotBlank() ->
                        queueTitle
                    queue.isNotEmpty() ->
                        "${queue.size} tracks"
                    else ->
                        "Current queue"
                },
        )

        Spacer(
            Modifier.height(10.dp)
        )

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        "This player isn't exposing its current queue.",
                    color =
                        Color.White.copy(
                            alpha = 0.55f
                        ),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                items(
                    count = queue.size
                ) { index ->
                    QueueRow(
                        item = queue[index],
                        selected =
                            index == selectedIndex,
                        theme = theme,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MenuRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    theme: AlbumTheme,
) {
    ClassicListRow(title = title, subtitle = subtitle, selected = selected, trailing = "›")
}

@Composable
internal fun QueueRow(
    item: QueueItem,
    selected: Boolean,
    theme: AlbumTheme,
) {
    ClassicListRow(
        title = item.title,
        subtitle = item.subtitle,
        selected = selected,
        trailing = if (item.isActive) "▶" else if (selected) "›" else "",
    )
}

@Composable
internal fun MenuHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFF1F1F1)).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFF111111), fontSize = 15.sp, fontFamily = ClassicFontFamily,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Color(0xFF666666), fontSize = 11.sp,
            fontFamily = ClassicFontFamily, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
