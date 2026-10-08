package com.foldpod.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.foldpod.app.media.PlayerSessionInfo

@Composable
internal fun PlayerScreen(
    sessions: List<PlayerSessionInfo>,
    selectedIndex: Int,
    currentSourceName: String,
    theme: AlbumTheme,
) {
    val listState = rememberLazyListState()
    val rowCount = sessions.size + 1
    ClassicKeepSelectionVisible(listState, selectedIndex.coerceIn(0, rowCount - 1), rowCount)

    ClassicLcd(title = "Player", sourceName = currentSourceName) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        "No active media sessions.\nStart Spotify, YouTube Music,\nor another player first.",
                    color =
                        Color(0xFF666666),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
            ) {
                items(
                    count = sessions.size
                ) { index ->
                    val session =
                        sessions[index]

                    MenuRow(
                        title =
                            session.appName,
                        subtitle =
                            buildString {
                                append(
                                    if (!session.hasSession) {
                                        "Not ready · 앱에서 재생을 시작하세요"
                                    } else if (session.isPlaying) {
                                        "Playing"
                                    } else {
                                        "Paused"
                                    }
                                )

                                if (session.title.isNotBlank()) {
                                    append(" · ")
                                    append(session.title)
                                }
                            },
                        selected =
                            selectedIndex == index,
                        theme = theme,
                    )
                }

                item {
                    MenuRow(
                        title =
                            "Open current app",
                        subtitle =
                            currentSourceName,
                        selected =
                            selectedIndex == sessions.size,
                        theme = theme,
                    )
                }
            }
        }
    }
}
