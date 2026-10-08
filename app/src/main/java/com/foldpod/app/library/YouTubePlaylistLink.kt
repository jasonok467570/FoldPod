package com.foldpod.app.library

import java.net.URI
import java.net.URLDecoder

/** Parses a shared link without opening it or following redirects. */
object YouTubePlaylistLink {
    enum class Failure { INVALID_LINK, UNSUPPORTED_MIX }
    class InvalidLink(val failure: Failure) : IllegalArgumentException(
        when (failure) {
            Failure.INVALID_LINK -> "올바른 YouTube 또는 YouTube Music 재생목록 공유 링크를 입력해 주세요."
            Failure.UNSUPPORTED_MIX -> "자동 생성 믹스·라디오 링크는 지원하지 않습니다. 일반 재생목록 링크를 사용해 주세요."
        },
    )

    fun parse(link: String): String {
        try {
            val uri = URI(link.trim())
            val host = uri.host?.lowercase()
            if (uri.scheme?.lowercase() != "https" || host !in HOSTS ||
                uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null
            ) invalid()
            val allowedPath = if (host == "youtu.be") {
                uri.rawPath.matches(Regex("/[A-Za-z0-9_-]{11}"))
            } else {
                uri.rawPath == "/playlist" || uri.rawPath == "/watch"
            }
            if (!allowedPath) invalid()
            val lists = uri.rawQuery.orEmpty().split('&').map { parameter ->
                val pieces = parameter.split('=', limit = 2)
                URLDecoder.decode(pieces[0], "UTF-8") to
                    URLDecoder.decode(pieces.getOrElse(1) { "" }, "UTF-8")
            }.filter { it.first == "list" }
            if (lists.size != 1) invalid()
            val id = lists.single().second
            if (id.startsWith("RD") && id.matches(Regex("[A-Za-z0-9_-]{2,150}"))) {
                throw InvalidLink(Failure.UNSUPPORTED_MIX)
            }
            if (!isValidId(id)) invalid()
            return id
        } catch (error: InvalidLink) {
            throw error
        } catch (_: Exception) {
            invalid()
        }
    }

    internal fun isValidId(id: String): Boolean = id.matches(Regex("[A-Za-z0-9_-]{10,150}"))
    private fun invalid(): Nothing = throw InvalidLink(Failure.INVALID_LINK)
    private val HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
}
