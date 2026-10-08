package com.foldpod.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YouTubePlaylistLinkTest {
    private val playlist = "PL1234567890_abcdefgh-XYZ"

    @Test fun acceptsYouTubeAndMusicSharedPlaylistLinks() {
        listOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com").forEach { host ->
            assertEquals(playlist, YouTubePlaylistLink.parse("https://$host/playlist?list=$playlist&si=share"))
            assertEquals(playlist, YouTubePlaylistLink.parse("https://$host/watch?v=abcdefghijk&list=$playlist&index=2"))
        }
        assertEquals(playlist, YouTubePlaylistLink.parse("https://youtu.be/abcdefghijk?si=share&list=$playlist"))
        assertEquals(playlist, YouTubePlaylistLink.parse("  https://MUSIC.YOUTUBE.COM/playlist?list=$playlist  "))
    }

    @Test fun acceptsSharedMusicLinkAndIgnoresShareTag() {
        assertEquals(
            playlist,
            YouTubePlaylistLink.parse("https://music.youtube.com/playlist?list=$playlist&si=example_share_tag"),
        )
    }

    @Test fun acceptsEncodedParametersAndAlbumPlaylists() {
        assertEquals(playlist, YouTubePlaylistLink.parse("https://youtube.com/playlist?%6cist=PL1234567890%5Fabcdefgh-XYZ"))
        val album = "OLAK5uy_k123456789abcdefgh"
        assertEquals(album, YouTubePlaylistLink.parse("https://music.youtube.com/playlist?list=$album"))
    }

    @Test fun rejectsMissingDuplicateAndMalformedIds() {
        listOf(
            "https://youtube.com/playlist",
            "https://youtube.com/playlist?list=",
            "https://youtube.com/playlist?list=$playlist&list=$playlist",
            "https://youtube.com/playlist?list=$playlist&%6cist=$playlist",
            "https://youtube.com/playlist?list=short",
            "https://youtube.com/playlist?list=${"a".repeat(151)}",
            "https://youtube.com/playlist?list=PL1234567890%2Fbad",
            "https://youtube.com/playlist?list=PL1234567890+bad",
            "https://youtube.com/playlist?list=PL1234567890%00",
            "https://youtube.com/playlist?list=PL1234567890%GG",
        ).forEach(::assertInvalid)
    }

    @Test fun rejectsSpoofedHostsCredentialsSchemesAndFragments() {
        listOf(
            "http://youtube.com/playlist?list=$playlist",
            "youtube.com/playlist?list=$playlist",
            "https://youtube.com.evil.example/playlist?list=$playlist",
            "https://evil.example/playlist?list=$playlist",
            "https://secret@youtube.com/playlist?list=$playlist",
            "https://youtube.com@evil.example/playlist?list=$playlist",
            "https://youtube.com:443/playlist?list=$playlist",
            "https://youtube.com./playlist?list=$playlist",
            "https://youtube.com/playlist?list=$playlist#fragment",
            "https://youtube.com/playlist?list=$playlist#",
            "https://youtube.com/redirect?list=$playlist",
            "https://youtube.com/%70laylist?list=$playlist",
            "https://youtube.com/channel/abc?list=$playlist",
            "https://youtu.be/playlist?list=$playlist",
            "https://youtu.be/abcdefghijk/extra?list=$playlist",
        ).forEach(::assertInvalid)
    }

    @Test fun distinguishesUnsupportedRadioAndAutoMix() {
        listOf("RDMM", "RDAM", "RDabcdefghijk", "RDAMVMabcdefghijk", "RDCLAK5uy_kabcdefghijk").forEach { id ->
            val error = assertThrows(YouTubePlaylistLink.InvalidLink::class.java) {
                YouTubePlaylistLink.parse("https://music.youtube.com/playlist?list=$id")
            }
            assertEquals(YouTubePlaylistLink.Failure.UNSUPPORTED_MIX, error.failure)
        }
    }

    private fun assertInvalid(link: String) {
        val error = assertThrows(YouTubePlaylistLink.InvalidLink::class.java) { YouTubePlaylistLink.parse(link) }
        assertEquals(link, YouTubePlaylistLink.Failure.INVALID_LINK, error.failure)
    }
}
