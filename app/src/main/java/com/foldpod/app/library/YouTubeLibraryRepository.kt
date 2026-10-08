package com.foldpod.app.library

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Read-only owned and locally registered shared playlists, independent from the player's queue. */
class YouTubeLibraryRepository(private val scope: CoroutineScope, context: Context) {
    private val sharedStore = YouTubeSharedPlaylistStore(context)
    private var sharedPlaylists = sharedStore.read()
    private var ownedPlaylists = emptyList<LibraryPlaylist>()
    private val mutableState = MutableStateFlow(emptyState().copy(playlists = sharedPlaylists))
    val state: StateFlow<ProviderLibraryState> = mutableState.asStateFlow()
    private var token: String? = null
    private var generation = 0
    private var requestJob: Job? = null

    fun refresh(accessToken: String) {
        val requestGeneration = nextRequest()
        token = accessToken
        mutableState.value = emptyState().copy(connected = true, loading = true, playlists = mergedPlaylists())
        requestJob = scope.launch {
            try {
                val playlists = withContext(Dispatchers.IO) {
                    fetchPages("playlists", mapOf("mine" to "true"), accessToken).map { playlistMetadata(it) }
                }
                if (generation == requestGeneration) {
                    ownedPlaylists = playlists
                    mutableState.value = mutableState.value.copy(loading = false, playlists = mergedPlaylists())
                }
            } catch (error: Exception) {
                handleFailure(error, requestGeneration)
            }
        }
    }

    fun importSharedPlaylist(link: String) {
        val playlistId = try {
            YouTubePlaylistLink.parse(link)
        } catch (error: YouTubePlaylistLink.InvalidLink) {
            showError(error.message.orEmpty())
            return
        }
        val accessToken = token ?: run {
            showError("Google에 연결한 뒤 공유 재생목록을 추가해 주세요.")
            return
        }
        val requestGeneration = nextRequest()
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        requestJob = scope.launch {
            try {
                val playlist = withContext(Dispatchers.IO) {
                    val items = fetchPages(
                        "playlists", mapOf("id" to playlistId), accessToken,
                        part = "snippet,contentDetails,status",
                    )
                    val item = items.singleOrNull { it.optString("id") == playlistId }
                        ?: throw InaccessiblePlaylistException()
                    val privacy = item.optJSONObject("status")?.optString("privacyStatus")
                    if (privacy != "public" && privacy != "unlisted") throw InaccessiblePlaylistException()
                    playlistMetadata(item, strict = true)
                }
                currentCoroutineContext().ensureActive()
                if (generation == requestGeneration) {
                    // Save only after API metadata validation and the stale-request check.
                    val updated = (sharedPlaylists.filterNot { it.id == playlistId } + playlist)
                    sharedStore.write(updated)
                    sharedPlaylists = updated
                    mutableState.value = mutableState.value.copy(loading = false, playlists = mergedPlaylists())
                }
            } catch (error: Exception) {
                handleFailure(error, requestGeneration, importing = true)
            }
        }
    }

    private fun mergedPlaylists(): List<LibraryPlaylist> = (ownedPlaylists + sharedPlaylists).distinctBy { it.id }

    private fun playlistMetadata(item: JSONObject, strict: Boolean = false): LibraryPlaylist {
        val id = item.getString("id")
        val snippet = if (strict) item.getJSONObject("snippet") else item.optJSONObject("snippet") ?: JSONObject()
        val title = if (strict) snippet.getString("title") else snippet.optString("title", "이름 없는 재생목록")
        if (strict && (!YouTubePlaylistLink.isValidId(id) || title.isBlank())) {
            throw IOException("Invalid playlist metadata")
        }
        return LibraryPlaylist(
            providerId = PROVIDER_ID,
            id = id,
            name = title,
            artworkUrl = artwork(snippet),
            trackCount = item.optJSONObject("contentDetails")?.let { details ->
                if (details.has("itemCount") && !details.isNull("itemCount")) {
                    if (strict) details.optInt("itemCount", -1).takeIf { it >= 0 } else details.optInt("itemCount")
                } else null
            },
        )
    }

