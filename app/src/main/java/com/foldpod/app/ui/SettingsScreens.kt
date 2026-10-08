package com.foldpod.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldpod.app.BuildConfig
import com.foldpod.app.settings.FoldPodSettingsStore
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    selectedIndex: Int,
    wheelSensitivity: Int,
    seekStepSeconds: Int,
    idleModeEnabled: Boolean,
    theme: AlbumTheme,
) {
    ClassicSettingsList(
        title = "Settings",
        subtitle = "FoldPod",
        selectedIndex = selectedIndex,
        rows = listOf(
            "Wheel Sensitivity" to FoldPodSettingsStore.wheelSensitivityLabel(wheelSensitivity),
            "Haptics" to "Wheel tick · Button click",
            "Display" to if (idleModeEnabled) "Idle mode on" else "Idle mode off",
            "About" to "Player-first preview",
            "Connections" to "Spotify · YouTube playlists",
            "Seek Step" to "$seekStepSeconds sec",
        ),
        theme = theme,
    )
}

@Composable
internal fun WheelSensitivityScreen(
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    theme: AlbumTheme,
    enabled: Boolean = true,
) {
    ClassicLcd(title = "Wheel", sourceName = "Sensitivity") {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState()).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$sensitivity / 10",
                color = ClassicSelectionBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                enabled = enabled,
                value = sensitivity.toFloat(),
                onValueChange = { value ->
                    onSensitivityChange(value.roundToInt().coerceIn(1, 10))
                },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = ClassicSelectionBlue,
                    activeTrackColor = ClassicSelectionBlue,
                    inactiveTrackColor = Color(0xFFCCCCCC),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("Low", color = Color(0xFF666666), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("High", color = Color(0xFF666666), fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Wheel rotation per step",
                color = Color(0xFF666666),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Wheel · Adjust    Center · Done",
                color = Color(0xFF777777),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun SeekStepSettingsScreen(
    seekStepSeconds: Int,
    onSeekStepChange: (Int) -> Unit,
    theme: AlbumTheme,
    enabled: Boolean = true,
) {
    ClassicLcd(title = "Seek Step", sourceName = "Seconds") {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState()).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$seekStepSeconds sec", color = ClassicSelectionBlue,
                fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Slider(
                enabled = enabled,
                value = seekStepSeconds.toFloat(),
                onValueChange = { onSeekStepChange(it.roundToInt().coerceIn(
                    FoldPodSettingsStore.MIN_SEEK_STEP_SECONDS,
                    FoldPodSettingsStore.MAX_SEEK_STEP_SECONDS)) },
                valueRange = 1f..60f,
                steps = 58,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = ClassicSelectionBlue,
                    activeTrackColor = ClassicSelectionBlue,
                    inactiveTrackColor = Color(0xFFCCCCCC),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("1 sec", color = Color(0xFF666666), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("60 sec", color = Color(0xFF666666), fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Seconds per wheel step in SEEK mode", color = Color(0xFF666666),
                fontSize = 12.sp, textAlign = TextAlign.Center)
            Text("Wheel · Adjust    Center · Done", color = Color(0xFF777777),
                fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun HapticsSettingsScreen(
    selectedIndex: Int,
    wheelTickEnabled: Boolean,
    buttonClickEnabled: Boolean,
    theme: AlbumTheme,
) {
    ClassicSettingsList(
        title = "Haptics",
        subtitle = "Feedback",
        selectedIndex = selectedIndex,
        rows = listOf(
            "Wheel Tick" to if (wheelTickEnabled) "On" else "Off",
            "Button Click" to if (buttonClickEnabled) "On" else "Off",
        ),
        theme = theme,
    )
}

@Composable
internal fun DisplaySettingsScreen(
    selectedIndex: Int,
    idleModeEnabled: Boolean,
    idleDelaySeconds: Int,
    pixelShiftEnabled: Boolean,
    theme: AlbumTheme,
) {
    ClassicSettingsList(
        title = "Display",
        subtitle = "Cover",
        selectedIndex = selectedIndex,
        rows = listOf(
            "Idle Mode" to if (idleModeEnabled) "On" else "Off",
            "Idle Delay" to "$idleDelaySeconds sec",
            "Pixel Shift" to if (pixelShiftEnabled) "On" else "Off",
        ),
        theme = theme,
    )
}

@Composable
private fun ClassicSettingsList(
    title: String,
    subtitle: String,
    selectedIndex: Int,
    rows: List<Pair<String, String>>,
    theme: AlbumTheme,
) {
    val listState = rememberLazyListState()
    val safeIndex = selectedIndex.coerceIn(0, rows.lastIndex)
    ClassicKeepSelectionVisible(listState, safeIndex, rows.size)
    ClassicLcd(title = title, sourceName = subtitle) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            items(rows.size) { index ->
                MenuRow(
                    title = rows[index].first,
                    subtitle = rows[index].second,
                    selected = index == safeIndex,
                    theme = theme,
                )
            }
        }
    }
}

@Composable
internal fun AboutScreen(theme: AlbumTheme) {
    ClassicLcd(title = "About", sourceName = "FoldPod") {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState()).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "FoldPod",
                color = Color(0xFF111111),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Player-first · v${BuildConfig.VERSION_NAME}",
                color = ClassicSelectionBlue,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "An iPod-inspired shell for the music app already playing on your phone.",
                color = Color(0xFF666666),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
