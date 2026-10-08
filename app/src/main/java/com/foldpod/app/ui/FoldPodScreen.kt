package com.foldpod.app.ui

import android.media.AudioManager
import android.os.SystemClock
import android.widget.Toast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.foldpod.app.media.NowPlayingState
import com.foldpod.app.media.QueueSelection
import com.foldpod.app.media.QueueSelectionRequest
import com.foldpod.app.media.QueueNavigationPolicy
import com.foldpod.app.media.QueueSelectionResult
import com.foldpod.app.media.identity
import com.foldpod.app.media.PlayerSessionInfo
import com.foldpod.app.media.MediaSessionAction
import com.foldpod.app.media.MediaActionSemantic
import com.foldpod.app.timer.SleepTimerState
import com.foldpod.app.settings.FoldPodSettingsStore
import com.foldpod.app.library.LibraryPlaylist
import com.foldpod.app.library.LibraryTrack
import com.foldpod.app.library.ProviderLibraryState
import com.foldpod.app.library.YouTubeTrackLink
import com.foldpod.app.library.SpotifyTrackLink
import com.foldpod.app.library.YouTubeLibraryRepository
import com.foldpod.app.library.PlaylistBrowserPolicy
import com.foldpod.app.library.PlaylistFavoritesStore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext



@Composable
fun FoldPodScreen(
    state: NowPlayingState,
    playerSessions: List<PlayerSessionInfo>,
    spotifyLibrary: ProviderLibraryState,
    youtubeLibrary: ProviderLibraryState,
    onSpotifyConnect: () -> Unit,
    onSpotifyRefresh: () -> Unit,
    onSpotifyDisconnect: () -> Unit,
    onYouTubeConnect: () -> Unit,
    onYouTubeRefresh: () -> Unit,
    onYouTubeDisconnect: () -> Unit,
    onYouTubeImportPlaylist: (String) -> Unit,
    onLibraryPlaylist: (LibraryPlaylist) -> Unit,
    onLibraryTrack: (LibraryPlaylist, LibraryTrack, Int) -> Unit,

    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onQueueItem: (QueueSelectionRequest) -> Unit,
    queueSelectionResults: Flow<QueueSelectionResult>,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long, NowPlayingState) -> Boolean,
    onMediaAction: (MediaSessionAction) -> Boolean,
    sleepTimer: SleepTimerState,
    onStartSleepTimer: (Int, String) -> Boolean,
    onCancelSleepTimer: () -> Unit,
    onExactTimerSettings: () -> Unit,

    onSelectPlayer: (String) -> Unit,
    onRefreshPlayers: () -> Unit,
    onOpenPlayerApp: (String?) -> Unit,

    onExit: () -> Unit,
    onIdleChanged: (Boolean) -> Unit,
) {
    val context =
        LocalContext.current

    val settingsStore =
        remember {
            FoldPodSettingsStore(context)
        }

    val audio =
        context.getSystemService(
            AudioManager::class.java
        )

    var page by remember {
        mutableStateOf(
            FoldPodPage.NOW_PLAYING
        )
    }

    var navigationBackwards by remember {
        mutableStateOf(false)
    }
    var nowPlayingReturnPage by remember { mutableStateOf(FoldPodPage.MAIN_MENU) }

    var mainMenuSelection by remember {
        mutableIntStateOf(0)
    }
    var controlsLocked by rememberSaveable { mutableStateOf(false) }
    var playbackOptionsSelection by remember { mutableIntStateOf(0) }
    var sleepTimerSelection by remember { mutableIntStateOf(0) }
    var playlistBrowserSelection by remember { mutableIntStateOf(0) }
    var providerSelection by remember { mutableIntStateOf(0) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    val favoritesStore = remember(context) { PlaylistFavoritesStore(context) }
    var playlistFavorites by remember(favoritesStore) { mutableStateOf(favoritesStore.read()) }
    var connectionsSelection by remember { mutableIntStateOf(0) }
    var showYouTubePlaylistLink by rememberSaveable { mutableStateOf(false) }
    var libraryTracksSelection by remember { mutableIntStateOf(0) }
    var selectedLibraryPlaylist by remember { mutableStateOf<LibraryPlaylist?>(null) }
    var pendingTrackScreen by remember { mutableStateOf<QueueSelectionRequest?>(null) }

    val playlistRows = state.queue.map { it.identity() }
    var rememberedPlaylistSelection by remember {
        mutableStateOf(QueueSelection(state.sessionGeneration, playlistRows, 0))
    }
    val selectionAtComposition = rememberedPlaylistSelection
    fun currentPlaylistIndex(): Int = QueueNavigationPolicy.selection(
        rememberedPlaylistSelection, state.sessionGeneration, playlistRows,
    )
    // Rendering reconciles synchronously; event handlers read the latest mutable selection.
    val playlistSelection = currentPlaylistIndex()
    SideEffect {
        if (rememberedPlaylistSelection == selectionAtComposition) {
            rememberedPlaylistSelection = QueueSelection(state.sessionGeneration, playlistRows, playlistSelection)
        }
    }
    fun selectPlaylistIndex(index: Int) {
        rememberedPlaylistSelection = QueueSelection(state.sessionGeneration, playlistRows, index)
    }


    var playerSelection by remember {
        mutableIntStateOf(0)
    }

    var settingsSelection by remember {
        mutableIntStateOf(0)
    }

    var hapticsSelection by remember {
        mutableIntStateOf(0)
    }

    var displaySettingsSelection by remember {
        mutableIntStateOf(0)
    }

    var wheelSensitivity by remember {
        mutableIntStateOf(
            settingsStore.wheelSensitivity
        )
    }

    var seekStepSeconds by remember {
        mutableIntStateOf(settingsStore.seekStepSeconds)
    }

    var wheelTickHapticsEnabled by remember {
        mutableStateOf(
            settingsStore.wheelTickHapticsEnabled
        )
    }

    var buttonHapticsEnabled by remember {
        mutableStateOf(
            settingsStore.buttonHapticsEnabled
        )
    }

    var idleModeEnabled by remember {
        mutableStateOf(
            settingsStore.idleModeEnabled
        )
    }

    var idleDelaySeconds by remember {
        mutableIntStateOf(
            settingsStore.idleDelaySeconds
        )
    }

    var pixelShiftEnabled by remember {
        mutableStateOf(
            settingsStore.pixelShiftEnabled
        )
    }

    var lastInteraction by remember {
        mutableLongStateOf(
            SystemClock.elapsedRealtime()
        )
    }

    var now by remember {
        mutableLongStateOf(
            SystemClock.elapsedRealtime()
        )
    }

    var nowPlayingControlMode by remember {
        mutableStateOf(
            NowPlayingControlMode.NORMAL
        )
    }

    // Session/track/page changes invalidate pending wheel movement synchronously.
    val seekContext = listOf(state.packageName, state.sessionGeneration, state.title,
        state.artist, state.album, state.durationMs, state.queue.firstOrNull { it.isActive }?.identity(), page)
    var pendingSeekDeltaMs by remember(seekContext) { mutableLongStateOf(0L) }
    val latestSeekContext by rememberUpdatedState(seekContext)
    val latestState by rememberUpdatedState(state)
    val latestSeekBy by rememberUpdatedState(onSeekBy)
    val seekPreviewMs = WheelSeekPolicy.preview(estimatedPosition(state, now), pendingSeekDeltaMs, state.durationMs)

    var nowPlayingControlMessage by remember {
        mutableStateOf<String?>(null)
    }

    var controlInteractionCounter by remember {
        mutableIntStateOf(0)
    }

    var albumTheme by remember {
        mutableStateOf(
            extractAlbumTheme(null)
        )
    }

    LaunchedEffect(
        state.artwork,
        state.title,
        state.artist,
    ) {
        albumTheme =
            withContext(Dispatchers.Default) {
                extractAlbumTheme(
                    state.artwork
                )
            }
    }

    val animatedBackgroundTop by
        animateColorAsState(
            targetValue =
                albumTheme.backgroundTop,
            animationSpec = tween(450),
            label = "backgroundTop",
        )

    val animatedBackgroundBottom by
        animateColorAsState(
            targetValue =
                albumTheme.backgroundBottom,
            animationSpec = tween(450),
            label = "backgroundBottom",
        )

    val animatedWheel by
        animateColorAsState(
            targetValue =
                albumTheme.wheel,
            animationSpec = tween(450),
            label = "wheel",
        )

    val animatedWheelCenter by
        animateColorAsState(
            targetValue =
                albumTheme.wheelCenter,
            animationSpec = tween(450),
            label = "wheelCenter",
        )

    val animatedAccent by
        animateColorAsState(
            targetValue =
                albumTheme.accent,
            animationSpec = tween(450),
            label = "accent",
        )

    val displayTheme =
        AlbumTheme(
            backgroundTop =
                animatedBackgroundTop,
            backgroundBottom =
                animatedBackgroundBottom,
            wheel =
                animatedWheel,
            wheelCenter =
                animatedWheelCenter,
            accent =
                animatedAccent,
        )

    LaunchedEffect(Unit) {
        while (true) {
            now =
                SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    val isIdle =
        idleModeEnabled && !showYouTubePlaylistLink &&
            now - lastInteraction >=
            idleDelaySeconds * 1_000L

    LaunchedEffect(
        seekContext,
    ) {
        nowPlayingControlMode =
            NowPlayingControlMode.NORMAL

        nowPlayingControlMessage =
            null
    }

    LaunchedEffect(isIdle) {
        onIdleChanged(isIdle)

        if (isIdle) {
            nowPlayingReturnPage = FoldPodPage.MAIN_MENU
            page =
                FoldPodPage.NOW_PLAYING

            nowPlayingControlMode =
                NowPlayingControlMode.NORMAL

            nowPlayingControlMessage =
                null
        }
    }

    fun interact() {
        lastInteraction =
            SystemClock.elapsedRealtime()
    }

    fun commitPendingSeek() {
        val delta = pendingSeekDeltaMs
        pendingSeekDeltaMs = 0L
        if (delta == 0L || latestSeekContext != seekContext || !latestState.canSeek ||
            nowPlayingControlMode != NowPlayingControlMode.SEEK || page != FoldPodPage.NOW_PLAYING || controlsLocked) return
        if (latestSeekBy(delta, latestState)) {
            nowPlayingControlMessage = "Seek · ${formatTime(WheelSeekPolicy.preview(
                estimatedPosition(latestState, SystemClock.elapsedRealtime()), delta, latestState.durationMs,
            ))}"
        }
    }

    LaunchedEffect(nowPlayingControlMode, seekContext, pendingSeekDeltaMs, controlInteractionCounter) {
        if (page == FoldPodPage.NOW_PLAYING && nowPlayingControlMode == NowPlayingControlMode.SEEK && pendingSeekDeltaMs != 0L) {
            delay(400)
            commitPendingSeek()
        }
    }

    LaunchedEffect(
        nowPlayingControlMode,
        controlInteractionCounter,
    ) {
        if (
            nowPlayingControlMode !=
            NowPlayingControlMode.NORMAL
        ) {
            delay(2_000)

            pendingSeekDeltaMs = 0L
            nowPlayingControlMode =
                NowPlayingControlMode.NORMAL

            nowPlayingControlMessage =
                null
        }
    }

    val selectedSession =
        playerSessions.firstOrNull {
            it.isSelected && it.packageName == state.packageName
        }

    val currentSourceName =
        selectedSession?.appName
            ?: mediaSourceName(
                state.packageName
            )

    // Preserve the current MediaSession queue first; account libraries remain separate.
    val currentQueuePlaylists = if (state.queue.isEmpty()) emptyList() else listOf(
        PlaylistSummary(
            id = "current:${state.packageName}:${state.sessionGeneration}",
            title = state.queueTitle.ifBlank { "Current Playlist" },
            subtitle = currentSourceName,
            artwork = state.artwork ?: state.queue.firstOrNull { it.artwork != null }?.artwork,
            artworkUri = state.queue.firstOrNull { it.isActive }?.artworkUri
                ?: state.queue.firstOrNull { it.artworkUri != null }?.artworkUri,
            trackCount = state.queue.size,
        )
    )

    val accountPlaylists = spotifyLibrary.playlists + youtubeLibrary.playlists
    val currentProviderId = playlistProviderId(state.packageName)
    val sessionProviderIds = playerSessions.mapNotNull { playlistProviderId(it.packageName) }
    val providerIds = buildList {
        if (spotifyLibrary.connected || spotifyLibrary.playlists.isNotEmpty() ||
            "spotify" in sessionProviderIds || currentProviderId == "spotify") add("spotify")
        if (youtubeLibrary.connected || youtubeLibrary.playlists.isNotEmpty() ||
            YouTubeLibraryRepository.PROVIDER_ID in sessionProviderIds || currentProviderId == YouTubeLibraryRepository.PROVIDER_ID) add(YouTubeLibraryRepository.PROVIDER_ID)
        addAll(sessionProviderIds.filter { it != "spotify" && it != YouTubeLibraryRepository.PROVIDER_ID }.distinct())
        if (currentProviderId != null && currentProviderId !in this) add(currentProviderId)
    }
    fun providerName(id: String): String = when (id) {
        "spotify" -> "Spotify"
        YouTubeLibraryRepository.PROVIDER_ID -> "YouTube Music"
        else -> playerSessions.firstOrNull { it.packageName == id }?.appName ?: mediaSourceName(id)
    }
    val providers = providerIds.map { id ->
        PlaylistProviderSummary(id, providerName(id), accountPlaylists.count { it.providerId == id } +
            if (id == currentProviderId) currentQueuePlaylists.size else 0)
    }
    val safeProviderSelection = providerSelection.coerceIn(0, providers.lastIndex.coerceAtLeast(0))
    val browsingProviderId = selectedProviderId?.takeIf { it in providerIds }
        ?: providers.getOrNull(safeProviderSelection)?.id
    val providerLibrary = when (browsingProviderId) {
        "spotify" -> spotifyLibrary
        YouTubeLibraryRepository.PROVIDER_ID -> youtubeLibrary
        else -> null
    }
    val orderedAccountPlaylists = PlaylistBrowserPolicy.order(
        accountPlaylists.filter { it.providerId == browsingProviderId }, playlistFavorites,
    ) { it.providerLibraryId }
    fun playlistSummaries(favorites: Set<String>): List<PlaylistSummary> {
        val summaries = PlaylistBrowserPolicy.order(
            accountPlaylists.filter { it.providerId == browsingProviderId }, favorites,
        ) { it.providerLibraryId }.map { playlist ->
            PlaylistSummary(playlist.providerLibraryId, playlist.name,
                playlist.trackCount?.let { "$it items" } ?: "Track count unavailable",
                artwork = null, trackCount = playlist.trackCount ?: 0, artworkUri = playlist.artworkUrl)
        }
        val (pinned, remaining) = summaries.partition { it.id in favorites }
        return pinned + (if (browsingProviderId == currentProviderId) currentQueuePlaylists else emptyList()) + remaining
    }
    val availablePlaylists = playlistSummaries(playlistFavorites)
    val safePlaylistBrowserSelection = playlistBrowserSelection.coerceIn(0, availablePlaylists.lastIndex.coerceAtLeast(0))
    val selectedLibrary = if (selectedLibraryPlaylist?.providerId == "spotify") spotifyLibrary else youtubeLibrary

    fun connectionAction(index: Int) {
        if (controlsLocked) return
        interact()
        connectionsSelection = index
        when (index) {
            0 -> onSpotifyConnect()
            1 -> onSpotifyRefresh()
            2 -> onSpotifyDisconnect()
            3 -> onYouTubeConnect()
            4 -> onYouTubeRefresh()
            5 -> onYouTubeDisconnect()
            6 -> showYouTubePlaylistLink = true
        }
    }

    // A natural track change cannot complete a rejected selection: only its repository result can.
    LaunchedEffect(queueSelectionResults) {
        queueSelectionResults.collect { result ->
            if (pendingTrackScreen == result.request) {
                pendingTrackScreen = null
                if (result.succeeded && page == FoldPodPage.PLAYLIST && !controlsLocked) {
                    nowPlayingReturnPage = FoldPodPage.PLAYLIST
                    page = FoldPodPage.NOW_PLAYING
                    nowPlayingControlMode = NowPlayingControlMode.NORMAL
                }
            }
        }
    }
    LaunchedEffect(state.sessionGeneration, page) {
        if (pendingTrackScreen?.sessionGeneration != state.sessionGeneration || page != FoldPodPage.PLAYLIST) {
            pendingTrackScreen = null
        }
    }

    fun toggleHold() {
        interact()
        pendingTrackScreen = null
        controlsLocked = !controlsLocked
        pendingSeekDeltaMs = 0L
        nowPlayingControlMode = NowPlayingControlMode.NORMAL
        nowPlayingControlMessage = null
    }

    fun returnToCurrentTrack() {
        interact()
        if (controlsLocked) return
        pendingTrackScreen = null
        if (page == FoldPodPage.PLAYLIST) {
            val active = state.queue.indices.filter { state.queue[it].isActive }.singleOrNull()
            if (active != null) selectPlaylistIndex(active)
            else Toast.makeText(context, "현재 곡의 목록 위치를 확인할 수 없습니다", Toast.LENGTH_SHORT).show()
        } else {
            navigationBackwards = true
            if (page != FoldPodPage.NOW_PLAYING) nowPlayingReturnPage = page
            page = FoldPodPage.NOW_PLAYING
            nowPlayingControlMode = NowPlayingControlMode.NORMAL
        }
    }

    fun centerLongPressed() {
        if (page != FoldPodPage.PROVIDER_PLAYLISTS) {
            returnToCurrentTrack()
            return
        }
        interact()
        if (controlsLocked) return
        val selected = availablePlaylists.getOrNull(playlistBrowserSelection.coerceIn(0, availablePlaylists.lastIndex.coerceAtLeast(0))) ?: return
        val playlist = orderedAccountPlaylists.firstOrNull { it.providerLibraryId == selected.id } ?: return
        val updated = favoritesStore.toggle(playlist.providerId, playlist.id)
        playlistBrowserSelection = playlistSummaries(updated).indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        playlistFavorites = updated
    }

    fun seekHeld(direction: Int) {
        interact()
        if (controlsLocked) return
        if (!state.canSeek) {
            nowPlayingControlMessage = "이 플레이어는 탐색을 지원하지 않습니다"
            return
        }
        if (onSeekBy(direction * 2_000L, state)) {
            pendingSeekDeltaMs = 0L
            nowPlayingControlMode = NowPlayingControlMode.NORMAL
            nowPlayingControlMessage = if (direction > 0) "Seek +2 sec" else "Seek −2 sec"
            controlInteractionCounter += 1
        }
    }

    fun menuPressed() {
        if (controlsLocked) return
        interact()
        if (showYouTubePlaylistLink) {
            showYouTubePlaylistLink = false
            return
        }
        pendingTrackScreen = null
        pendingSeekDeltaMs = 0L
        navigationBackwards = true

        page =
            when (page) {
                FoldPodPage.NOW_PLAYING -> {
                    nowPlayingControlMode =
                        NowPlayingControlMode.NORMAL
                    nowPlayingControlMessage =
                        null
                    nowPlayingReturnPage
                }

                FoldPodPage.MAIN_MENU -> {
                    nowPlayingReturnPage = FoldPodPage.MAIN_MENU
                    FoldPodPage.NOW_PLAYING
                }

                FoldPodPage.PLAYLIST,
                FoldPodPage.LIBRARY_TRACKS -> FoldPodPage.PROVIDER_PLAYLISTS

                FoldPodPage.PROVIDER_PLAYLISTS -> FoldPodPage.PLAYLISTS

                FoldPodPage.PLAYLISTS,
                FoldPodPage.PLAYER,
                FoldPodPage.PLAYBACK_OPTIONS,
                FoldPodPage.SLEEP_TIMER,
                FoldPodPage.SETTINGS ->
                    FoldPodPage.MAIN_MENU

                FoldPodPage.WHEEL_SETTINGS,
                FoldPodPage.SEEK_SETTINGS,
                FoldPodPage.HAPTICS_SETTINGS,
                FoldPodPage.DISPLAY_SETTINGS,
                FoldPodPage.CONNECTIONS,
                FoldPodPage.ABOUT ->
                    FoldPodPage.SETTINGS
            }
    }

    fun centerPressed() {
        if (controlsLocked) return
        interact()
        navigationBackwards = false

        when (page) {
            FoldPodPage.NOW_PLAYING -> {
                controlInteractionCounter += 1

                when (nowPlayingControlMode) {
                    NowPlayingControlMode.NORMAL -> {
                        pendingSeekDeltaMs = 0L
                        nowPlayingControlMessage =
                            null
                        nowPlayingControlMode =
                            NowPlayingControlMode.SEEK
                    }

                    NowPlayingControlMode.SEEK -> {
                        commitPendingSeek()
                        nowPlayingControlMode =
                            NowPlayingControlMode.NORMAL
                        nowPlayingControlMessage =
                            null
                    }
                }
            }

            FoldPodPage.MAIN_MENU -> {
                val selected =
                    MAIN_MENU_ITEMS.getOrNull(
                        mainMenuSelection
                    ) ?: return

                when (selected.action) {
                    MainMenuAction.NOW_PLAYING -> {
                        nowPlayingReturnPage = FoldPodPage.MAIN_MENU
                        page =
                            FoldPodPage.NOW_PLAYING
                    }

                    MainMenuAction.PLAYLIST -> {
                        providerSelection = 0
                        page =
                            FoldPodPage.PLAYLISTS
                    }

                    MainMenuAction.PLAYER -> {
                        onRefreshPlayers()

                        val selectedIndex =
                            playerSessions.indexOfFirst {
                                it.isSelected
                            }

                        playerSelection =
                            if (selectedIndex >= 0) {
                                selectedIndex
                            } else {
                                0
                            }

                        page =
                            FoldPodPage.PLAYER
                    }

                    MainMenuAction.SETTINGS -> {
                        settingsSelection = 0
                        page =
                            FoldPodPage.SETTINGS
                    }

                    MainMenuAction.EXIT ->
                        onExit()
                    MainMenuAction.PLAYBACK_OPTIONS -> {
                        playbackOptionsSelection = 0
                        page = FoldPodPage.PLAYBACK_OPTIONS
                    }
                    MainMenuAction.SLEEP_TIMER -> {
                        sleepTimerSelection = 0
                        page = FoldPodPage.SLEEP_TIMER
                    }
                }
            }

            FoldPodPage.PLAYLISTS -> {
                providers.getOrNull(providerSelection.coerceIn(0, providers.lastIndex.coerceAtLeast(0)))?.let { provider ->
                    selectedProviderId = provider.id
                    playlistBrowserSelection = 0
                    page = FoldPodPage.PROVIDER_PLAYLISTS
                }
            }

            FoldPodPage.PROVIDER_PLAYLISTS -> {
                val selected = availablePlaylists.getOrNull(playlistBrowserSelection.coerceIn(0, availablePlaylists.lastIndex.coerceAtLeast(0)))
                if (selected != null && currentQueuePlaylists.any { it.id == selected.id }) {
                    val activeIndex = state.queue.indexOfFirst { it.isActive }
                    selectPlaylistIndex(if (activeIndex >= 0) activeIndex else 0)
                    page = FoldPodPage.PLAYLIST
                } else if (selected != null) {
                    accountPlaylists.firstOrNull { it.providerLibraryId == selected.id }?.let { playlist ->
                        selectedLibraryPlaylist = playlist
                        libraryTracksSelection = 0
                        onLibraryPlaylist(playlist)
                        page = FoldPodPage.LIBRARY_TRACKS
                    }
                }
            }

            FoldPodPage.LIBRARY_TRACKS -> {
                val playlist = selectedLibraryPlaylist
                val link = if (selectedLibrary.loading || selectedLibrary.error != null) null else
                    when (playlist?.providerId) {
                        "spotify" -> SpotifyTrackLink.forSelection(selectedLibrary, playlist, libraryTracksSelection)
                        "youtube_music" -> YouTubeTrackLink.forSelection(selectedLibrary, playlist, libraryTracksSelection)
                        else -> null
                    }
                if (playlist != null && link != null) {
                    selectedLibrary.tracks.getOrNull(libraryTracksSelection)?.let { track ->
                        onLibraryTrack(playlist, track, libraryTracksSelection)
                    }
                } else {
                    Toast.makeText(context, "이 곡을 선택할 수 없습니다. 재생목록을 다시 불러오거나 음악 앱에서 확인하세요", Toast.LENGTH_SHORT).show()
                }
            }

            FoldPodPage.CONNECTIONS -> connectionAction(connectionsSelection)

            FoldPodPage.PLAYLIST -> {
                val currentIndex = currentPlaylistIndex()
                state.queue
                    .getOrNull(
                        currentIndex
                    )
                    ?.let {
                        val request = QueueSelectionRequest(
                            state.sessionGeneration, state.queueRevision, playlistRows, currentIndex,
                        )
                        if (it.isActive) {
                            nowPlayingReturnPage = FoldPodPage.PLAYLIST
                            page = FoldPodPage.NOW_PLAYING
                            nowPlayingControlMode = NowPlayingControlMode.NORMAL
                        } else {
                            pendingTrackScreen = request
                            onQueueItem(request)
                        }
                    }
            }

            FoldPodPage.PLAYER -> {
                if (playerSessions.isEmpty()) {
                    onOpenPlayerApp(
                        state.packageName
                    )
                    return
                }

                if (
                    playerSelection <
                    playerSessions.size
                ) {
                    val session =
                        playerSessions[
                            playerSelection
                        ]

                    if (!session.hasSession) {
                        Toast.makeText(context, "${session.appName} 앱에서 재생을 시작한 뒤 Player를 새로고침하세요", Toast.LENGTH_LONG).show()
                        return
                    }

                    onSelectPlayer(
                        session.packageName
                    )

                    page =
                        FoldPodPage.NOW_PLAYING

                } else {
                    onOpenPlayerApp(
                        state.packageName
                    )
                }
            }

            FoldPodPage.SETTINGS -> {
                when (settingsSelection) {
                    0 ->
                        page =
                            FoldPodPage.WHEEL_SETTINGS

                    1 -> {
                        hapticsSelection = 0
                        page =
                            FoldPodPage.HAPTICS_SETTINGS
                    }

                    2 -> {
                        displaySettingsSelection = 0
                        page =
                            FoldPodPage.DISPLAY_SETTINGS
                    }

                    3 ->
                        page =
                            FoldPodPage.ABOUT
                    4 -> {
                        connectionsSelection = 0
                        page = FoldPodPage.CONNECTIONS
                    }
                    5 -> page = FoldPodPage.SEEK_SETTINGS
                }
            }

            FoldPodPage.PLAYBACK_OPTIONS -> {
                val semantic = if (playbackOptionsSelection == 0) MediaActionSemantic.SHUFFLE else MediaActionSemantic.REPEAT
                val action = state.customActions.filter { it.semantic == semantic }.singleOrNull()
                if (action == null || !onMediaAction(action)) {
                    Toast.makeText(context, "이 플레이어가 해당 기능을 제공하지 않거나 상태가 바뀌었습니다", Toast.LENGTH_SHORT).show()
                }
            }
            FoldPodPage.SLEEP_TIMER -> {
                when (sleepTimerSelection) {
                    0, 1, 2 -> {
                        val packageName = state.packageName
                        if (packageName == null || !onStartSleepTimer(listOf(15, 30, 60)[sleepTimerSelection], packageName)) {
                            Toast.makeText(context, "재생 앱을 선택한 뒤 타이머를 시작하세요", Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> onCancelSleepTimer()
                    4 -> onExactTimerSettings()
                }
            }

            FoldPodPage.WHEEL_SETTINGS,
            FoldPodPage.SEEK_SETTINGS -> {
                page = FoldPodPage.SETTINGS
            }

            FoldPodPage.HAPTICS_SETTINGS -> {
                when (hapticsSelection) {
                    0 -> {
                        wheelTickHapticsEnabled =
                            !wheelTickHapticsEnabled
                        settingsStore
                            .setWheelTickHapticsEnabled(
                                wheelTickHapticsEnabled
                            )
                    }

                    1 -> {
                        buttonHapticsEnabled =
                            !buttonHapticsEnabled
                        settingsStore
                            .setButtonHapticsEnabled(
                                buttonHapticsEnabled
                            )
                    }
                }
            }

            FoldPodPage.DISPLAY_SETTINGS -> {
                when (displaySettingsSelection) {
                    0 -> {
                        idleModeEnabled =
                            !idleModeEnabled
                        settingsStore
                            .setIdleModeEnabled(
                                idleModeEnabled
                            )
                    }

                    1 -> {
                        idleDelaySeconds =
                            settingsStore
                                .cycleIdleDelaySeconds()
                    }

                    2 -> {
                        pixelShiftEnabled =
                            !pixelShiftEnabled
                        settingsStore
                            .setPixelShiftEnabled(
                                pixelShiftEnabled
                            )
                    }
                }
            }

            FoldPodPage.ABOUT -> {
                page = FoldPodPage.SETTINGS
            }
        }
    }

    fun wheelStep(
        direction: Int
    ) {
        if (controlsLocked) return
        interact()

        when (page) {
            FoldPodPage.NOW_PLAYING -> {
                controlInteractionCounter += 1

                when (nowPlayingControlMode) {
                    NowPlayingControlMode.SEEK -> {
                        if (state.canSeek) {
                            pendingSeekDeltaMs = WheelSeekPolicy.accumulate(
                                pendingSeekDeltaMs, direction * seekStepSeconds * 1_000L,
                                estimatedPosition(state, SystemClock.elapsedRealtime()), state.durationMs,
                            )
                        } else {
                            nowPlayingControlMessage = "이 플레이어는 탐색을 지원하지 않습니다"
                        }
                    }

                    NowPlayingControlMode.NORMAL -> {
                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            if (direction > 0) {
                                AudioManager.ADJUST_RAISE
                            } else {
                                AudioManager.ADJUST_LOWER
                            },
                            0,
                        )

                    }
                }
            }

            FoldPodPage.MAIN_MENU -> {
                mainMenuSelection =
                    (
                        mainMenuSelection +
                            direction
                        )
                        .coerceIn(
                            0,
                            MAIN_MENU_ITEMS.lastIndex,
                        )
            }

            FoldPodPage.PLAYLISTS -> {
                providerSelection = (providerSelection.coerceIn(0, providers.lastIndex.coerceAtLeast(0)) + direction).coerceIn(0, providers.lastIndex.coerceAtLeast(0))
            }

            FoldPodPage.PROVIDER_PLAYLISTS -> {
                playlistBrowserSelection = (playlistBrowserSelection.coerceIn(0, availablePlaylists.lastIndex.coerceAtLeast(0)) + direction)
                    .coerceIn(0, availablePlaylists.lastIndex.coerceAtLeast(0))
            }

            FoldPodPage.LIBRARY_TRACKS -> {
                libraryTracksSelection = (libraryTracksSelection + direction)
                    .coerceIn(0, selectedLibrary.tracks.lastIndex.coerceAtLeast(0))
            }

            FoldPodPage.CONNECTIONS -> {
                connectionsSelection = (connectionsSelection + direction).coerceIn(0, 6)
            }

            FoldPodPage.PLAYLIST -> {
                pendingTrackScreen = null
                if (state.queue.isNotEmpty()) {
                    val currentIndex = currentPlaylistIndex()
                    val nextSelection = QueueNavigationPolicy.stepSelection(
                        rememberedPlaylistSelection, state.sessionGeneration, playlistRows, direction,
                    )
                    if (nextSelection.index != currentIndex) {
                        rememberedPlaylistSelection = nextSelection
                    }
                }
            }

            FoldPodPage.PLAYER -> {
                val maxIndex =
                    if (playerSessions.isEmpty()) {
                        0
                    } else {
                        playerSessions.size
                    }

                playerSelection =
                    (
                        playerSelection +
                            direction
                        )
                        .coerceIn(
                            0,
                            maxIndex,
                        )
            }

            FoldPodPage.SETTINGS -> {
                settingsSelection =
                    (
                        settingsSelection +
                            direction
                        )
                        .coerceIn(0, 5)
            }

            FoldPodPage.WHEEL_SETTINGS -> {
                wheelSensitivity =
                    (
                        wheelSensitivity +
                            direction
                        )
                        .coerceIn(1, 10)

                settingsStore
                    .setWheelSensitivity(
                        wheelSensitivity
                    )
            }

            FoldPodPage.SEEK_SETTINGS -> {
                seekStepSeconds = (seekStepSeconds + direction).coerceIn(1, 60)
                settingsStore.setSeekStepSeconds(seekStepSeconds)
            }

            FoldPodPage.HAPTICS_SETTINGS -> {
                hapticsSelection =
                    (
                        hapticsSelection +
                            direction
                        )
                        .coerceIn(0, 1)
            }

            FoldPodPage.DISPLAY_SETTINGS -> {
                displaySettingsSelection =
                    (
                        displaySettingsSelection +
                            direction
                        )
                        .coerceIn(0, 2)
            }

            FoldPodPage.ABOUT -> Unit
            FoldPodPage.PLAYBACK_OPTIONS -> {
                playbackOptionsSelection = (playbackOptionsSelection + direction).coerceIn(0, 1)
            }
            FoldPodPage.SLEEP_TIMER -> {
                sleepTimerSelection = (sleepTimerSelection + direction).coerceIn(0, 4)
            }
        }
    }

    val latestWheelStep by rememberUpdatedState<(Int) -> Unit> { wheelStep(it) }
    val latestCenterPressed by rememberUpdatedState<() -> Unit> { centerPressed() }

    val backgroundBrush =
        if (isIdle) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.Black,
                        Color.Black,
                    )
            )
        } else {
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0.00f to
                            displayTheme.backgroundTop,
                        0.32f to
                            displayTheme.backgroundTop,
                        0.72f to
                            displayTheme.backgroundBottom,
                        1.00f to
                            displayTheme.backgroundBottom,
                    )
            )
        }

    val classicStatus = rememberClassicStatus(state.packageName, currentSourceName)
    if (showYouTubePlaylistLink) {
        YouTubePlaylistLinkDialog(
            library = youtubeLibrary,
            onDismiss = {
                showYouTubePlaylistLink = false
                interact()
            },
            onImport = { link ->
                interact()
                onYouTubeImportPlaylist(link)
            },
        )
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    backgroundBrush
                )
                .windowInsetsPadding(
                    WindowInsets.displayCutout
                )
                .clickable(
                    enabled = isIdle
                ) {
                    interact()
                }
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 6.dp,
                    bottom = 16.dp,
                ),
    ) {
        if (isIdle && !controlsLocked) {
            IdleScreen(
                state = state,
                nowElapsed = now,
                pixelShiftEnabled =
                    pixelShiftEnabled,
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Fit the whole classic face to both dimensions, including short/folded windows.
                val bodyWidth = minOf(maxWidth, maxHeight / 1.65f)
                val screenWidth = bodyWidth * 0.9f
                val screenHeight = screenWidth * 0.75f
                val wheelSize = bodyWidth * 0.76f
                val screenWheelGap = bodyWidth * 0.10f

                Column(
                    modifier = Modifier.size(bodyWidth, screenHeight + screenWheelGap + wheelSize),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(screenWidth, screenHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .border(3.dp, Color(0xFF161616), RoundedCornerShape(10.dp))
                                .padding(6.dp),
                    ) {
                        CompositionLocalProvider(LocalClassicStatus provides classicStatus) {
                        Column(Modifier.fillMaxSize().background(Color.White)) {
                        val status = when {
                            controlsLocked -> "HOLD · Hold MENU to unlock"
                            sleepTimer.active -> "Sleep · ${formatTime(sleepTimer.remainingMs)}"
                            else -> null
                        }
                        if (status != null) {
                            Text(status, color = ClassicSelectionBlue, fontSize = 10.sp, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                        }
                        AnimatedContent(
                            targetState = page,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            transitionSpec = {
                                val direction =
                                    if (navigationBackwards) {
                                        -1
                                    } else {
                                        1
                                    }

                                (
                                    slideInHorizontally(
                                        animationSpec = tween(170)
                                    ) { width ->
                                        direction * width / 5
                                    } +
                                        fadeIn(
                                            animationSpec = tween(140)
                                        )
                                    )
                                    .togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = tween(150)
                                        ) { width ->
                                            -direction * width / 5
                                        } +
                                            fadeOut(
                                                animationSpec = tween(120)
                                            )
                                    )
                            },
                            label = "foldPodPage",
                        ) { animatedPage ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                when (animatedPage) {
                                    FoldPodPage.NOW_PLAYING -> {
                                        NowPlayingScreen(
                                            state = state,
                                            nowElapsed = now,
                                            theme = displayTheme,
                                            controlMode =
                                                nowPlayingControlMode,
                                            seekPreviewMs =
                                                seekPreviewMs,
                                            seekStepSeconds = seekStepSeconds,
                                            controlMessage =
                                                nowPlayingControlMessage,
                                        )
                                    }

                                    FoldPodPage.MAIN_MENU -> {
                                        MainMenuScreen(
                                            selectedIndex =
                                                mainMenuSelection,
                                            sourceName =
                                                currentSourceName,
                                            playlistSubtitle =
                                                when {
                                                    accountPlaylists.isNotEmpty() ->
                                                        "${providers.size} providers"
                                                    state.queueTitle.isNotBlank() ->
                                                        state.queueTitle
                                                    state.queue.isNotEmpty() ->
                                                        "${state.queue.size} tracks"
                                                    else ->
                                                        "Current queue"
                                                },
                                            theme = displayTheme,
                                            state = state,
                                        )
                                    }

                                    FoldPodPage.PLAYLISTS -> {
                                        ClassicPlaylistProvidersScreen(providers, safeProviderSelection)
                                    }

                                    FoldPodPage.PROVIDER_PLAYLISTS -> {
                                        LibraryPlaylistBrowserScreen(
                                            playlists = availablePlaylists,
                                            selectedIndex = safePlaylistBrowserSelection,
                                            providerName = browsingProviderId?.let { providerName(it) } ?: "Playlists",
                                            favorites = playlistFavorites,
                                            library = providerLibrary,
                                        )
                                    }

                                    FoldPodPage.LIBRARY_TRACKS -> {
                                        LibraryTracksScreen(selectedLibrary, libraryTracksSelection,
                                            canOpenSelectedTrack = !selectedLibrary.loading && selectedLibrary.error == null &&
                                                when (selectedLibraryPlaylist?.providerId) {
                                                    "spotify" -> SpotifyTrackLink.forSelection(
                                                        selectedLibrary, selectedLibraryPlaylist, libraryTracksSelection,
                                                    ) != null
                                                    "youtube_music" -> YouTubeTrackLink.forSelection(
                                                        selectedLibrary, selectedLibraryPlaylist, libraryTracksSelection,
                                                    ) != null
                                                    else -> false
                                                })
                                    }

                                    FoldPodPage.CONNECTIONS -> {
                                        ConnectionsScreen(spotifyLibrary, youtubeLibrary, connectionsSelection,
                                            enabled = !controlsLocked, onAction = ::connectionAction)
                                    }

                                    FoldPodPage.PLAYLIST -> {
                                        ClassicPlaylistTracksScreen(
                                            state = state,
                                            selectedIndex =
                                                playlistSelection,
                                            theme = displayTheme,
                                        )
                                    }

                                    FoldPodPage.PLAYER -> {
                                        PlayerScreen(
                                            sessions =
                                                playerSessions,
                                            selectedIndex =
                                                playerSelection,
                                            currentSourceName =
                                                currentSourceName,
                                            theme = displayTheme,
                                        )
                                    }

                                    FoldPodPage.SETTINGS -> {
                                        SettingsScreen(
                                            selectedIndex =
                                                settingsSelection,
                                            wheelSensitivity =
                                                wheelSensitivity,
                                            idleModeEnabled =
                                                idleModeEnabled,
                                            seekStepSeconds = seekStepSeconds,
                                            theme = displayTheme,
                                        )
                                    }
                                    FoldPodPage.PLAYBACK_OPTIONS -> {
                                        PlaybackOptionsScreen(state, playbackOptionsSelection, displayTheme)
                                    }
                                    FoldPodPage.SLEEP_TIMER -> {
                                        SleepTimerScreen(sleepTimer, sleepTimerSelection, displayTheme)
                                    }

                                    FoldPodPage.WHEEL_SETTINGS -> {
                                        WheelSensitivityScreen(
                                            sensitivity =
                                                wheelSensitivity,
                                            onSensitivityChange = { value ->
                                                if (!controlsLocked) {
                                                wheelSensitivity = value
                                                settingsStore
                                                    .setWheelSensitivity(
                                                        value
                                                    )
                                                interact()
                                                }
                                            },
                                            theme = displayTheme,
                                            enabled = !controlsLocked,
                                        )
                                    }

                                    FoldPodPage.SEEK_SETTINGS -> {
                                        SeekStepSettingsScreen(
                                            seekStepSeconds = seekStepSeconds,
                                            onSeekStepChange = { value ->
                                                if (!controlsLocked) {
                                                    seekStepSeconds = value.coerceIn(1, 60)
                                                    settingsStore.setSeekStepSeconds(seekStepSeconds)
                                                    interact()
                                                }
                                            },
                                            theme = displayTheme,
                                            enabled = !controlsLocked,
                                        )
                                    }

                                    FoldPodPage.HAPTICS_SETTINGS -> {
                                        HapticsSettingsScreen(
                                            selectedIndex =
                                                hapticsSelection,
                                            wheelTickEnabled =
                                                wheelTickHapticsEnabled,
                                            buttonClickEnabled =
                                                buttonHapticsEnabled,
                                            theme = displayTheme,
                                        )
                                    }

                                    FoldPodPage.DISPLAY_SETTINGS -> {
                                        DisplaySettingsScreen(
                                            selectedIndex =
                                                displaySettingsSelection,
                                            idleModeEnabled =
                                                idleModeEnabled,
                                            idleDelaySeconds =
                                                idleDelaySeconds,
                                            pixelShiftEnabled =
                                                pixelShiftEnabled,
                                            theme = displayTheme,
                                        )
                                    }

                                    FoldPodPage.ABOUT -> {
                                        AboutScreen(
                                            theme = displayTheme,
                                        )
                                    }
                                }
                            }
                        }
                        }
                        }
                    }

                    Spacer(
                        Modifier.height(screenWheelGap)
                    )

                    ClickWheel(
                        modifier =
                            Modifier.size(wheelSize),
                        wheelColor =
                            displayTheme.wheel,
                        centerColor =
                            displayTheme.wheelCenter,
                        accentColor =
                            displayTheme.accent,
                        stepDegrees =
                            FoldPodSettingsStore
                                .wheelStepDegrees(
                                    wheelSensitivity
                                ),
                        wheelTickHapticsEnabled =
                            wheelTickHapticsEnabled,
                        buttonHapticsEnabled =
                            buttonHapticsEnabled,
                        /*
                         * Acceleration is useful for volume, but it makes list
                         * navigation skip rows and can desynchronise the visual
                         * highlight from the user's intent.
                         */
                        accelerationEnabled =
                            page ==
                                FoldPodPage.NOW_PLAYING &&
                                nowPlayingControlMode !=
                                    NowPlayingControlMode.SEEK,
                        // Local callable references compare equal despite changed captured snapshots.
                        // Updated handlers keep pointer callbacks on the latest state and seek context.
                        onWheelStep = { direction -> latestWheelStep(direction) },
                        onMenu =
                            ::menuPressed,
                        onPrevious = {
                            if (!controlsLocked) {
                            interact()
                            onPrevious()
                            }
                        },
                        onNext = {
                            if (!controlsLocked) {
                            interact()
                            onNext()
                            }
                        },
                        onPlayPause = {
                            if (!controlsLocked) {
                            interact()
                            onPlayPause()
                            }
                        },
                        onCenter = { latestCenterPressed() },
                        controlsLocked = controlsLocked,
                        onMenuLongPress = { toggleHold() },
                        onCenterLongPress = remember(state, playerSessions, spotifyLibrary, youtubeLibrary, selectedLibraryPlaylist, selectedProviderId, playlistFavorites) { { centerLongPressed() } },
                        onSeekHoldStep = remember(state, playerSessions) { { direction -> seekHeld(direction) } },
                        onInteraction =
                            ::interact,
                    )
                }
            }
        }
    }
}