    fun loadTracks(playlist: LibraryPlaylist) {
        if (playlist.providerId != PROVIDER_ID) return
        val accessToken = token ?: run {
            showError("Google에 연결한 뒤 재생목록을 선택해 주세요.")
            return
        }
        val requestGeneration = nextRequest()
        mutableState.value = mutableState.value.copy(
            loading = true, selectedPlaylist = playlist, tracks = emptyList(), error = null,
        )
        requestJob = scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    fetchPages("playlistItems", mapOf("playlistId" to playlist.id), accessToken).map { item ->
                        val snippet = item.optJSONObject("snippet") ?: JSONObject()
                        val videoId = item.optJSONObject("contentDetails")?.optString("videoId")
                            ?.takeIf { it.isNotBlank() }
                        val title = snippet.optString("title", "이름 없는 동영상")
                        LibraryTrack(
                            providerId = PROVIDER_ID,
                            id = videoId,
                            name = title,
                            // YouTube exposes the owner's channel, not verified music artist/album metadata.
                            artist = snippet.optString("videoOwnerChannelTitle"),
                            artworkUrl = artwork(snippet),
                            available = videoId != null && title != "Private video" && title != "Deleted video",
                        )
                    }
                }
                if (generation == requestGeneration) {
                    mutableState.value = mutableState.value.copy(loading = false, tracks = tracks)
                }
            } catch (error: Exception) {
                handleFailure(error, requestGeneration)
            }
        }
    }

    fun showError(message: String) {
        nextRequest()
        mutableState.value = mutableState.value.copy(loading = false, error = message)
    }

    fun showConnectionError(message: String) {
        nextRequest()
        token = null
        ownedPlaylists = emptyList()
        mutableState.value = emptyState().copy(playlists = sharedPlaylists, error = message)
    }

    fun disconnect() {
        nextRequest()
        token = null
        ownedPlaylists = emptyList()
        mutableState.value = emptyState().copy(playlists = sharedPlaylists)
    }

    fun close() = disconnect()

    private fun nextRequest(): Int {
        requestJob?.cancel()
        return ++generation
    }

    private fun handleFailure(error: Exception, requestGeneration: Int, importing: Boolean = false) {
        if (error is CancellationException) throw error
        if (generation != requestGeneration) return
        val unauthorized = error is YouTubeHttpException && error.status == 401
        if (unauthorized) token = null
        val message = when {
            error is InaccessiblePlaylistException || (importing && error is YouTubeHttpException && error.status == 404) ->
                "공개·일부 공개 재생목록만 추가할 수 있습니다. 링크와 접근 권한을 확인해 주세요."
            unauthorized -> "Google 권한이 만료되었습니다. 연결 버튼으로 다시 인증해 주세요."
            error is YouTubeHttpException && error.status == 403 ->
                "YouTube 접근이 거부되었습니다. 읽기 권한, YouTube Data API 활성화 및 할당량을 확인해 주세요."
            error is YouTubeHttpException && error.status == 404 -> "재생목록을 찾을 수 없습니다. 목록을 새로고침해 주세요."
            else -> "YouTube 목록을 불러오지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요."
        }
        mutableState.value = mutableState.value.copy(connected = !unauthorized, loading = false, error = message)
    }

    private suspend fun fetchPages(
        endpoint: String,
        parameters: Map<String, String>,
        accessToken: String,
        part: String = "snippet,contentDetails",
    ): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        val seenTokens = mutableSetOf<String>()
        var pageToken: String? = null
        do {
            currentCoroutineContext().ensureActive()
            val query = parameters + mapOf("part" to part, "maxResults" to "50") +
                pageToken?.let { mapOf("pageToken" to it) }.orEmpty()
            val response = request(endpoint, query, accessToken)
            val pageItems = response.getJSONArray("items")
            for (index in 0 until pageItems.length()) items += pageItems.getJSONObject(index)
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (pageToken != null && !seenTokens.add(pageToken)) throw IOException("Repeated page token")
        } while (pageToken != null)
        return items
    }

    private fun request(endpoint: String, parameters: Map<String, String>, accessToken: String): JSONObject {
        require(endpoint == "playlists" || endpoint == "playlistItems")
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val url = URL("https://www.googleapis.com/youtube/v3/$endpoint?$query")
        val connection = url.openConnection() as HttpURLConnection
        try {
            // Never forward the bearer token to a redirect destination.
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw YouTubeHttpException(status)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun artwork(snippet: JSONObject): String? {
        val thumbnails = snippet.optJSONObject("thumbnails") ?: return null
        return listOf("high", "medium", "default").firstNotNullOfOrNull { size ->
            thumbnails.optJSONObject(size)?.optString("url")?.takeIf { it.startsWith("https://") }
        }
    }

    private class InaccessiblePlaylistException : IOException("Playlist inaccessible")

    private class YouTubeHttpException(val status: Int) : IOException("YouTube HTTP $status")

    companion object {
        const val PROVIDER_ID = "youtube_music"
        private fun emptyState() = ProviderLibraryState(
            providerId = PROVIDER_ID,
            accountScopeNote = "내 YouTube 재생목록 + 공유 링크 · 읽기 전용 (저장 목록은 링크로 추가 · 자동 믹스 미지원)",
        )
    }
}
