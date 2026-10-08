package com.foldpod.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTrackLinkTest {
    private val playlist = LibraryPlaylist("spotify", "37i9dQZF1DXcBWIGoYBM5M", "Own playlist")
    private val track = LibraryTrack("spotify", "4uLU6hMCjMI75M1A2tKUQC", "Selected")
    private val uri = "spotify:track:4uLU6hMCjMI75M1A2tKUQC"

    @Test fun buildsOnlyTheSelectedTrackUri() {
        assertEquals(uri, SpotifyTrackLink.create(playlist, track, 0))
        assertEquals(uri, SpotifyTrackLink.create(playlist, track, 8))
    }

    @Test fun rejectsMalformedIdsAndUriInjection() {
        listOf(null, "", "short", "a".repeat(23), "a".repeat(21),
            "spotify:track:4uLU6hMCjMI75M1A2tKUQC", "4uLU6hMCjMI75M1A2tKUQ_",
            "https://example.com").forEach { id ->
            assertNull(SpotifyTrackLink.create(playlist, track.copy(id = id), 0))
        }
        assertNull(SpotifyTrackLink.create(playlist.copy(id = "bad-playlist"), track, 0))
    }

    @Test fun rejectsLocalUnavailableAndProviderMismatch() {
        assertNull(SpotifyTrackLink.create(playlist, track.copy(isLocal = true), 0))
        assertNull(SpotifyTrackLink.create(playlist, track.copy(available = false), 0))
        assertNull(SpotifyTrackLink.create(playlist.copy(providerId = "youtube_music"), track, 0))
        assertNull(SpotifyTrackLink.create(playlist, track.copy(providerId = "youtube_music"), 0))
        assertNull(SpotifyTrackLink.create(playlist, track, -1))
    }

    @Test fun selectionRequiresMatchingSuccessfulLoadedPlaylistAndBounds() {
        val library = ProviderLibraryState(selectedPlaylist = playlist, tracks = listOf(track))
        assertEquals(uri, SpotifyTrackLink.forSelection(library, playlist, 0))
        assertNull(SpotifyTrackLink.forSelection(library.copy(loading = true), playlist, 0))
        assertNull(SpotifyTrackLink.forSelection(library.copy(error = "Load failed"), playlist, 0))
        assertNull(SpotifyTrackLink.forSelection(library.copy(selectedPlaylist = playlist.copy(id = "a".repeat(22))), playlist, 0))
        assertNull(SpotifyTrackLink.forSelection(library.copy(providerId = "youtube_music"), playlist, 0))
        assertNull(SpotifyTrackLink.forSelection(library, null, 0))
        assertNull(SpotifyTrackLink.forSelection(library, playlist, -1))
        assertNull(SpotifyTrackLink.forSelection(library, playlist, 1))
    }

    @Test fun duplicateTrackSelectionStillRejectsUnavailablePosition() {
        val library = ProviderLibraryState(selectedPlaylist = playlist,
            tracks = listOf(track, track.copy(available = false), track))
        assertEquals(uri, SpotifyTrackLink.forSelection(library, playlist, 2))
        assertNull(SpotifyTrackLink.forSelection(library, playlist, 1))
    }

    @Test fun playbackRequiresExactProviderIdentityAndPlaying() {
        assertTrue(SpotifyTrackLink.confirmsPlayback(uri, uri, null, true))
        assertTrue(SpotifyTrackLink.confirmsPlayback(uri, track.id, null, true))
        assertTrue(SpotifyTrackLink.confirmsPlayback(uri, null, uri, true))
        assertFalse(SpotifyTrackLink.confirmsPlayback(uri, uri, uri, false))
        assertFalse(SpotifyTrackLink.confirmsPlayback(uri, null, null, true))
        assertFalse(SpotifyTrackLink.confirmsPlayback(uri, "Selected", null, true))
        assertFalse(SpotifyTrackLink.confirmsPlayback(uri, "spotify:track:" + "a".repeat(22), null, true))
        assertFalse(SpotifyTrackLink.confirmsPlayback(uri, null, uri + "?other", true))
        assertFalse(SpotifyTrackLink.confirmsPlayback(track.id!!, track.id, null, true))
    }
}
