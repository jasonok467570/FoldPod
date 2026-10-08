package com.foldpod.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistBrowserPolicyTest {
    @Test fun samePlaylistIdIsScopedToItsProvider() {
        val spotify = PlaylistBrowserPolicy.key("spotify", "same_id")
        val youtube = PlaylistBrowserPolicy.key("youtube_music", "same_id")
        assertEquals(listOf(youtube, spotify), PlaylistBrowserPolicy.order(listOf(spotify, youtube), setOf(youtube)) { it })
    }

    @Test fun pinsMoveFirstWithoutChangingOrderWithinGroups() {
        val items = listOf("spotify:a", "spotify:b", "spotify:c", "spotify:d")
        assertEquals(listOf("spotify:b", "spotify:d", "spotify:a", "spotify:c"),
            PlaylistBrowserPolicy.order(items, setOf("spotify:d", "spotify:b")) { it })
        assertEquals(items, PlaylistBrowserPolicy.order(items, emptySet()) { it })
        assertEquals(items, PlaylistBrowserPolicy.order(items, items.toSet()) { it })
    }

    @Test fun missingFavoritesDoNotCreateRowsOrAffectAnotherProvider() {
        val items = listOf("youtube_music:first", "youtube_music:second")
        assertEquals(items, PlaylistBrowserPolicy.order(items, setOf("spotify:second", "youtube_music:missing")) { it })
        assertEquals(emptyList<String>(), PlaylistBrowserPolicy.order(emptyList<String>(), setOf("spotify:a")) { it })
    }

    @Test fun orderUsesIdentityRatherThanDisplayNameAndKeepsAllRows() {
        data class Row(val id: String, val title: String)
        val rows = listOf(Row("spotify:a", "Same"), Row("spotify:b", "Same"))
        assertEquals(listOf(rows[1], rows[0]), PlaylistBrowserPolicy.order(rows, setOf("spotify:b")) { it.id })
    }

    @Test fun rejectsAmbiguousAndMalformedStoredIdentities() {
        listOf("spotify:abc123", "youtube_music:PL1234567890_abcdefgh-XYZ").forEach {
            assertTrue(PlaylistBrowserPolicy.isValidKey(it))
        }
        listOf("", "spotify", ":abc", "spotify:", "spotify:a:b", "spotify:a/b", "spotify:a b").forEach {
            assertFalse(PlaylistBrowserPolicy.isValidKey(it))
        }
        assertThrows(IllegalArgumentException::class.java) { PlaylistBrowserPolicy.key("spotify:bad", "id") }
        assertThrows(IllegalArgumentException::class.java) { PlaylistBrowserPolicy.key("spotify", "") }
    }
}
