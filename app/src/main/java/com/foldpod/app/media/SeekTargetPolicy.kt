package com.foldpod.app.media

/** Validate the originating track again at dispatch, after any queued session callbacks. */
internal object SeekTargetPolicy {
    fun matches(expected: NowPlayingState, live: NowPlayingState): Boolean =
        expected.hasSession && live.hasSession &&
            expected.packageName == live.packageName &&
            expected.sessionGeneration == live.sessionGeneration &&
            expected.title == live.title && expected.artist == live.artist &&
            expected.album == live.album && expected.durationMs == live.durationMs &&
            expected.queue.firstOrNull { it.isActive }?.identity() ==
                live.queue.firstOrNull { it.isActive }?.identity()
}
