package com.foldpod.app.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.net.Uri
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

import com.foldpod.app.BuildConfig
import com.foldpod.app.library.LibraryPlaylist
import com.foldpod.app.library.LibraryTrack
import com.foldpod.app.library.YouTubeTrackLink
import com.foldpod.app.service.FoldPodNotificationListener
import com.foldpod.app.settings.FoldPodSettingsStore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow


class MediaControllerRepository private constructor(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val settingsStore = FoldPodSettingsStore(appContext)

    private val packageManager =
        appContext.packageManager

    private val sessionManager =
        appContext.getSystemService(
            MediaSessionManager::class.java
        )

    private val listenerComponent =
        ComponentName(
            appContext,
            FoldPodNotificationListener::class.java,
        )

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val _state =
        MutableStateFlow(
            NowPlayingState()
        )

    val state: StateFlow<NowPlayingState> =
        _state.asStateFlow()

    private val _sessions =
        MutableStateFlow<List<PlayerSessionInfo>>(
            emptyList()
        )

    val sessions: StateFlow<List<PlayerSessionInfo>> =
        _sessions.asStateFlow()

    private val _queueSelectionResults = MutableSharedFlow<QueueSelectionResult>(replay = 0, extraBufferCapacity = 16)
    val queueSelectionResults: SharedFlow<QueueSelectionResult> = _queueSelectionResults.asSharedFlow()

    private fun emitQueueSelectionResult(request: QueueSelectionRequest, succeeded: Boolean) {
        val result = QueueSelectionResult(request, succeeded)
        if (Looper.myLooper() == Looper.getMainLooper()) _queueSelectionResults.tryEmit(result)
        else mainHandler.post { _queueSelectionResults.tryEmit(result) }
    }

    private var controller: MediaController? = null
    private var sessionGeneration = 0L
    private var queueRevision = 0L
    private var publishedRows = emptyList<QueueRowIdentity>()
    private var navigation: DirectQueueNavigation? = null
    private var navigationUiRequest: QueueSelectionRequest? = null
    private var pendingQueueRequest: QueueSelectionRequest? = null
    private var pendingQueueUiRequest: QueueSelectionRequest? = null
    private var spotifyWebPlaybackBusyCheck: () -> Boolean = { false }
    private val spotifyWebPlaybackBusy: Boolean get() = spotifyWebPlaybackBusyCheck()
    private var navigationBlocked = false
    private var blockedQueueNavigation: BlockedQueueNavigation? = null
    private var metadataSequence = 0L
    private var dispatchedMetadataSequence = 0L
    private var navigationStartedPlaying = false
    private fun metadataFingerprint(metadata: MediaMetadata?): QueueMetadataFingerprint =
        QueueMetadataFingerprint(
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            metadata?.description?.mediaId, metadata?.description?.mediaUri?.toString(),
        )
    private val navigationTimeout = Runnable {
        if (navigation != null) failNavigation("Player did not confirm the selected track", quarantine = true)
    }

    private fun failNavigation(message: String, quarantine: Boolean = false) {
        if (quarantine && !navigationBlocked) {
            blockedQueueNavigation = BlockedQueueNavigation(
                _state.value.queue.firstOrNull { it.isActive }?.id
                    ?: controller?.playbackState?.activeQueueItemId ?: -1L,
                metadataSequence,
            )
        }
        val failedRequests = listOfNotNull(navigationUiRequest ?: navigation?.request,
            pendingQueueUiRequest ?: pendingQueueRequest).distinct()
        navigation = null
        navigationUiRequest = null
        pendingQueueRequest = null
        pendingQueueUiRequest = null
        failedRequests.forEach { emitQueueSelectionResult(it, false) }
        mainHandler.removeCallbacks(navigationTimeout)
        navigationBlocked = navigationBlocked || quarantine
        notifyQueueFailure(message)
    }

    private fun notifyQueueFailure(message: String) {
        Log.w("FoldPodQueue", "Queue selection stopped: $message")
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun invalidateNavigation() {
        val cancelledRequests = listOfNotNull(navigationUiRequest ?: navigation?.request,
            pendingQueueUiRequest ?: pendingQueueRequest).distinct()
        navigation = null
        navigationUiRequest = null
        pendingQueueRequest = null
        pendingQueueUiRequest = null
        cancelledRequests.forEach { emitQueueSelectionResult(it, false) }
        mainHandler.removeCallbacks(navigationTimeout)
        sessionGeneration += 1
        queueRevision = 0
        publishedRows = emptyList()
        navigationBlocked = false
        blockedQueueNavigation = null
    }

    /*
     * 사용자가 Player 화면에서 명시적으로 선택한 session.
     * 일시적으로 없으면 재생 중 session으로 fallback하되 저장된 선택은 유지한다.
     */
    private var preferredPackageName: String? = settingsStore.lastSelectedPlayerPackage


    private var controllerCallback = createControllerCallback()

    private fun createControllerCallback(generation: Long = sessionGeneration): MediaController.Callback =
        object : MediaController.Callback() {

            override fun onMetadataChanged(
                metadata: MediaMetadata?
            ) {
                if (generation != sessionGeneration) return
                metadataSequence += 1
                publish(
                    metadata,
                    controller?.playbackState,
                )
                acknowledgeNavigation()
                refreshSessionSummaries()
            }

            override fun onPlaybackStateChanged(
                playbackState: PlaybackState?
            ) {
                if (generation != sessionGeneration) return
                publish(
                    controller?.metadata,
                    playbackState,
                )
                acknowledgeNavigation()
                refreshSessionSummaries()
            }

            override fun onQueueChanged(
                queue: MutableList<MediaSession.QueueItem>?
            ) {
                if (generation != sessionGeneration) return
                publish(
                    controller?.metadata,
                    controller?.playbackState,
                )
            }

            override fun onQueueTitleChanged(
                title: CharSequence?
            ) {
                if (generation != sessionGeneration) return
                publish(
                    controller?.metadata,
                    controller?.playbackState,
                )
            }

            override fun onSessionDestroyed() {
                if (generation != sessionGeneration) return
                switchController(null)
                refreshActiveSession()
            }
        }


    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            chooseController(
                controllers.orEmpty()
            )
        }


    fun start() {
        try {
            sessionManager
                .addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    listenerComponent,
                    mainHandler,
                )

            refreshActiveSession()

        } catch (_: SecurityException) {
            _state.value = NowPlayingState()
            _sessions.value = emptyList()
        }
    }


    fun stop() {
        runCatching {
            sessionManager
                .removeOnActiveSessionsChangedListener(
                    sessionsChangedListener
                )
        }

        controller
            ?.unregisterCallback(
                controllerCallback
            )

        invalidateNavigation()
        controller = null
    }


    fun refreshActiveSession() {
        try {
            chooseController(
                sessionManager
                    .getActiveSessions(
                        listenerComponent
                    )
            )

        } catch (_: SecurityException) {
            _state.value = NowPlayingState()
            _sessions.value = emptyList()
        }
    }


    fun selectSession(
        packageName: String
    ) {
        try {
            val controllers =
                sessionManager
                    .getActiveSessions(
                        listenerComponent
                    )

            val target =
                controllers.firstOrNull {
                    it.packageName == packageName
                } ?: return

            preferredPackageName =
                packageName

            settingsStore.setLastSelectedPlayerPackage(packageName)

            switchController(
                target
            )

            publishSessions(
                controllers
            )

        } catch (_: SecurityException) {
            Unit
        }
    }


    private fun chooseController(
        controllers: List<MediaController>
    ) {
        val preferred = SessionPreferencePolicy.selectedIndex(preferredPackageName,
            controllers.map { SessionPreferenceCandidate(it.packageName,
                it.playbackState?.state == PlaybackState.STATE_PLAYING) })?.let { controllers[it] }

        switchController(
            preferred
        )

        publishSessions(
            controllers
        )
    }


    private fun switchController(
        preferred: MediaController?
    ) {
        if (
            preferred?.sessionToken ==
            controller?.sessionToken
        ) {
            publish(
                controller?.metadata,
                controller?.playbackState,
            )
            return
        }

        controller
            ?.unregisterCallback(
                controllerCallback
            )

        invalidateNavigation()
        controller = preferred
        controllerCallback = createControllerCallback()

        controller
            ?.registerCallback(
                controllerCallback,
                mainHandler,
            )

        publish(
            controller?.metadata,
            controller?.playbackState,
        )
    }


    private fun refreshSessionSummaries() {
        try {
            publishSessions(
                sessionManager.getActiveSessions(
                    listenerComponent
                )
            )
        } catch (_: SecurityException) {
            Unit
        }
    }


    private fun publishSessions(
        controllers: List<MediaController>
    ) {
        val selectedToken =
            controller?.sessionToken

        val liveSessions =
            controllers
                .map { item ->
                    val metadata = item.metadata
                    val playback = item.playbackState

                    PlayerSessionInfo(
                        packageName = item.packageName,
                        appName = appLabel(item.packageName),
                        title =
                            metadata?.getString(
                                MediaMetadata.METADATA_KEY_TITLE
                            )
                                ?: metadata?.getString(
                                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE
                                )
                                ?: "",
                        artist =
                            metadata?.getString(
                                MediaMetadata.METADATA_KEY_ARTIST
                            )
                                ?: metadata?.getString(
                                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                                )
                                ?: metadata?.getString(
                                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
                                )
                                ?: "",
                        isPlaying =
                            playback?.state ==
                                PlaybackState.STATE_PLAYING,
                        isSelected =
                            item.sessionToken == selectedToken,
                    )
                }

        val installed = listOf("com.spotify.music", "com.google.android.apps.youtube.music")
            .filter { packageName ->
                runCatching { appContext.packageManager.getApplicationInfo(packageName, 0) }.isSuccess
            }.map { PlayerSessionInfo(it, appLabel(it), hasSession = false) }
        _sessions.value = PlayerListPolicy.merge(liveSessions, installed)

    }


    private fun appLabel(
        packageName: String
    ): String {

        /*
         * Some media sessions expose a generic/localized app label.
         * Keep well-known music players deterministic in FoldPod.
         */
        return when (packageName) {

            "com.spotify.music" ->
                "Spotify"

            "com.google.android.apps.youtube.music" ->
                "YouTube Music"

            "com.google.android.youtube" ->
                "YouTube"

            else -> {
                try {
                    val info =
                        packageManager.getApplicationInfo(
                            packageName,
                            0,
                        )

                    packageManager
                        .getApplicationLabel(info)
                        .toString()

                } catch (_: PackageManager.NameNotFoundException) {
                    packageName.substringAfterLast('.')
                }
            }
        }
    }


    private fun publish(
        metadata: MediaMetadata?,
        playback: PlaybackState?,
    ) {
        val current =
            controller

        if (current == null) {
            _state.value =
                NowPlayingState()
            return
        }

        val artwork =
            metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ALBUM_ART
            )
                ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ART
                )
                ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON
                )

        val activeQueueId =
            playback?.activeQueueItemId
                ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()

        val rawQueue =
            current.queue
                .orEmpty()

        val rows = rawQueue.map { item ->
            QueueRowIdentity(item.queueId, item.description.mediaId,
                item.description.mediaUri?.toString(), item.description.title?.toString() ?: "Unknown title",
                item.description.subtitle?.toString().orEmpty())
        }
        val activeQueueIndex = QueueNavigationPolicy.activeIndex(
            rows, activeQueueId, metadata?.description?.mediaId, metadata?.description?.mediaUri?.toString(),
        )
        if (rows != publishedRows) {
            queueRevision += 1
            publishedRows = rows
            if (navigation?.refresh(rows) == false) {
                failNavigation("Queue changed; select the track again", quarantine = true)
            }
        }
        val blocked = blockedQueueNavigation
        if (navigationBlocked && blocked != null && QueueNavigationRecoveryPolicy.canRecover(
                blocked, rows, activeQueueId, metadataFingerprint(metadata), metadataSequence,
                playback?.state == PlaybackState.STATE_PLAYING || playback?.state == PlaybackState.STATE_PAUSED)) {
            navigationBlocked = false
            blockedQueueNavigation = null
        }

        val queueItems =
            rawQueue
                .mapIndexed { index, item ->
                    val description =
                        item.description

                    QueueItem(
                        id = item.queueId,
                        title =
                            description.title
                                ?.toString()
                                ?: "Unknown title",
                        subtitle =
                            description.subtitle
                                ?.toString()
                                ?: "",
                        artwork =
                            description.iconBitmap,
                        artworkUri = description.iconUri?.toString(),
                        mediaId = description.mediaId,
                        mediaUri = description.mediaUri?.toString(),
                        isActive =
                            index ==
                                activeQueueIndex,
                    )
                }

        _state.value =
            NowPlayingState(
                packageName =
                    current.packageName,
                title =
                    metadata?.getString(
                        MediaMetadata.METADATA_KEY_TITLE
                    )
                        ?: metadata?.getString(
                            MediaMetadata.METADATA_KEY_DISPLAY_TITLE
                        )
                        ?: "Unknown title",
                artist =
                    metadata?.getString(
                        MediaMetadata.METADATA_KEY_ARTIST
                    )
                        ?: metadata?.getString(
                            MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                        )
                        ?: metadata?.getString(
                            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
                        )
                        ?: "Unknown artist",
                album =
                    metadata?.getString(
                        MediaMetadata.METADATA_KEY_ALBUM
                    ).orEmpty(),
                artwork =
                    artwork,
                durationMs =
                    metadata?.getLong(
                        MediaMetadata.METADATA_KEY_DURATION
                    ) ?: 0L,
                positionMs =
                    playback?.position
                        ?: 0L,
                updateElapsedRealtimeMs =
                    playback?.lastPositionUpdateTime
                        ?: 0L,
                playbackSpeed =
                    playback?.playbackSpeed
                        ?: 0f,
                isPlaying =
                    playback?.state ==
                        PlaybackState.STATE_PLAYING,
                hasSession =
                    true,
                canSeek = (playback?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L,
                customActions = describeCustomActions(playback?.customActions.orEmpty()),
                sessionGeneration = sessionGeneration,
                queueRevision = queueRevision,
                queueTitle =
                    current.queueTitle
                        ?.toString()
                        .orEmpty(),
                queue =
                    queueItems,
            )
    }


    fun togglePlayPause() {
        val current =
            controller
                ?: return

        if (_state.value.isPlaying) {
            current.transportControls.pause()
        } else {
            current.transportControls.play()
        }
    }


    fun next() {
        controller
            ?.transportControls
            ?.skipToNext()
    }


    fun previous() {
        controller
            ?.transportControls
            ?.skipToPrevious()
    }


    fun seekTo(
        positionMs: Long
    ) {
        controller
            ?.transportControls
            ?.seekTo(
                positionMs.coerceAtLeast(0L)
            )
    }

    /** Seek from the selected session's live elapsed position, rather than a rendered UI snapshot. */
    fun seekBy(deltaMs: Long, expectedState: NowPlayingState): Boolean {
        if (deltaMs == 0L || expectedState.sessionGeneration != sessionGeneration ||
            !SeekTargetPolicy.matches(expectedState, _state.value)) return false
        val current = controller ?: return false
        if (current.packageName != expectedState.packageName) return false
        val playback = current.playbackState ?: return false
        if (playback.actions and PlaybackState.ACTION_SEEK_TO == 0L) return false
        val target = RelativeSeekPolicy.target(playback.position, playback.lastPositionUpdateTime,
            playback.playbackSpeed, playback.state == PlaybackState.STATE_PLAYING, SystemClock.elapsedRealtime(),
            current.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L, deltaMs) ?: return false
        return runCatching { current.transportControls.seekTo(target) }.isSuccess
    }

    private fun describeCustomActions(actions: List<PlaybackState.CustomAction>): List<MediaSessionAction> =
        actions.map { action ->
            val label = action.name.toString()
            MediaSessionAction(action.action, label, MediaSessionActionPolicy.semantic(label), sessionGeneration)
        }

    /** Send only the currently advertised exact action and its live extras; wait for session callbacks for state. */
    fun performCustomAction(request: MediaSessionAction): Boolean {
        val current = controller ?: return false
        val actions = current.playbackState?.customActions.orEmpty()
        val index = MediaSessionActionPolicy.matchingIndex(request, sessionGeneration, describeCustomActions(actions))
            ?: return false
        if (controller !== current || request.sessionGeneration != sessionGeneration) return false
        val action = actions[index]
        return runCatching { current.transportControls.sendCustomAction(action.action, action.extras) }.isSuccess
    }

    fun pauseSelectedSession(): Boolean {
        val current = controller ?: return false
        if ((current.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PAUSE == 0L) return false
        return runCatching { current.transportControls.pause() }.isSuccess
    }


    /** Null means the request was dispatched; playback remains confirmed only by live session metadata. */
    fun requestYouTubeLibraryTrack(playlist: LibraryPlaylist, track: LibraryTrack, selectedIndex: Int): String? {
        val link = YouTubeTrackLink.create(playlist, track, selectedIndex)
            ?: return "이 항목은 YouTube Music에서 재생할 수 없습니다"
        if (navigation != null || pendingQueueRequest != null || spotifyWebPlaybackBusy) {
            return "현재 곡 선택 요청을 처리 중입니다. 잠시 후 다시 선택해 주세요"
        }
        return try {
            val active = sessionManager.getActiveSessions(listenerComponent)
            val music = active.firstOrNull { it.packageName == "com.google.android.apps.youtube.music" }
                ?: return "YouTube Music에서 먼저 음악을 재생한 뒤 FoldPod에서 다시 선택해 주세요"
            if ((music.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_URI == 0L) {
                return "YouTube Music이 직접 곡 선택을 지원하지 않습니다. 음악 앱에서 곡을 선택해 주세요"
            }
            // The explicit library selection selects its provider's existing session without opening an app.
            preferredPackageName = music.packageName
            settingsStore.setLastSelectedPlayerPackage(music.packageName)
            switchController(music)
            publishSessions(active)
            music.transportControls.playFromUri(Uri.parse(link), null)
            null
        } catch (_: RuntimeException) {
            "YouTube Music에 재생을 요청할 수 없습니다. 음악 앱에서 먼저 재생한 뒤 다시 시도해 주세요"
        }
    }

    /** Select the existing local session and serialize Web API playback with queue commands. */
    fun beginSpotifyWebPlayback(): String? {
        if (navigation != null || pendingQueueRequest != null || spotifyWebPlaybackBusy)
            return "현재 곡 선택 요청을 처리 중입니다. 잠시 후 다시 선택해 주세요"
        return try {
            val active = sessionManager.getActiveSessions(listenerComponent)
            val spotify = active.firstOrNull { it.packageName == "com.spotify.music" }
                ?: return "이 휴대폰의 Spotify에서 먼저 음악을 재생해 주세요"
            preferredPackageName = spotify.packageName
            settingsStore.setLastSelectedPlayerPackage(spotify.packageName)
            switchController(spotify)
            publishSessions(active)
            null
        } catch (_: RuntimeException) {
            "Spotify 재생 세션을 확인할 수 없습니다. 휴대폰의 Spotify에서 먼저 재생해 주세요"
        }
    }

    fun setSpotifyWebPlaybackBusyCheck(check: () -> Boolean) { spotifyWebPlaybackBusyCheck = check }

    fun playQueueItem(request: QueueSelectionRequest) {
        playQueueItem(request, request)
    }

    private fun playQueueItem(request: QueueSelectionRequest, uiRequest: QueueSelectionRequest) {
        if (spotifyWebPlaybackBusy) {
            emitQueueSelectionResult(uiRequest, false)
            notifyQueueFailure("Spotify 곡 선택을 확인 중입니다. 잠시 후 다시 선택해 주세요")
            return
        }
        val current = controller ?: run {
            emitQueueSelectionResult(uiRequest, false)
            return
        }
        // Refresh the live source before comparing the displayed snapshot.
        publish(current.metadata, current.playbackState)
        val state = _state.value
        val rows = state.queue.map { it.identity() }
        if (navigationBlocked) {
            emitQueueSelectionResult(uiRequest, false)
            failNavigation("이전 곡 선택을 확인하지 못했습니다. 다음 곡으로 이동한 뒤 다시 선택해 주세요")
            return
        }
        if (!request.matches(sessionGeneration, queueRevision, rows)) {
            emitQueueSelectionResult(uiRequest, false)
            notifyQueueFailure("Queue changed; select the track again")
            return
        }
        val inFlight = navigation
        if (inFlight != null) {
            if (!QueueNavigationPolicy.identifiable(rows, request.index)) {
                emitQueueSelectionResult(uiRequest, false)
                notifyQueueFailure("Player does not expose a reliable identity for this track")
                return
            }
            // Latch the newest validated intent, but never interrupt a command already sent.
            val nextPending = if (rows.getOrNull(request.index) == inFlight.request.rows.getOrNull(inFlight.request.index)) null else request
            val nextPendingUi = if (nextPending == null) null else uiRequest
            (pendingQueueUiRequest ?: pendingQueueRequest)?.takeIf { it != nextPendingUi }
                ?.let { emitQueueSelectionResult(it, false) }
            pendingQueueRequest = nextPending
            pendingQueueUiRequest = nextPendingUi
            if (nextPending == null && uiRequest != (navigationUiRequest ?: inFlight.request))
                emitQueueSelectionResult(uiRequest, false)
            return
        }
        val active = resolveActiveIndex()
        if (active == request.index) {
            emitQueueSelectionResult(uiRequest, false)
            return
        }
        val playback = current.playbackState
        val capabilities = DirectQueueCapabilities(
            DirectQueuePolicy.supportsQueueJump(
                (playback?.actions ?: 0L) and PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM != 0L,
                current.packageName,
            ),
            (playback?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_URI != 0L,
            (playback?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_MEDIA_ID != 0L,
        )
        val command = DirectQueuePolicy.command(rows, request.index, active,
            playback?.activeQueueItemId ?: -1L, capabilities)
        logDirectCommand(current, request, capabilities, command)
        if (command == null) {
            emitQueueSelectionResult(uiRequest, false)
            notifyQueueFailure("Player does not support direct selection of this track")
            return
        }
        navigationStartedPlaying = state.isPlaying
        navigationUiRequest = uiRequest
        navigation = DirectQueueNavigation(request, command, active)
        dispatchNavigation()
    }

    private fun logDirectCommand(current: MediaController, request: QueueSelectionRequest,
        capabilities: DirectQueueCapabilities, command: DirectQueueCommand?) {
        if (!BuildConfig.DEBUG) return
        val target = request.rows[request.index]
        // Never log HTTP URLs, query strings, extras, or authentication data.
        fun safeIdentity(value: String?): String = when {
            value == null -> "null"
            value.contains("?") || value.contains("#") || value.contains("://") -> "<uri omitted>"
            else -> value.take(160).replace('\r', ' ').replace('\n', ' ')
        }
        val kind = when (command) {
            is DirectQueueCommand.SkipToQueueItem -> "skipToQueueItem"
            is DirectQueueCommand.PlayFromUri -> "playFromUri"
            is DirectQueueCommand.PlayFromMediaId -> "playFromMediaId"
            null -> "unsupported"
        }
        Log.d("FoldPodQueue", "direct=$kind package=${current.packageName} actions=${current.playbackState?.actions ?: 0L} " +
            "capabilities=$capabilities targetIndex=${request.index} queueId=${target.id} " +
            "mediaId=${safeIdentity(target.mediaId)} mediaUri=${safeIdentity(target.mediaUri)}")
    }

    private fun resolveActiveIndex(): Int? {
        val current = controller ?: return null
        return QueueNavigationPolicy.activeIndex(
            _state.value.queue.map { it.identity() }, current.playbackState?.activeQueueItemId ?: -1L,
            current.metadata?.description?.mediaId, current.metadata?.description?.mediaUri?.toString(),
        )
    }

    private fun dispatchNavigation() {
        val plan = navigation ?: return
        val current = controller ?: run {
            failNavigation("Player session ended", quarantine = true)
            return
        }
        publish(current.metadata, current.playbackState)
        if (navigation !== plan) return
        if (!plan.request.matches(sessionGeneration, queueRevision, publishedRows) ||
            !plan.canDispatch(publishedRows, resolveActiveIndex())) {
            failNavigation("Queue changed; select the track again", quarantine = true)
            return
        }
        // Revalidate the advertised capability and exact identity immediately before dispatch.
        val playback = current.playbackState
        val capabilities = DirectQueueCapabilities(
            DirectQueuePolicy.supportsQueueJump(
                (playback?.actions ?: 0L) and PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM != 0L,
                current.packageName,
            ),
            (playback?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_URI != 0L,
            (playback?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_MEDIA_ID != 0L,
        )
        if (DirectQueuePolicy.command(publishedRows, plan.request.index, resolveActiveIndex(),
            playback?.activeQueueItemId ?: -1L, capabilities) != plan.command) {
            failNavigation("Player no longer supports this direct selection")
            return
        }
        if (!plan.dispatch()) return
        mainHandler.removeCallbacks(navigationTimeout)
        dispatchedMetadataSequence = metadataSequence
        mainHandler.postDelayed(navigationTimeout, 5_000L)
        try {
            when (val command = plan.command) {
                is DirectQueueCommand.SkipToQueueItem -> current.transportControls.skipToQueueItem(command.id)
                is DirectQueueCommand.PlayFromUri -> current.transportControls.playFromUri(Uri.parse(command.uri), null)
                is DirectQueueCommand.PlayFromMediaId -> current.transportControls.playFromMediaId(command.mediaId, null)
            }
        } catch (_: RuntimeException) {
            failNavigation("Player could not select the track", quarantine = true)
        }
    }

    private fun resumePendingRequest(): Boolean {
        val request = pendingQueueRequest ?: return false
        val uiRequest = pendingQueueUiRequest ?: request
        pendingQueueRequest = null
        pendingQueueUiRequest = null
        navigation = null
        navigationUiRequest = null
        mainHandler.removeCallbacks(navigationTimeout)
        val rows = _state.value.queue.map { it.identity() }
        val index = DirectQueuePolicy.preservedIndex(request, rows)
        if (request.sessionGeneration != sessionGeneration || index == null) {
            emitQueueSelectionResult(uiRequest, false)
            failNavigation("Selected track is no longer in the queue")
            return true
        }
        playQueueItem(QueueSelectionRequest(sessionGeneration, queueRevision, rows, index), uiRequest)
        return true
    }

    private fun acknowledgeNavigation() {
        val plan = navigation ?: return
        if (plan.request.sessionGeneration != sessionGeneration) {
            failNavigation("Player session changed", quarantine = true)
            return
        }
        if (!plan.dispatched) return
        val active = resolveActiveIndex()
        val playbackState = controller?.playbackState?.state
        val ready = playbackState == PlaybackState.STATE_PLAYING ||
            (!navigationStartedPlaying && playbackState == PlaybackState.STATE_PAUSED)
        when (plan.acknowledge(publishedRows, active, metadataFingerprint(controller?.metadata),
            freshMetadata = metadataSequence > dispatchedMetadataSequence, ready = ready)) {
            QueueNavigationPlan.Acknowledgement.WAIT -> Unit
            QueueNavigationPlan.Acknowledgement.REJECT ->
                failNavigation("Player reported an unexpected queue position", quarantine = true)
            QueueNavigationPlan.Acknowledgement.COMPLETE -> {
                emitQueueSelectionResult(navigationUiRequest ?: plan.request, true)
                if (resumePendingRequest()) return
                navigation = null
                navigationUiRequest = null
                mainHandler.removeCallbacks(navigationTimeout)
            }
            QueueNavigationPlan.Acknowledgement.ADVANCE ->
                failNavigation("Player did not confirm the selected track", quarantine = true)
        }
    }


    companion object {
        @Volatile
        private var instance:
            MediaControllerRepository? = null

        fun get(
            context: Context
        ): MediaControllerRepository {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: MediaControllerRepository(
                            context
                        ).also {
                            instance = it
                        }
                }
        }
    }
}
