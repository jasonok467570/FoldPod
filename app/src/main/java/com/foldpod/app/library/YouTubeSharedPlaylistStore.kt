package com.foldpod.app.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local registrations only. No credentials or remote playlist mutations. */
internal class YouTubeSharedPlaylistStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("youtube_shared_playlists", Context.MODE_PRIVATE)

    fun read(): List<LibraryPlaylist> = try {
        val items = JSONArray(preferences.getString("playlists", "[]"))
        (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val title = item.optString("title")
            if (!YouTubePlaylistLink.isValidId(id) || id.startsWith("RD") || title.isBlank()) return@mapNotNull null
            LibraryPlaylist(
                providerId = YouTubeLibraryRepository.PROVIDER_ID,
                id = id,
                name = title,
                artworkUrl = item.optString("artwork").takeIf { it.startsWith("https://") },
                trackCount = if (item.has("count") && !item.isNull("count")) {
                    item.optInt("count", -1).takeIf { it >= 0 }
                } else null,
            )
        }.distinctBy { it.id }
    } catch (_: Exception) {
        emptyList()
    }

    fun write(playlists: List<LibraryPlaylist>) {
        val items = JSONArray()
        playlists.distinctBy { it.id }.forEach { playlist ->
            items.put(JSONObject().apply {
                put("id", playlist.id)
                put("title", playlist.name)
                put("artwork", playlist.artworkUrl ?: JSONObject.NULL)
                put("count", playlist.trackCount ?: JSONObject.NULL)
            })
        }
        preferences.edit().putString("playlists", items.toString()).apply()
    }
}
