package com.foldpod.app.library

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object SpotifySecurity {
    fun playlistCount(current: Int?, legacy: Int?): Int? =
        current?.takeIf { it >= 0 } ?: legacy?.takeIf { it >= 0 }

    fun randomValue(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun matchesState(expected: String, actual: String?): Boolean = actual != null &&
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

    /** Never send a bearer token to a paging URL supplied by another host or redirect. */
    fun validateApiUrl(value: String): String {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host == "api.spotify.com" &&
            (uri.port == -1 || uri.port == 443) && uri.rawUserInfo == null &&
            uri.fragment == null && uri.path.startsWith("/v1/")) { "Invalid Spotify paging URL" }
        return value
    }
}
