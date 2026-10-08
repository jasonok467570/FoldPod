package com.foldpod.app.media

import org.junit.Assert.*
import org.junit.Test

class PlayerListPolicyTest {
    @Test fun installedPlayersRemainVisibleWithoutAnySessions() {
        val installed = listOf(PlayerSessionInfo("spotify", "Spotify"), PlayerSessionInfo("ytm", "YouTube Music"))
        val result = PlayerListPolicy.merge(emptyList(), installed)
        assertEquals(2, result.size)
        assertTrue(result.all { !it.hasSession && !it.isSelected && !it.isPlaying && it.title.isEmpty() })
    }
    @Test fun liveSessionWinsWithoutDuplicatingInstalledPlayerOrLosingOtherPlayers() {
        val live = PlayerSessionInfo("spotify", "Spotify", title = "Actual", isPlaying = true, isSelected = true)
        val result = PlayerListPolicy.merge(listOf(live, PlayerSessionInfo("other", "Other")),
            listOf(PlayerSessionInfo("spotify", "Spotify"), PlayerSessionInfo("ytm", "YouTube Music")))
        assertEquals(3, result.size)
        assertEquals(live, result.first())
        assertEquals(1, result.count { it.packageName == "spotify" })
        assertFalse(result.single { it.packageName == "ytm" }.hasSession)
    }
}
