package com.foldpod.app.ui

import com.foldpod.app.library.LibraryPlaylist
import com.foldpod.app.library.YouTubeLibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistProviderPolicyTest {
    @Test fun musicPackagesMatchTheirActualAccountLibraryIds() {
        val spotify = LibraryPlaylist("spotify", "saved", "Saved Spotify")
        val youtube = LibraryPlaylist(YouTubeLibraryRepository.PROVIDER_ID, "PL_saved", "Saved YouTube")
        val playlists = listOf(spotify, youtube)
        assertEquals(listOf(spotify), playlists.filter {
            it.providerId == playlistProviderId("com.spotify.music")
        })
        assertEquals(listOf(youtube), playlists.filter {
            it.providerId == playlistProviderId("com.google.android.apps.youtube.music")
        })
        assertEquals("youtube_music:PL_saved", youtube.providerLibraryId)
    }

    @Test fun normalYouTubeHasItsOwnCurrentQueueFolder() {
        assertEquals("com.google.android.youtube", playlistProviderId("com.google.android.youtube"))
    }

    @Test fun otherPlayersKeepPackageIdentityAndMissingSourceHasNoFolder() {
        assertEquals("org.example.player", playlistProviderId("org.example.player"))
        assertNull(playlistProviderId(null))
    }
}
