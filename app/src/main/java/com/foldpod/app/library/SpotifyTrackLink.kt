package com.foldpod.app.library

/** Builds an exact Spotify track request from provider IDs, never a supplied URI. */
object SpotifyTrackLink {
    private val idPattern = Regex("[A-Za-z0-9]{22}")

    fun create(playlist: LibraryPlaylist, track: LibraryTrack, selectedIndex: Int): String? {
        if (playlist.providerId != "spotify" || track.providerId != "spotify" ||
            !playlist.id.matches(idPattern) || !track.available || track.isLocal || selectedIndex < 0
        ) return null
        val id = track.id?.takeIf { it.matches(idPattern) } ?: return null
        return "spotify:track:$id"
    }

    fun forSelection(library: ProviderLibraryState, playlist: LibraryPlaylist?, selectedIndex: Int): String? {
        if (playlist == null || library.loading || library.error != null ||
            library.providerId != playlist.providerId ||
            library.selectedPlaylist?.providerLibraryId != playlist.providerLibraryId
        ) return null
        val track = library.tracks.getOrNull(selectedIndex) ?: return null
        return create(playlist, track, selectedIndex)
    }

    /** Only exact provider identity confirms playback; display titles are insufficient. */
    fun confirmsPlayback(uri: String, mediaId: String?, mediaUri: String?, isPlaying: Boolean): Boolean {
        val id = uri.removePrefix("spotify:track:")
        return uri == "spotify:track:$id" && id.matches(idPattern) && isPlaying &&
            (mediaId == uri || mediaId == id || mediaUri == uri)
    }
}
