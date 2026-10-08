package com.foldpod.app.ui

internal enum class FoldPodPage {
    NOW_PLAYING,
    MAIN_MENU,
    PLAYLISTS,
    PROVIDER_PLAYLISTS,
    PLAYLIST,
    PLAYER,
    PLAYBACK_OPTIONS,
    SLEEP_TIMER,
    SETTINGS,
    WHEEL_SETTINGS,
    SEEK_SETTINGS,
    HAPTICS_SETTINGS,
    DISPLAY_SETTINGS,
    ABOUT,
    CONNECTIONS,
    LIBRARY_TRACKS,
}

internal enum class NowPlayingControlMode {
    NORMAL,
    SEEK,
}

internal enum class MainMenuAction {
    NOW_PLAYING,
    PLAYLIST,
    PLAYER,
    PLAYBACK_OPTIONS,
    SLEEP_TIMER,
    SETTINGS,
    EXIT,
}

internal data class MainMenuItem(
    val title: String,
    val action: MainMenuAction,
)

internal val MAIN_MENU_ITEMS =
    listOf(
        MainMenuItem(
            "Now Playing",
            MainMenuAction.NOW_PLAYING,
        ),
        MainMenuItem(
            "Playlists",
            MainMenuAction.PLAYLIST,
        ),
        MainMenuItem(
            "Player",
            MainMenuAction.PLAYER,
        ),
        MainMenuItem(
            "Playback",
            MainMenuAction.PLAYBACK_OPTIONS,
        ),
        MainMenuItem(
            "Sleep Timer",
            MainMenuAction.SLEEP_TIMER,
        ),
        MainMenuItem(
            "Settings",
            MainMenuAction.SETTINGS,
        ),
        MainMenuItem(
            "Exit FoldPod",
            MainMenuAction.EXIT,
        ),
    )
