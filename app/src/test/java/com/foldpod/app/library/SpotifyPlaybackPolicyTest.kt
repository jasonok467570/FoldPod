package com.foldpod.app.library

import org.junit.Assert.*
import org.junit.Test

class SpotifyPlaybackPolicyTest {
    private val playlist = LibraryPlaylist("spotify", "37i9dQZF1DXcBWIGoYBM5M", "Own playlist")
    private val track = LibraryTrack("spotify", "4uLU6hMCjMI75M1A2tKUQC", "Selected")
    private val phone = SpotifyPlaybackDevice("phone", "Smartphone", true, false, "My Fold phone")

    @Test fun payloadUsesOriginalIndexIncludingNullAndUnavailablePositions() {
        val unavailable = LibraryTrack("spotify", null, "Unavailable", available = false)
        val library = ProviderLibraryState(selectedPlaylist = playlist, tracks = listOf(unavailable, track, track))
        assertEquals("{\"context_uri\":\"spotify:playlist:37i9dQZF1DXcBWIGoYBM5M\",\"offset\":{\"position\":1},\"position_ms\":0}",
            SpotifyPlaybackPolicy.payload(library, playlist, track, 1))
        assertEquals("{\"context_uri\":\"spotify:playlist:37i9dQZF1DXcBWIGoYBM5M\",\"offset\":{\"position\":2},\"position_ms\":0}",
            SpotifyPlaybackPolicy.payload(library, playlist, track, 2))
        assertNull(SpotifyPlaybackPolicy.payload(library, playlist, unavailable, 0))
    }

    @Test fun staleSelectionAndInvalidBoundsCannotProduceMutationBody() {
        val library = ProviderLibraryState(selectedPlaylist = playlist, tracks = listOf(track))
        assertNull(SpotifyPlaybackPolicy.payload(library, playlist, track.copy(id = "a".repeat(22)), 0))
        assertNull(SpotifyPlaybackPolicy.payload(library.copy(loading = true), playlist, track, 0))
        assertNull(SpotifyPlaybackPolicy.payload(library.copy(error = "Failed"), playlist, track, 0))
        assertNull(SpotifyPlaybackPolicy.payload(library.copy(selectedPlaylist = null), playlist, track, 0))
        assertNull(SpotifyPlaybackPolicy.payload(library, playlist, track, -1))
        assertNull(SpotifyPlaybackPolicy.payload(library, playlist, track, 1))
    }

    @Test fun deviceSelectionRejectsDesktopRestrictedAndAmbiguousPhones() {
        assertEquals(phone, SpotifyPlaybackPolicy.playbackPhone(listOf(phone)))
        assertEquals(phone.copy(type = "Tablet"), SpotifyPlaybackPolicy.playbackPhone(listOf(phone.copy(type = "Tablet"))))
        assertNull(SpotifyPlaybackPolicy.playbackPhone(listOf(phone.copy(type = "Computer"))))
        assertEquals(phone.copy(active = false), SpotifyPlaybackPolicy.playbackPhone(listOf(phone.copy(active = false))))
        assertNull(SpotifyPlaybackPolicy.playbackPhone(listOf(phone.copy(restricted = true))))
        assertNull(SpotifyPlaybackPolicy.playbackPhone(listOf(phone.copy(id = null))))
        assertNull(SpotifyPlaybackPolicy.playbackPhone(listOf(phone.copy(name = ""))))
        assertNull(SpotifyPlaybackPolicy.playbackPhone(listOf(phone, phone.copy(id = "other"))))
        assertEquals(phone, SpotifyPlaybackPolicy.playbackPhone(listOf(phone, phone.copy(type = "Computer"))))
    }

