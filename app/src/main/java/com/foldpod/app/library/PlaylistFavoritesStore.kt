package com.foldpod.app.library

import android.content.Context

/** Playlist pins are local to FoldPod and never modify a provider's library. */
class PlaylistFavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playlist_favorites", Context.MODE_PRIVATE)

    fun read(): Set<String> = prefs.getStringSet("keys", emptySet()).orEmpty()
        .filterTo(mutableSetOf(), PlaylistBrowserPolicy::isValidKey)

    fun toggle(providerId: String, playlistId: String): Set<String> {
        val key = PlaylistBrowserPolicy.key(providerId, playlistId)
        val updated = read().toMutableSet()
        if (!updated.remove(key)) updated.add(key)
        prefs.edit().putStringSet("keys", updated.toSet()).apply()
        return updated.toSet()
    }
}
