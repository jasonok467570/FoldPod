package com.foldpod.app.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

/** Retain network/auth sessions across rotation and folding without retaining an Activity. */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    // Preferences contain only opt-in/opt-out and request identity; credentials remain in GIS and memory.
    private val googlePreferences = application.getSharedPreferences("google_library_connection", Context.MODE_PRIVATE)
    val googleAuthorizationState = GoogleAuthorizationState(
        if (googlePreferences.contains("autoRestore")) googlePreferences.getBoolean("autoRestore", false) else null,
        initialGeneration = googlePreferences.getInt("requestGeneration", 0),
    ) { enabled, generation ->
        val editor = googlePreferences.edit().putInt("requestGeneration", generation)
        enabled?.let { editor.putBoolean("autoRestore", it) }
        // Commit both values atomically before launching consent or accepting future saved state.
        editor.commit()
    }
    private val startupGoogleAuthorization = GoogleAuthorizationBridge(application, googleAuthorizationState)
    val spotify = SpotifyLibraryRepository(application)
    val youtube = YouTubeLibraryRepository(viewModelScope, application)

    init {
        spotify.refresh()
    }

    /** Invoked after Activity pending-resolution state is restored; retained across folds. */
    fun restoreConnections() {
        startupGoogleAuthorization.authorize(
            onResolution = {}, // Startup never opens account selection or consent UI.
            onAuthorized = youtube::refresh,
            onError = youtube::showConnectionError,
            automatic = true,
        )
    }

    override fun onCleared() {
        startupGoogleAuthorization.close()
        spotify.close()
        youtube.close()
        super.onCleared()
    }
}
