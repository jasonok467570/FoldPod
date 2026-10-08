package com.foldpod.app.ui

/** Pending wheel movement stays relative to the progressing playback clock until committed. */
internal object WheelSeekPolicy {
    fun preview(positionMs: Long, pendingDeltaMs: Long, durationMs: Long): Long {
        val target = (positionMs + pendingDeltaMs).coerceAtLeast(0L)
        return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
    }

    fun accumulate(pendingDeltaMs: Long, stepMs: Long, positionMs: Long, durationMs: Long): Long {
        val position = preview(positionMs, 0L, durationMs)
        return preview(position, pendingDeltaMs + stepMs, durationMs) - position
    }
}
