package com.foldpod.app.library

/** Provider-scoped identities and stable ordering for locally pinned account playlists. */
object PlaylistBrowserPolicy {
    private val providerPattern = Regex("[a-z][a-z0-9_.-]{0,79}")
    private val playlistPattern = Regex("[A-Za-z0-9_-]{1,150}")

    fun key(providerId: String, playlistId: String): String {
        require(providerPattern.matches(providerId) && playlistPattern.matches(playlistId))
        return "$providerId:$playlistId"
    }

    fun isValidKey(value: String): Boolean {
        val separator = value.indexOf(':')
        return separator > 0 && providerPattern.matches(value.substring(0, separator)) &&
            playlistPattern.matches(value.substring(separator + 1))
    }

    fun <T> order(items: List<T>, favorites: Set<String>, keyOf: (T) -> String): List<T> {
        val (pinned, others) = items.partition { keyOf(it) in favorites }
        return pinned + others
    }
}
