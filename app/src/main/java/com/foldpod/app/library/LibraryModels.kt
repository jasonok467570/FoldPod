package com.foldpod.app.library

data class LibraryPlaylist(
    val providerId: String,
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val trackCount: Int? = null,
) {
    val providerLibraryId: String get() = "$providerId:$id"
}

data class LibraryTrack(
    val providerId: String,
    val id: String?,
    val name: String,
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val available: Boolean = true,
    val isLocal: Boolean = false,
)

/** Provider library data is independent of the MediaSession active queue. */
data class ProviderLibraryState(
    val providerId: String = "spotify",
    val connected: Boolean = false,
    val loading: Boolean = false,
    val playlists: List<LibraryPlaylist> = emptyList(),
    val selectedPlaylist: LibraryPlaylist? = null,
    val tracks: List<LibraryTrack> = emptyList(),
    val error: String? = null,
    val accountScopeNote: String = "Spotify playlists · Playback requires Premium + Spotify Connect",
)
