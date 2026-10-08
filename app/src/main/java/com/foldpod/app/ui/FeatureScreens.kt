package com.foldpod.app.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.foldpod.app.media.MediaActionSemantic
import com.foldpod.app.media.NowPlayingState
import com.foldpod.app.timer.SleepTimerState

@Composable
internal fun PlaybackOptionsScreen(
    state: NowPlayingState,
    selectedIndex: Int,
    theme: AlbumTheme,
) {
    val listState = rememberLazyListState()
    ClassicKeepSelectionVisible(listState, selectedIndex.coerceIn(0, 1), 2)
    val semantics = listOf(MediaActionSemantic.SHUFFLE, MediaActionSemantic.REPEAT)

    ClassicLcd(title = "Playback", sourceName = "Options") {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
        ) {
            items(2) { index ->
                val action = state.customActions.filter { it.semantic == semantics[index] }.singleOrNull()
                MenuRow(
                    title = if (index == 0) "Shuffle" else "Repeat",
                    subtitle = action?.label ?: "Unavailable",
                    selected = selectedIndex == index,
                    theme = theme,
                )
            }
        }
    }
}

@Composable
internal fun SleepTimerScreen(
    state: SleepTimerState,
    selectedIndex: Int,
    theme: AlbumTheme,
) {
    val listState = rememberLazyListState()
    ClassicKeepSelectionVisible(listState, selectedIndex.coerceIn(0, 4), 5)
    val remaining = timerRemainingText(state.remainingMs)

    ClassicLcd(title = "Sleep timer", sourceName = if (state.active) remaining else "Off") {
        if (!state.exactScheduling) {
            Text(
                text = "절전 상태에서는 종료가 늦어질 수 있습니다.",
                color = Color(0xFF666666),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        timerOutcomeText(state.outcome)?.let { outcome ->
            Text(
                text = outcome,
                color = Color(0xFF666666),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
        ) {
            items(5) { index ->
                MenuRow(
                    title = when (index) {
                        0 -> "15 min"
                        1 -> "30 min"
                        2 -> "60 min"
                        3 -> "Cancel timer"
                        else -> "Exact timer permission"
                    },
                    subtitle = when (index) {
                        0, 1, 2 -> "Pause selected player"
                        3 -> if (state.active) remaining else "No active timer"
                        else -> if (state.exactScheduling) "Enabled" else "Allow precise timing while asleep"
                    },
                    selected = selectedIndex == index,
                    theme = theme,
                )
            }
        }
    }
}

private fun timerRemainingText(remainingMs: Long): String {
    val milliseconds = remainingMs.coerceAtLeast(0L)
    val seconds = milliseconds / 1_000 + if (milliseconds % 1_000 == 0L) 0 else 1
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun timerOutcomeText(outcome: String?): String? = when (outcome) {
    "pause_requested" -> "Player pause requested"
    "player_unavailable" -> "Selected player unavailable"
    "schedule_unavailable" -> "Timer could not be set"
    else -> null
}
