package com.foldpod.app.media

enum class MediaActionSemantic { SHUFFLE, REPEAT }

/** The label describes the currently offered action, not an inferred toggle state. */
data class MediaSessionAction(
    val actionId: String,
    val label: String,
    val semantic: MediaActionSemantic?,
    val sessionGeneration: Long,
)

object MediaSessionActionPolicy {
    // English standalone words and the Korean words observed in the actual session labels.
    private val shuffle = Regex("\\bshuffle\\b|(^|\\s)셔플(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val repeat = Regex("\\brepeat\\b|(^|\\s)반복(?=\\s|$)", RegexOption.IGNORE_CASE)

    fun semantic(label: String): MediaActionSemantic? {
        val isShuffle = shuffle.containsMatchIn(label)
        val isRepeat = repeat.containsMatchIn(label)
        return when {
            isShuffle && !isRepeat -> MediaActionSemantic.SHUFFLE
            isRepeat && !isShuffle -> MediaActionSemantic.REPEAT
            else -> null
        }
    }

    /** Never select the first duplicate ID or send an action copied from an earlier session/label. */
    fun matchingIndex(request: MediaSessionAction, sessionGeneration: Long, live: List<MediaSessionAction>): Int? {
        if (request.sessionGeneration != sessionGeneration || request.actionId.isBlank() || request.semantic == null) return null
        return live.indices.filter { live[it].actionId == request.actionId }.singleOrNull()
            ?.takeIf { live[it] == request }
    }
}

data class SessionPreferenceCandidate(val packageName: String, val playing: Boolean)

object SessionPreferencePolicy {
    fun selectedIndex(preferredPackage: String?, sessions: List<SessionPreferenceCandidate>): Int? =
        sessions.indexOfFirst { it.packageName == preferredPackage }.takeIf { it >= 0 }
            ?: sessions.indexOfFirst { it.playing }.takeIf { it >= 0 }
            ?: sessions.indices.firstOrNull()
}

object RelativeSeekPolicy {
    fun target(positionMs: Long, updateElapsedMs: Long, speed: Float, playing: Boolean,
        nowElapsedMs: Long, durationMs: Long, deltaMs: Long): Long? {
        if (positionMs < 0 || !speed.isFinite()) return null
        val elapsed = if (playing && updateElapsedMs in 1..nowElapsedMs) nowElapsedMs - updateElapsedMs else 0L
        val projected = (positionMs.toDouble() + elapsed.toDouble() * speed.coerceAtLeast(0f)).toLong().coerceAtLeast(0L)
        val shifted = when {
            deltaMs > 0 && projected > Long.MAX_VALUE - deltaMs -> Long.MAX_VALUE
            deltaMs < 0 && projected < Long.MIN_VALUE - deltaMs -> Long.MIN_VALUE
            else -> projected + deltaMs
        }.coerceAtLeast(0L)
        return if (durationMs > 0) shifted.coerceAtMost(durationMs) else shifted
    }
}
