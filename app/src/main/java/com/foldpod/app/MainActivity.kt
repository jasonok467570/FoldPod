package com.foldpod.app

import android.content.Intent
import android.app.AlertDialog
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.foldpod.app.library.GoogleAuthorizationBridge
import com.foldpod.app.library.LibraryViewModel
import com.foldpod.app.library.LibraryPlaylist
import com.foldpod.app.library.LibraryTrack

import com.foldpod.app.media.MediaControllerRepository
import com.foldpod.app.ui.FoldPodScreen
import com.foldpod.app.timer.SleepTimerScheduler
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


class MainActivity : ComponentActivity() {
    private var spotifyPlaybackDeviceDialog: AlertDialog? = null
    private val libraryModel by lazy { ViewModelProvider(this)[LibraryViewModel::class.java] }
    private val spotifyLibrary get() = libraryModel.spotify
    private val youtubeLibrary get() = libraryModel.youtube
    private val googleAuthorization by lazy { GoogleAuthorizationBridge(this, libraryModel.googleAuthorizationState) }
    private val googleResolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        googleAuthorization.handleResult(result.resultCode, result.data,
            onAuthorized = youtubeLibrary::refresh, onError = youtubeLibrary::showConnectionError)
    }

    private fun authorizeYouTube() {
        googleAuthorization.authorize(
            onResolution = { request -> googleResolution.launch(request) },
            onAuthorized = youtubeLibrary::refresh,
            onError = youtubeLibrary::showConnectionError,
        )
    }
    private val sleepTimerScheduler by lazy { SleepTimerScheduler.get(this) }
    private val repository by lazy {
        MediaControllerRepository.get(this)
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        googleAuthorization.restorePendingState(savedInstanceState?.getBundle("googleAuthorization"))
        libraryModel.restoreConnections()
        // Bind the repository rather than the Activity; cancellation needs no UI callback
        // to release the shared media command guard after a fold or disconnect.
        val playbackLibrary = spotifyLibrary
        repository.setSpotifyWebPlaybackBusyCheck { playbackLibrary.playbackBlocked }

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        enableEdgeToEdge()

        WindowCompat
            .getInsetsController(
                window,
                window.decorView,
            )
            .apply {
                hide(
                    WindowInsetsCompat.Type.systemBars()
                )
                systemBarsBehavior =
                    WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

        setContent {
            MaterialTheme {
                if (hasNotificationAccess()) {
                    val state by
                        repository.state
                            .collectAsStateWithLifecycle()

                    val sessions by
                        repository.sessions
                            .collectAsStateWithLifecycle()
                    val sleepTimer by sleepTimerScheduler.state.collectAsStateWithLifecycle()
                    val spotifyState by spotifyLibrary.state.collectAsStateWithLifecycle()
                    val youtubeState by youtubeLibrary.state.collectAsStateWithLifecycle()

                    FoldPodScreen(
                        state = state,
                        playerSessions = sessions,
                        spotifyLibrary = spotifyState,
                        youtubeLibrary = youtubeState,
                        onSpotifyConnect = {
                            spotifyLibrary.connect { uri -> startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                        onSpotifyRefresh = spotifyLibrary::refresh,
                        onSpotifyDisconnect = {
                            spotifyLibrary.disconnect()
                        },
                        onYouTubeConnect = ::authorizeYouTube,
                        onYouTubeRefresh = ::authorizeYouTube,
                        onYouTubeDisconnect = {
                            googleAuthorization.disconnect()
                            youtubeLibrary.disconnect()
                        },
                        onYouTubeImportPlaylist = youtubeLibrary::importSharedPlaylist,
                        onLibraryPlaylist = { playlist ->
                            if (playlist.providerId == "spotify") spotifyLibrary.loadTracks(playlist)
                            else youtubeLibrary.loadTracks(playlist)
                        },
                        onLibraryTrack = ::requestLibraryTrack,
                        onPrevious =
                            repository::previous,
                        onNext =
                            repository::next,
                        onPlayPause =
                            repository::togglePlayPause,
                        onQueueItem =
                            repository::playQueueItem,
                        queueSelectionResults = repository.queueSelectionResults,
                        onSeekTo =
                            repository::seekTo,
                        onSeekBy = repository::seekBy,
                        onMediaAction = repository::performCustomAction,
                        sleepTimer = sleepTimer,
                        onStartSleepTimer = sleepTimerScheduler::start,
                        onCancelSleepTimer = sleepTimerScheduler::cancel,
                        onExactTimerSettings = {
                            val intent = sleepTimerScheduler.exactAlarmSettingsIntent()
                            if (intent != null) {
                                runCatching { startActivity(intent) }.onFailure {
                                    Toast.makeText(this, "정확한 타이머 설정을 열 수 없습니다", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this, "정확한 타이머가 이미 허용되어 있습니다", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSelectPlayer =
                            repository::selectSession,
                        onRefreshPlayers =
                            repository::refreshActiveSession,
                        onOpenPlayerApp = { packageName ->
                            openPlayerApp(
                                packageName
                            )
                        },
                        onExit = {
                            finish()
                        },
                        onIdleChanged = { idle ->
                            window.attributes =
                                window.attributes.apply {
                                    screenBrightness =
                                        if (idle) {
                                            0.05f
                                        } else {
                                            WindowManager
                                                .LayoutParams
                                                .BRIGHTNESS_OVERRIDE_NONE
                                        }
                                }
                        },
                    )
                } else {
                    PermissionScreen(
                        onOpenSettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                )
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasNotificationAccess()) {
            repository.start()
        }
    }

    override fun onResume() {
        super.onResume()
        sleepTimerScheduler.refresh()
        if (hasNotificationAccess()) {
            repository.refreshActiveSession()
        }
    }

    override fun onStop() {
        repository.stop()
        super.onStop()
    }

    override fun onDestroy() {
        spotifyPlaybackDeviceDialog?.dismiss()
        spotifyPlaybackDeviceDialog = null
        googleAuthorization.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("googleAuthorization", googleAuthorization.savePendingState())
        super.onSaveInstanceState(outState)
    }

    private fun requestLibraryTrack(playlist: LibraryPlaylist, track: LibraryTrack, selectedIndex: Int) {
        if (playlist.providerId == "spotify") {
            if (spotifyLibrary.playbackBlocked) {
                Toast.makeText(this, "Spotify 곡 선택의 결과를 확인 중입니다. Spotify Connect로 다시 연결해 주세요.", Toast.LENGTH_SHORT).show()
                return
            }
            val error = repository.beginSpotifyWebPlayback()
            if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return }
            spotifyLibrary.playTrack(playlist, track, selectedIndex,
                confirmDevice = ::confirmSpotifyPlaybackDevice) { playbackError ->
                if (playbackError != null && !isDestroyed) Toast.makeText(this, playbackError, Toast.LENGTH_LONG).show()
            }
            return
        }
        val error = when (playlist.providerId) {
            "youtube_music" -> repository.requestYouTubeLibraryTrack(playlist, track, selectedIndex)
            else -> "이 음악 제공자의 곡 선택은 지원하지 않습니다"
        }
        if (error != null) Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private suspend fun confirmSpotifyPlaybackDevice(deviceName: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (isFinishing || isDestroyed) { continuation.resume(false); return@suspendCancellableCoroutine }
            val dialog = AlertDialog.Builder(this)
                .setTitle("Spotify 재생 기기 확인")
                .setMessage("‘$deviceName’에서 선택한 곡을 재생할까요? 이 휴대폰의 Spotify 기기가 맞는지 확인해 주세요.")
                .setPositiveButton("이 기기에서 재생") { _, _ -> if (continuation.isActive) continuation.resume(true) }
                .setNegativeButton("취소") { _, _ -> if (continuation.isActive) continuation.resume(false) }
                .create()
            dialog.setOnDismissListener {
                if (spotifyPlaybackDeviceDialog === dialog) spotifyPlaybackDeviceDialog = null
                if (continuation.isActive) continuation.resume(false)
            }
            continuation.invokeOnCancellation { runOnUiThread { dialog.dismiss() } }
            spotifyPlaybackDeviceDialog = dialog
            dialog.show()
        }

    private fun openPlayerApp(
        packageName: String?
    ) {
        if (packageName.isNullOrBlank()) {
            return
        }

        val launchIntent =
            packageManager
                .getLaunchIntentForPackage(
                    packageName
                )
                ?: return

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(
            launchIntent
        )
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
}


@Composable
private fun PermissionScreen(
    onOpenSettings: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(28.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Text(
            "FoldPod",
            color = Color.White,
            style =
                MaterialTheme.typography.headlineLarge,
        )

        Spacer(
            Modifier.height(16.dp)
        )

        Text(
            "FoldPod uses Android MediaSession access to control the music app already playing on your phone. It does not store or transmit notification contents.",
            color = Color(0xFFBDBDBD),
        )

        Spacer(
            Modifier.height(20.dp)
        )

        Button(
            onClick = onOpenSettings
        ) {
            Text(
                "Open notification access"
            )
        }
    }
}