    @Test fun scopeRefreshPreservesOnlyOmittedScopesAndOldRecordsGrantNothing() {
        val scopes = SpotifyPlaybackPolicy.playbackScopes
        assertEquals(emptySet<String>(), SpotifyPlaybackPolicy.grantedScopes(null))
        assertEquals(scopes, SpotifyPlaybackPolicy.grantedScopes(null, scopes))
        assertEquals(setOf("playlist-read-private"), SpotifyPlaybackPolicy.grantedScopes("playlist-read-private", scopes))
        assertEquals(emptySet<String>(), SpotifyPlaybackPolicy.grantedScopes("", scopes))
        assertTrue(SpotifyPlaybackPolicy.connectScopes.containsAll(scopes))
        assertEquals(setOf("a", "b"), SpotifyPlaybackPolicy.grantedScopes(" a  b a "))
    }

    @Test fun approvedInactivePhoneCannotBeReplacedByAnotherActivePhone() {
        val approved = phone.copy(active = false)
        val other = phone.copy(id = "other")
        assertEquals(approved, SpotifyPlaybackPolicy.playbackPhone(listOf(approved, other), "phone"))
        assertNull(SpotifyPlaybackPolicy.playbackPhone(listOf(other), "phone"))
        assertNull(SpotifyPlaybackPolicy.playbackPhone(listOf(approved.copy(restricted = true)), "phone"))
    }

    @Test fun changedRemotePositionCannotStartAnotherTrack() {
        assertTrue(SpotifyPlaybackPolicy.remoteIndexMatches(track, track))
        // An insertion before the cached position moves a different ID into that offset.
        assertFalse(SpotifyPlaybackPolicy.remoteIndexMatches(track, track.copy(id = "a".repeat(22))))
        assertFalse(SpotifyPlaybackPolicy.remoteIndexMatches(track, null))
        assertFalse(SpotifyPlaybackPolicy.remoteIndexMatches(track, track.copy(id = null)))
        assertFalse(SpotifyPlaybackPolicy.remoteIndexMatches(track, track.copy(available = false)))
        assertFalse(SpotifyPlaybackPolicy.remoteIndexMatches(track, track.copy(isLocal = true)))
        assertFalse(SpotifyPlaybackPolicy.remoteIndexMatches(track, track.copy(providerId = "youtube_music")))
        assertFalse(SpotifyPlaybackPolicy.remoteIndexMatches(track.copy(id = null), track.copy(id = null)))
    }

    @Test fun confirmationRequiresPlayingExactTrackDeviceAndPlaylistContext() {
        fun confirmed(trackId: String? = track.id, playing: Boolean = true, device: String? = "phone",
            context: String? = "spotify:playlist:${playlist.id}") =
            SpotifyPlaybackPolicy.confirmsPlayback(trackId, track.id!!, playing, device, "phone", context, playlist.id)
        assertTrue(confirmed())
        assertFalse(confirmed(playing = false))
        assertFalse(confirmed(trackId = null))
        assertFalse(confirmed(trackId = "a".repeat(22)))
        assertFalse(confirmed(device = "desktop"))
        assertFalse(confirmed(context = null))
        assertFalse(confirmed(context = "spotify:playlist:" + "a".repeat(22)))
    }

    @Test fun unknownMutationResponsesStayBlockedButExplicitRejectionsCanRetry() {
        assertFalse(SpotifyPlaybackPolicy.mutationUncertain(false, false, false, null))
        assertTrue(SpotifyPlaybackPolicy.mutationUncertain(true, false, false, null))
        assertTrue(SpotifyPlaybackPolicy.mutationUncertain(true, false, false, 500))
        assertTrue(SpotifyPlaybackPolicy.mutationUncertain(true, false, false, 503))
        listOf(400, 401, 403, 404, 422, 429).forEach { status ->
            assertFalse(SpotifyPlaybackPolicy.mutationUncertain(true, false, false, status))
            assertTrue(SpotifyPlaybackPolicy.mutationUncertain(true, true, false, status))
        }
        assertFalse(SpotifyPlaybackPolicy.mutationUncertain(true, true, true, null))
    }
}
