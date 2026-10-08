package com.foldpod.app.library

/** Builds an exact-video request only from validated provider IDs, never a supplied URI. */
object YouTubeTrackLink {
    fun create(playlist: LibraryPlaylist, track: LibraryTrack, selectedIndex: Int): String? {
        if (playlist.providerId != "youtube_music" || track.providerId != "youtube_music" ||
            !track.available || track.isLocal || selectedIndex < 0 ||
            !YouTubePlaylistLink.isValidId(playlist.id)
        ) return null
        val videoId = track.id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return null
        return "https://music.youtube.com/watch?v=$videoId"
    }

    fun forSelection(library: ProviderLibraryState, playlist: LibraryPlaylist?, selectedIndex: Int): String? {
        if (playlist == null || library.loading || library.providerId != playlist.providerId ||
            library.selectedPlaylist?.providerLibraryId != playlist.providerLibraryId
        ) return null
        val track = library.tracks.getOrNull(selectedIndex) ?: return null
        return create(playlist, track, selectedIndex)
    }
}
