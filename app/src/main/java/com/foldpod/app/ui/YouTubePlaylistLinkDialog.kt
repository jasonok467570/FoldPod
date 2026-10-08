package com.foldpod.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.foldpod.app.library.ProviderLibraryState
import com.foldpod.app.library.YouTubePlaylistLink

@Composable
internal fun YouTubePlaylistLinkDialog(
    library: ProviderLibraryState,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var submittedId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(library.loading, library.error, library.playlists, submittedId) {
        if (submittedId != null && !library.loading && library.error == null &&
            library.playlists.any { it.id == submittedId }) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = Color.Black,
        textContentColor = Color(0xFF555555),
        title = { Text("YouTube 공유 목록 추가") },
        text = {
            Column {
                Text("공개·일부 공개 YouTube 또는 YouTube Music 재생목록 링크를 붙여 넣으세요.")
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; validationError = null; submittedId = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("공유 링크") },
                    placeholder = { Text("https://music.youtube.com/playlist?list=…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    maxLines = 3,
                    enabled = !library.loading,
                    isError = validationError != null || (submittedId != null && library.error != null),
                )
                if (!library.connected) {
                    Text("먼저 Connections에서 YouTube Connect로 로그인해 주세요.")
                }
                val error = validationError ?: library.error.takeIf { submittedId != null }
                if (error != null) Text(error, color = Color(0xFFB3261E))
                Text("추가한 목록은 FoldPod에 저장되며, 원본 계정의 목록은 변경하지 않습니다.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = library.connected && !library.loading && link.isNotBlank(),
                onClick = {
                    try {
                        val id = YouTubePlaylistLink.parse(link)
                        validationError = null
                        submittedId = id
                        onImport(link)
                    } catch (invalid: IllegalArgumentException) {
                        validationError = invalid.message ?: "올바른 재생목록 링크를 입력해 주세요."
                    }
                },
            ) { Text(if (library.loading) "불러오는 중…" else "추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
