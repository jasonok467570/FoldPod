package com.foldpod.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTrackLinkTest {
    private val playlist = LibraryPlaylist("youtube_music", "PL1234567890_abcdefgh-XYZ", "Shared")
    private val track = LibraryTrack("youtube_music", "AbCdEf12_-3", "Selected")

    @Test fun usesOnlyExactVideoRegardlessOfImportedPosition() {
        assertEquals("https://music.youtube.com/watch?v=AbCdEf12_-3",
            YouTubeTrackLink.create(playlist, track, 0))
        assertEquals("https://music.youtube.com/watch?v=AbCdEf12_-3",
            YouTubeTrackLink.create(playlist, track, 7))
    }

    @Test fun rejectsUnavailableLocalAndOtherProviderItems() {
        assertNull(YouTubeTrackLink.create(playlist, track.copy(available = false), 0))
        assertNull(YouTubeTrackLink.create(playlist, track.copy(isLocal = true), 0))
        assertNull(YouTubeTrackLink.create(playlist.copy(providerId = "spotify"), track, 0))
        assertNull(YouTubeTrackLink.create(playlist, track.copy(providerId = "spotify"), 0))
        assertNull(YouTubeTrackLink.create(playlist, track, -1))
    }

    @Test fun rejectsMalformedIdsAndUriInjection() {
        listOf(null, "", "short", "AbCdEf12_-34", "AbCdEf12&-3", "AbCdEf12/-3", "https://evil.example").forEach { id ->
            assertNull(YouTubeTrackLink.create(playlist, track.copy(id = id), 0))
        }
        listOf("short", "PL1234567890&v=other", "PL1234567890/bad", "a".repeat(151)).forEach { id ->
            assertNull(YouTubeTrackLink.create(playlist.copy(id = id), track, 0))
        }
    }

    @Test fun selectionRequiresMatchingLoadedPlaylistAndExistingIndex() {
        val library = ProviderLibraryState(providerId = "youtube_music", selectedPlaylist = playlist, tracks = listOf(track))
        assertEquals(YouTubeTrackLink.create(playlist, track, 0), YouTubeTrackLink.forSelection(library, playlist, 0))
        assertNull(YouTubeTrackLink.forSelection(library.copy(loading = true), playlist, 0))
        assertNull(YouTubeTrackLink.forSelection(library.copy(selectedPlaylist = playlist.copy(id = "PLother123456")), playlist, 0))
        assertNull(YouTubeTrackLink.forSelection(library.copy(providerId = "spotify"), playlist, 0))
        assertNull(YouTubeTrackLink.forSelection(library, null, 0))
        assertNull(YouTubeTrackLink.forSelection(library, playlist, -1))
        assertNull(YouTubeTrackLink.forSelection(library, playlist, 1))
    }

    @Test fun duplicateVideosValidateSelectedImportedPosition() {
        val library = ProviderLibraryState(providerId = "youtube_music", selectedPlaylist = playlist,
            tracks = listOf(track, track.copy(available = false), track))
        assertEquals("https://music.youtube.com/watch?v=AbCdEf12_-3",
            YouTubeTrackLink.forSelection(library, playlist, 2))
        assertNull(YouTubeTrackLink.forSelection(library, playlist, 1))
    }
}
