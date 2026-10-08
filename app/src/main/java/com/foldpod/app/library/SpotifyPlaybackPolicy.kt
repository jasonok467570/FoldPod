package com.foldpod.app.library

internal data class SpotifyPlaybackDevice(
    val id: String?, val type: String, val active: Boolean, val restricted: Boolean,
    val name: String = "",
)

/** Pure boundaries for Spotify Web API playback; never changes the active device. */
internal object SpotifyPlaybackPolicy {
    val playbackScopes = setOf("user-read-playback-state", "user-modify-playback-state")
    val connectScopes = setOf("playlist-read-private", "playlist-read-collaborative") + playbackScopes

    fun grantedScopes(response: String?, previous: Set<String> = emptySet()): Set<String> =
        response?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.toSet() ?: previous

    fun payload(library: ProviderLibraryState, playlist: LibraryPlaylist, track: LibraryTrack, index: Int): String? {
        if (SpotifyTrackLink.forSelection(library, playlist, index) == null || library.tracks.getOrNull(index) != track) return null
        return "{\"context_uri\":\"spotify:playlist:${playlist.id}\",\"offset\":{\"position\":$index},\"position_ms\":0}"
    }

    /** Explicit device_id playback can start a paused device after the user confirms it. */
    fun playbackPhone(devices: List<SpotifyPlaybackDevice>, approvedId: String? = null): SpotifyPlaybackDevice? {
        // Spotify reports this Fold's session as Tablet, including its cover screen.
        val phones = devices.filter { !it.restricted && it.type in setOf("Smartphone", "Tablet") &&
            !it.id.isNullOrBlank() && it.name.isNotBlank() }
        if (approvedId != null) return phones.filter { it.id == approvedId }.singleOrNull()
        return phones.singleOrNull() ?: phones.filter { it.active }.singleOrNull()
    }

    fun remoteIndexMatches(selected: LibraryTrack, remote: LibraryTrack?): Boolean =
        remote != null && selected.providerId == "spotify" && remote.providerId == "spotify" &&
            selected.id != null && selected.id == remote.id && remote.available && !remote.isLocal

    fun confirmsPlayback(trackId: String?, expectedTrackId: String, playing: Boolean,
        deviceId: String?, expectedDeviceId: String, contextUri: String?, expectedPlaylistId: String): Boolean =
        playing && trackId == expectedTrackId && deviceId == expectedDeviceId &&
            contextUri == "spotify:playlist:$expectedPlaylistId"

    fun mutationUncertain(attempted: Boolean, accepted: Boolean, confirmed: Boolean, failureStatus: Int?): Boolean =
        attempted && !confirmed && (accepted || failureStatus !in setOf(400, 401, 403, 404, 422, 429))
}
