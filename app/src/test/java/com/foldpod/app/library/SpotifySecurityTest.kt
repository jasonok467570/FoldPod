package com.foldpod.app.library

import org.junit.Assert.*
import org.junit.Test

class SpotifySecurityTest {
    @Test fun pkceMatchesRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifySecurity.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun stateRejectsMissingOrAlteredValue() {
        assertTrue(SpotifySecurity.matchesState("expected", "expected"))
        assertFalse(SpotifySecurity.matchesState("expected", null))
        assertFalse(SpotifySecurity.matchesState("expected", "Expected"))
        assertFalse(SpotifySecurity.matchesState("expected", "expected-more"))
    }

    @Test fun verifierUsesUnpaddedUrlSafeEntropy() {
        val first = SpotifySecurity.randomValue()
        assertEquals(43, first.length)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertNotEquals(first, SpotifySecurity.randomValue())
    }

    @Test fun pagingOnlyPermitsSpotifyHttpsApi() {
        val valid = "https://api.spotify.com/v1/me/playlists?offset=50&limit=50"
        assertEquals(valid, SpotifySecurity.validateApiUrl(valid))
        listOf("http://api.spotify.com/v1/me/playlists", "https://evil.example/v1/me/playlists",
            "https://api.spotify.com.evil.example/v1/me/playlists", "https://secret@api.spotify.com/v1/me/playlists",
            "https://api.spotify.com:8443/v1/me/playlists", "https://api.spotify.com/oauth",
            "https://api.spotify.com/v1/me/playlists#fragment").forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { SpotifySecurity.validateApiUrl(candidate) }
        }
    }

    @Test fun providerQualifiedPlaylistIdentityDoesNotCollide() {
        assertNotEquals(LibraryPlaylist("spotify", "same", "Title").providerLibraryId,
            LibraryPlaylist("youtube", "same", "Title").providerLibraryId)
    }

    @Test fun absentPlaylistMetadataDoesNotInventZeroTracks() {
        assertNull(SpotifySecurity.playlistCount(null, null))
        assertNull(SpotifySecurity.playlistCount(-1, -1))
        assertNull(LibraryPlaylist("spotify", "metadataOnly", "Followed playlist").trackCount)
        assertEquals(0, SpotifySecurity.playlistCount(0, null))
        assertEquals(14, SpotifySecurity.playlistCount(null, 14))
        assertEquals(14, SpotifySecurity.playlistCount(-1, 14))
        assertEquals(25, SpotifySecurity.playlistCount(25, 14))
    }
}
