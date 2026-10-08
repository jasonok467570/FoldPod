package com.foldpod.app.media

/** Installed players are discoverable entries, never invented MediaControllers. */
internal object PlayerListPolicy {
    fun merge(live: List<PlayerSessionInfo>, installed: List<PlayerSessionInfo>): List<PlayerSessionInfo> {
        val livePackages = live.map { it.packageName }.toSet()
        return (live + installed.filter { it.packageName !in livePackages }.distinctBy { it.packageName }.map {
            PlayerSessionInfo(it.packageName, it.appName, hasSession = false)
        }).sortedWith(compareByDescending<PlayerSessionInfo> { it.isSelected }
            .thenByDescending { it.isPlaying }.thenBy { it.appName.lowercase() })
    }
}
