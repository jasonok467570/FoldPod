package com.foldpod.app.library

/** ViewModel-owned state without Activity references. Tokens exist only in this object's memory. */
class GoogleAuthorizationState(
    private var autoRestoreEnabled: Boolean? = null,
    initialGeneration: Int = 0,
    private val persistState: (Boolean?, Int) -> Unit = { _, _ -> },
) {
    private var startupAttempted = false

    /** Try silently once per process; only a manual Disconnect opts out of future launches. */
    internal fun beginAutomaticRequest(): Int? {
        if (startupAttempted || autoRestoreEnabled == false || pendingGeneration != null) return null
        startupAttempted = true
        return beginRequest()
    }

    internal fun acceptAuthorization(requestGeneration: Int, token: String): Boolean {
        if (requestGeneration != generation) return false
        autoRestoreEnabled = true
        persistState(autoRestoreEnabled, generation)
        rememberToken(token)
        return true
    }

    internal var generation: Int = initialGeneration
        private set
    internal var pendingGeneration: Int? = null
        private set
    internal var lastAccessToken: String? = null
        private set
    private var used = false

    internal data class PendingSnapshot(val generation: Int, val pendingGeneration: Int?)

    internal fun beginRequest(): Int {
        startupAttempted = true
        used = true
        generation++
        persistState(autoRestoreEnabled, generation)
        return generation
    }

    internal fun markPending(requestGeneration: Int) {
        used = true
        pendingGeneration = requestGeneration
    }

    /** Consume once durably, returning the new identity for this result's delivery. */
    internal fun consumePending(): Int? {
        used = true
        val pending = pendingGeneration
        pendingGeneration = null
        if (pending == null || pending != generation) return null
        // Old saved Bundles must stay consumed even if the process dies before another save.
        generation++
        persistState(autoRestoreEnabled, generation)
        return generation
    }

    internal fun rememberToken(token: String?) {
        used = true
        lastAccessToken = token
    }

    fun disconnect() {
        used = true
        generation++
        autoRestoreEnabled = false
        persistState(autoRestoreEnabled, generation)
        // Preserve outstanding resolution identity until its stale result is consumed.
        lastAccessToken = null
    }

    internal fun snapshot(): PendingSnapshot {
        used = true
        return PendingSnapshot(generation, pendingGeneration)
    }

    internal fun restore(snapshot: PendingSnapshot) {
        // A retained ViewModel may have disconnected or consumed a result since this snapshot.
        if (used || snapshot.generation != generation) return
        used = true
        pendingGeneration = snapshot.pendingGeneration
        if (pendingGeneration != null) startupAttempted = true
    }
}
