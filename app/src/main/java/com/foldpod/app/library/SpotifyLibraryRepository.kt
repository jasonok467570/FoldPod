package com.foldpod.app.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.BindException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/** Spotify OAuth/library session with explicit, scoped Premium playback control. */
class SpotifyLibraryRepository(
    context: Context,
    private val clientId: String = "30ffe9eec28242eda714673838a3f9dd",
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = SpotifyTokenStore(context.applicationContext)
    private val mutableState = MutableStateFlow(ProviderLibraryState())
    val state: StateFlow<ProviderLibraryState> = mutableState.asStateFlow()
    private val lock = Any()
    private var operation: Job? = null
    private var generation = 0L
    private var closed = false
    private var tokens: SpotifyTokens? = null
    private var loadedTokens = false
    private var retryAfterUntil = 0L
    private var callbackServer: ServerSocket? = null
    private var connection: HttpURLConnection? = null
    private var playbackInFlight = false
    private var playbackUncertain = false
    val playbackBlocked: Boolean get() = synchronized(lock) { !closed && (playbackInFlight || playbackUncertain) }
    private var approvedPlaybackDeviceId: String? = null

    fun connect(openUri: (Uri) -> Unit) {
        // Release the Activity browser callback as soon as the browser is launched.
        var launchBrowser: ((Uri) -> Unit)? = openUri
        startOperation(resetPlaybackBlock = true) { ticket ->
            val verifier = SpotifySecurity.randomValue()
            val expectedState = SpotifySecurity.randomValue()
            val server = ServerSocket()
            try {
                synchronized(lock) {
                    checkCurrent(ticket)
                    callbackServer = server
                }
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8888))
                server.soTimeout = 500
                val authUri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                    .appendQueryParameter("client_id", clientId)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", REDIRECT_URI)
                    .appendQueryParameter("scope", SpotifyPlaybackPolicy.connectScopes.joinToString(" "))
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("code_challenge", SpotifySecurity.challenge(verifier))
                    .appendQueryParameter("state", expectedState).build()
                withContext(Dispatchers.Main) {
                    val callback = launchBrowser
                    launchBrowser = null
                    callback?.invoke(authUri)
                }
                val code = awaitCode(server, expectedState)
                val result = requestToken(mapOf("grant_type" to "authorization_code", "code" to code,
                    "redirect_uri" to REDIRECT_URI, "code_verifier" to verifier), ticket)
                saveTokens(result, ticket)
                update(ticket) { it.copy(connected = true, playlists = emptyList(), selectedPlaylist = null, tracks = emptyList()) }
                loadPlaylists(ticket)
            } finally {
                server.close()
                synchronized(lock) { if (callbackServer === server) callbackServer = null }
            }
        }
    }

    fun refresh() = startOperation { ticket ->
        restoreTokens(ticket)
        if (synchronized(lock) { tokens } == null) {
            update(ticket) { it.copy(connected = false, loading = false) }
        } else {
            loadPlaylists(ticket)
        }
    }

    fun loadTracks(playlist: LibraryPlaylist) = startOperation { ticket ->
        require(playlist.providerId == "spotify" && playlist.id.matches(Regex("[A-Za-z0-9]+")))
        restoreTokens(ticket)
        update(ticket) { it.copy(selectedPlaylist = playlist, tracks = emptyList()) }
        val items = paged("https://api.spotify.com/v1/playlists/${playlist.id}/items?limit=50", ticket)
        val tracks = items.map { wrapper ->
            val item = wrapper.optJSONObject("item") ?: wrapper.optJSONObject("track")
            if (item == null) LibraryTrack("spotify", null, "Unavailable item", available = false)
            else {
                val album = item.optJSONObject("album")
                val local = wrapper.optBoolean("is_local") || item.optBoolean("is_local")
                LibraryTrack("spotify", item.nullableString("id"), item.optString("name").ifBlank { "Unavailable item" },
                    artist = item.optJSONArray("artists")?.let { artists ->
                        (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.nullableString("name") }.joinToString(", ")
                    } ?: item.optJSONObject("show")?.optString("publisher").orEmpty(),
                    album = album?.optString("name").orEmpty(),
                    artworkUrl = imageUrl(album ?: item),
                    available = item.optString("type", "track") == "track" && !local && item.nullableString("id") != null &&
                        (!item.has("is_playable") || item.optBoolean("is_playable")) && item.optJSONObject("restrictions") == null,
                    isLocal = local)
            }
        }
        update(ticket) { it.copy(connected = true, tracks = tracks, loading = false, error = null) }
    }

    /** Completion null means independently confirmed playback, never just an accepted PUT. */
    fun playTrack(playlist: LibraryPlaylist, track: LibraryTrack, selectedIndex: Int,
        confirmDevice: suspend (String) -> Boolean,
        onComplete: (String?) -> Unit) {
        val ticket: Long
        val payload: String
        synchronized(lock) {
            if (closed) return
            val error = when {
                playbackInFlight -> "Spotify 곡 선택을 처리 중입니다. 잠시 기다려 주세요."
                playbackUncertain -> "이전 Spotify 곡 선택의 결과를 확인하지 못했습니다. Spotify Connect로 다시 연결한 뒤 시도해 주세요."
                else -> null
            }
            if (error != null) { onComplete(error); return }
            payload = SpotifyPlaybackPolicy.payload(mutableState.value, playlist, track, selectedIndex)
                ?: run { onComplete("재생목록이 변경되었거나 이 항목은 재생할 수 없습니다. 다시 불러와 주세요."); return }
            ticket = generation
            playbackInFlight = true
        }
        scope.launch {
            var mutationAttempted = false
            var mutationAccepted = false
            var confirmed = false
            var message: String? = null
            try {
                restoreTokens(ticket)
                val granted = synchronized(lock) { checkCurrent(ticket); tokens?.scopes.orEmpty() }
                if (!granted.containsAll(SpotifyPlaybackPolicy.playbackScopes)) {
                    throw LibraryFailure("Spotify Connect에서 재생 상태와 재생 제어 권한을 새로 승인해 주세요. 재생에는 Premium이 필요합니다.")
                }
                val approvedId = synchronized(lock) { checkCurrent(ticket); approvedPlaybackDeviceId }
                val devices = playbackDevices(ticket)
                val phone = SpotifyPlaybackPolicy.playbackPhone(devices, approvedId)
                    ?: throw LibraryFailure("재생 가능한 휴대폰 Spotify 기기를 확인할 수 없습니다. 이 휴대폰의 Spotify에서 먼저 음악을 재생해 주세요.")
                if (approvedId != null && approvedId != phone.id)
                    throw LibraryFailure("활성 Spotify 기기가 승인한 기기와 다릅니다. Spotify Connect로 다시 연결해 주세요.")
                if (approvedId == null) {
                    val approved = withContext(Dispatchers.Main) { confirmDevice(phone.name) }
                    if (!approved) throw LibraryFailure("Spotify 재생 기기 선택을 취소했습니다.")
                    synchronized(lock) { checkCurrent(ticket); approvedPlaybackDeviceId = phone.id }
                }
                // The dialog may stay open while Spotify devices or playlist positions change.
                if (SpotifyPlaybackPolicy.playbackPhone(playbackDevices(ticket), phone.id)?.id != phone.id)
                    throw LibraryFailure("Spotify 재생 기기가 변경되었거나 사용할 수 없습니다. 휴대폰 Spotify에서 확인해 주세요.")
                val positionPage = api("https://api.spotify.com/v1/playlists/${playlist.id}/items?offset=$selectedIndex&limit=1", ticket)
                val position = positionPage.optJSONArray("items")?.optJSONObject(0)
                val item = position?.optJSONObject("item") ?: position?.optJSONObject("track")
                val remote = item?.let {
                    val local = position?.optBoolean("is_local") == true || it.optBoolean("is_local")
                    LibraryTrack("spotify", it.nullableString("id"), it.optString("name"),
                        available = it.optString("type", "track") == "track" && !local &&
                            (!it.has("is_playable") || it.optBoolean("is_playable")) && it.optJSONObject("restrictions") == null,
                        isLocal = local)
                }
                if (!SpotifyPlaybackPolicy.remoteIndexMatches(track, remote))
                    throw LibraryFailure("Spotify 재생목록의 곡 순서가 변경되었습니다. Refresh로 다시 불러온 뒤 선택해 주세요.")
                synchronized(lock) {
                    checkCurrent(ticket)
                    if (SpotifyPlaybackPolicy.payload(mutableState.value, playlist, track, selectedIndex) != payload)
                        throw LibraryFailure("재생목록이 변경되었습니다. 다시 선택해 주세요.")
                }
                mutationAttempted = true
                api("https://api.spotify.com/v1/me/player/play?device_id=${encode(phone.id!!)}", ticket,
                    method = "PUT", body = payload)
                mutationAccepted = true
                // The accepted command may take time to appear. Never substitute title matching.
                repeat(6) {
                    if (!confirmed) {
                        delay(750L)
                        val playback = api("https://api.spotify.com/v1/me/player", ticket)
                        confirmed = SpotifyPlaybackPolicy.confirmsPlayback(
                            playback.optJSONObject("item")?.nullableString("id"), track.id!!,
                            playback.optBoolean("is_playing"), playback.optJSONObject("device")?.nullableString("id"), phone.id,
                            playback.optJSONObject("context")?.nullableString("uri"), playlist.id,
                        )
                    }
                }
                if (!confirmed) throw LibraryFailure("Spotify가 선택한 곡의 재생을 확인하지 않았습니다. Spotify Connect로 다시 연결해 주세요.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // A rejected HTTP request is safe to retry; a lost response after PUT is not.
                synchronized(lock) {
                    if (ticket == generation && SpotifyPlaybackPolicy.mutationUncertain(
                            mutationAttempted, mutationAccepted, confirmed, (failure as? HttpFailure)?.status))
                        playbackUncertain = true
                }
                message = (failure as? LibraryFailure)?.message
                    ?: if (mutationAttempted) "Spotify 재생 요청의 결과를 확인하지 못했습니다. Spotify Connect로 다시 연결해 주세요."
                    else "Spotify에 연결할 수 없습니다. 연결 상태를 확인해 주세요."
            } finally {
                synchronized(lock) { playbackInFlight = false }
            }
            withContext(Dispatchers.Main) {
                synchronized(lock) { checkCurrent(ticket) }
                onComplete(message)
            }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            generation++
            operation?.cancel()
            callbackServer?.close()
            callbackServer = null
            connection?.disconnect()
            connection = null
            tokens = null
            loadedTokens = true
            retryAfterUntil = 0L
            playbackUncertain = false
            approvedPlaybackDeviceId = null
            store.clear()
            mutableState.value = ProviderLibraryState()
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            generation++
            operation?.cancel()
            callbackServer?.close()
            callbackServer = null
            connection?.disconnect()
            connection = null
        }
        scope.cancel()
    }

    private fun startOperation(resetPlaybackBlock: Boolean = false, block: suspend (Long) -> Unit) {
        synchronized(lock) {
            // Browsing/Connect cannot cancel an in-flight playback mutation and start another.
            if (closed || playbackInFlight) return
            if (resetPlaybackBlock) {
                playbackUncertain = false
                approvedPlaybackDeviceId = null
            }
            generation++
            val ticket = generation
            operation?.cancel()
            callbackServer?.close()
            callbackServer = null
            connection?.disconnect()
            connection = null
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            operation = scope.launch {
                try {
                    block(ticket)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val message = when (failure) {
                        is LibraryFailure -> failure.message ?: "Spotify request failed."
                        is BindException -> "Port 8888 is busy. Close the other connection and try again."
                        is SocketTimeoutException -> "Spotify connection timed out. Try Connect again."
                        else -> "Could not contact Spotify. Check your connection and try again."
                    }
                    update(ticket) { it.copy(loading = false, error = message) }
                }
            }
        }
    }

    private fun checkCurrent(ticket: Long) {
        if (closed || ticket != generation) throw CancellationException()
    }

    private suspend fun playbackDevices(ticket: Long): List<SpotifyPlaybackDevice> {
        val devices = api("https://api.spotify.com/v1/me/player/devices", ticket).optJSONArray("devices")
        return (0 until (devices?.length() ?: 0)).mapNotNull { index ->
            devices?.optJSONObject(index)?.let { device ->
                SpotifyPlaybackDevice(device.nullableString("id"), device.optString("type"),
                    device.optBoolean("is_active"), device.optBoolean("is_restricted"), device.optString("name"))
            }
        }
    }

    private fun update(ticket: Long, change: (ProviderLibraryState) -> ProviderLibraryState) {
        synchronized(lock) { checkCurrent(ticket); mutableState.value = change(mutableState.value) }
    }

    private fun restoreTokens(ticket: Long) {
        synchronized(lock) {
            checkCurrent(ticket)
            if (!loadedTokens) { tokens = store.read(); loadedTokens = true }
            mutableState.value = mutableState.value.copy(connected = tokens != null)
        }
    }

    private fun saveTokens(value: SpotifyTokens, ticket: Long) {
        synchronized(lock) { checkCurrent(ticket); store.write(value); tokens = value; loadedTokens = true }
    }

    private suspend fun accessToken(ticket: Long, forceRefresh: Boolean = false): String {
        currentCoroutineContext().ensureActive()
        val current = synchronized(lock) { checkCurrent(ticket); tokens }
            ?: throw LibraryFailure("Connect your Spotify account to browse playlists.")
        if (!forceRefresh && current.expiresAt > System.currentTimeMillis() + 60_000) return current.access
        val refreshed = requestToken(mapOf("grant_type" to "refresh_token", "refresh_token" to current.refresh), ticket, current.refresh, current.scopes)
        saveTokens(refreshed, ticket)
        return refreshed.access
    }

    private suspend fun requestToken(fields: Map<String, String>, ticket: Long, previousRefresh: String? = null,
        previousScopes: Set<String> = emptySet()): SpotifyTokens {
        val body = (fields + ("client_id" to clientId)).entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val json = request("https://accounts.spotify.com/api/token", ticket, body = body)
        val refresh = json.nullableString("refresh_token") ?: previousRefresh
            ?: throw LibraryFailure("Spotify did not return a reusable connection. Try Connect again.")
        val access = json.nullableString("access_token")
            ?: throw LibraryFailure("Spotify authorization could not be completed.")
        return SpotifyTokens(access, refresh, System.currentTimeMillis() + json.getLong("expires_in") * 1000,
            SpotifyPlaybackPolicy.grantedScopes(if (json.has("scope") && !json.isNull("scope")) json.optString("scope") else null,
                previousScopes))
    }

    private suspend fun api(url: String, ticket: Long, method: String = "GET", body: String? = null): JSONObject {
        SpotifySecurity.validateApiUrl(url)
        val bearer = accessToken(ticket)
        return try { request(url, ticket, bearer = bearer, body = body, method = method) }
        catch (failure: HttpFailure) {
            if (failure.status != 401) throw failure
            try { request(url, ticket, bearer = accessToken(ticket, forceRefresh = true), body = body, method = method) }
            catch (retryFailure: HttpFailure) {
                if (retryFailure.status == 401) {
                    synchronized(lock) { checkCurrent(ticket); tokens = null; store.clear() }
                    update(ticket) { it.copy(connected = false, playlists = emptyList(), selectedPlaylist = null, tracks = emptyList()) }
                }
                throw retryFailure
            }
        }
    }

    private suspend fun paged(firstUrl: String, ticket: Long): List<JSONObject> {
        val visited = mutableSetOf<String>()
        val results = mutableListOf<JSONObject>()
        var next: String? = firstUrl
        while (next != null) {
            currentCoroutineContext().ensureActive()
            if (visited.size >= 1000 || !visited.add(next)) throw LibraryFailure("Spotify returned an incomplete playlist list. Try Refresh again.")
            val page = api(next, ticket)
            val array = page.optJSONArray("items") ?: throw LibraryFailure("Spotify returned an unsupported playlist format.")
            for (index in 0 until array.length()) {
                // A null playlist item occupies its original position instead of silently changing order.
                results += array.optJSONObject(index) ?: JSONObject()
            }
            next = page.nullableString("next")
        }
        return results
    }

    private suspend fun loadPlaylists(ticket: Long) {
        val playlists = paged("https://api.spotify.com/v1/me/playlists?limit=50", ticket).mapNotNull { item ->
            item.nullableString("id")?.let { id ->
                LibraryPlaylist("spotify", id, item.optString("name").ifBlank { "Untitled playlist" }, imageUrl(item),
                    SpotifySecurity.playlistCount(item.optJSONObject("items")?.optInt("total", -1),
                        item.optJSONObject("tracks")?.optInt("total", -1)))
            }
        }
        update(ticket) { it.copy(connected = true, loading = false, playlists = playlists, error = null) }
    }

    private suspend fun request(url: String, ticket: Long, bearer: String? = null, body: String? = null,
        method: String = if (body == null) "GET" else "POST"): JSONObject {
        currentCoroutineContext().ensureActive()
        synchronized(lock) {
            checkCurrent(ticket)
            if (System.currentTimeMillis() < retryAfterUntil) throw LibraryFailure("Spotify is limiting requests. Try again in ${((retryAfterUntil - System.currentTimeMillis()) / 1000 + 1)} seconds.")
        }
        val http = URL(url).openConnection() as HttpURLConnection
        synchronized(lock) { checkCurrent(ticket); connection = http }
        try {
            http.instanceFollowRedirects = false
            http.connectTimeout = 15_000
            http.readTimeout = 20_000
            http.requestMethod = method
            http.setRequestProperty("Accept", "application/json")
            bearer?.let { http.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                http.doOutput = true
                http.setRequestProperty("Content-Type", if (method == "PUT") "application/json" else "application/x-www-form-urlencoded")
                http.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = http.responseCode
            if (status == 429) {
                val seconds = (http.getHeaderField("Retry-After")?.toLongOrNull() ?: 60L).coerceIn(1L, 86_400L)
                synchronized(lock) { checkCurrent(ticket); retryAfterUntil = System.currentTimeMillis() + seconds * 1000 }
                throw HttpFailure(status, "Spotify is limiting requests. Try again in $seconds seconds.")
            }
            if (status !in 200..299) {
                val message = when (status) {
                    400, 401 -> "Spotify connection expired or authorization failed. Connect again."
                    403 -> if (url.contains("/me/player")) "Spotify 재생에는 Premium과 재생 제어 권한이 필요합니다. Spotify Connect와 앱 접근 권한을 확인해 주세요."
                        else "Spotify does not permit this account or playlist to be read. Check app access and playlist permissions."
                    404 -> if (url.contains("/me/player")) "활성 Spotify 기기를 찾지 못했습니다. 휴대폰 Spotify에서 먼저 음악을 재생해 주세요."
                        else "This Spotify playlist is unavailable or cannot be read by this app."
                    else -> "Spotify request failed (HTTP $status). Try again later."
                }
                if ((status == 400 || status == 401) && url == "https://accounts.spotify.com/api/token") {
                    synchronized(lock) { checkCurrent(ticket); tokens = null; store.clear() }
                    update(ticket) { it.copy(connected = false, playlists = emptyList(), tracks = emptyList(), selectedPlaylist = null) }
                }
                throw HttpFailure(status, message)
            }
            // Playback dispatch succeeds with HTTP 204 and no JSON body.
            if (status == 204) return JSONObject()
            val content = http.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (output.length + count > 4 * 1024 * 1024) throw LibraryFailure("Spotify response is too large to display.")
                    output.append(buffer, 0, count)
                }
                output.toString()
            }
            currentCoroutineContext().ensureActive()
            return JSONObject(content)
        } finally {
            http.disconnect()
            synchronized(lock) { if (connection === http) connection = null }
        }
    }

    private suspend fun awaitCode(server: ServerSocket, expectedState: String): String {
        val deadline = System.nanoTime() + 180_000_000_000L
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            socket.use {
                it.soTimeout = 3000
                val requestLine = try {
                    val input = it.getInputStream()
                    val line = StringBuilder()
                    while (line.length < 8192) {
                        val character = input.read()
                        if (character < 0 || character == 10) break
                        if (character != 13) line.append(character.toChar())
                    }
                    // Consume bounded headers before closing so a browser receives the response
                    // without an unread-input TCP reset. Nothing from the request is logged.
                    var headerBytes = 0
                    var headerLineLength = 0
                    while (headerBytes++ < 16384) {
                        val character = input.read()
                        if (character < 0 || (character == 10 && headerLineLength == 0)) break
                        if (character == 10) headerLineLength = 0
                        else if (character != 13) headerLineLength++
                    }
                    line.toString()
                } catch (_: SocketTimeoutException) { "" }
                val parts = requestLine.split(' ')
                val uri = parts.getOrNull(1)?.let { value -> runCatching { Uri.parse(value) }.getOrNull() }
                val valid = runCatching {
                    parts.firstOrNull() == "GET" && uri?.path == "/callback" &&
                        uri.getQueryParameters("state").size == 1 &&
                        SpotifySecurity.matchesState(expectedState, uri.getQueryParameter("state"))
                }.getOrDefault(false)
                val code = if (valid) uri?.getQueryParameter("code") else null
                val denied = valid && uri?.getQueryParameter("error") != null
                val text = if (!code.isNullOrBlank()) "Authorization received. Return to FoldPod to finish connecting." else "Spotify authorization could not be completed. Return to FoldPod."
                val response = text.toByteArray(Charsets.UTF_8)
                runCatching {
                    it.getOutputStream().write(("HTTP/1.1 ${if (valid) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${response.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").toByteArray(Charsets.US_ASCII) + response)
                }
                if (denied) throw LibraryFailure("Spotify authorization was cancelled. Try Connect again when ready.")
                if (!code.isNullOrBlank()) return code
            }
        }
        throw SocketTimeoutException()
    }

    private fun imageUrl(item: JSONObject): String? = item.optJSONArray("images")?.let { images ->
        (0 until images.length()).asSequence().mapNotNull { images.optJSONObject(it)?.nullableString("url") }
            .firstOrNull { runCatching { val uri = java.net.URI(it); uri.scheme == "https" && uri.host != null }.getOrDefault(false) }
    }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private open class LibraryFailure(message: String) : Exception(message)
    private class HttpFailure(val status: Int, message: String) : LibraryFailure(message)

    private companion object { const val REDIRECT_URI = "http://127.0.0.1:8888/callback" }
}
