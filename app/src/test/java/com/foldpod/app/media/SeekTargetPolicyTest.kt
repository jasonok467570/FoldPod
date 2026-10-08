package com.foldpod.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekTargetPolicyTest {
    private val rendered = NowPlayingState(packageName = "player", sessionGeneration = 2,
        title = "Song", artist = "Artist", album = "Album", durationMs = 60_000,
        hasSession = true, canSeek = true,
        queue = listOf(QueueItem(1, "Song", mediaId = "song-a", isActive = true)))

    @Test fun advancingClockAndPlaybackCallbacksKeepTargetValid() {
        assertTrue(SeekTargetPolicy.matches(rendered,
            rendered.copy(positionMs = 20_000, updateElapsedRealtimeMs = 500, isPlaying = true)))
    }

    @Test fun sessionSwitchBetweenRenderAndDispatchRejectsPendingSeek() {
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(packageName = "other")))
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(sessionGeneration = 3)))
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(hasSession = false)))
    }

    @Test fun trackSwitchBetweenRenderAndDispatchRejectsPendingSeek() {
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(title = "Next song")))
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(artist = "Other artist")))
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(album = "Other album")))
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(durationMs = 90_000)))
    }

    @Test fun sameTitleDifferentQueueOccurrenceRejectsPendingSeek() {
        assertFalse(SeekTargetPolicy.matches(rendered, rendered.copy(
            queue = listOf(QueueItem(2, "Song", mediaId = "song-b", isActive = true)))))
    }
}
