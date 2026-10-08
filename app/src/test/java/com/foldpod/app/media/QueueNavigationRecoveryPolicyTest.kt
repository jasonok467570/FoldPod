package com.foldpod.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueNavigationRecoveryPolicyTest {
    private val blocked = BlockedQueueNavigation(10, 5)
    private val rows = listOf(QueueRowIdentity(11, title = "Next", subtitle = "Artist"))
    private val metadata = QueueMetadataFingerprint(title = "Next", artist = "Artist")

    @Test fun freshNextTrackRestoresNavigationWithoutPlayerSwitch() {
        assertTrue(QueueNavigationRecoveryPolicy.canRecover(blocked, rows, 11, metadata, 6, true))
    }

    @Test fun oldOrIncompleteCallbacksDoNotUnlockInFlightSelection() {
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, rows, 11, metadata, 5, true))
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, rows, 11, metadata, 6, false))
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, rows, 10, metadata, 6, true))
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, rows, -1, metadata, 6, true))
    }

    @Test fun ambiguousOrContradictoryActiveOccurrenceDoesNotUnlock() {
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, rows + rows, 11, metadata, 6, true))
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, emptyList(), 11, metadata, 6, true))
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, rows, 11,
            metadata.copy(title = "Old track"), 6, true))
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, rows, 11,
            metadata.copy(artist = "Other artist"), 6, true))
    }

    @Test fun exactContentIdentityMustAgreeDespiteMatchingTitles() {
        val identifiedRows = listOf(
            QueueRowIdentity(10, "old", "uri:old", "Next", "Artist"),
            QueueRowIdentity(11, "new", "uri:new", "Next", "Artist"),
        )
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, identifiedRows, 11,
            metadata.copy(mediaId = "old", mediaUri = "uri:old"), 6, true))
        assertFalse(QueueNavigationRecoveryPolicy.canRecover(blocked, identifiedRows, 11,
            metadata.copy(mediaId = "unknown", mediaUri = "uri:new"), 6, true))
        assertTrue(QueueNavigationRecoveryPolicy.canRecover(blocked, identifiedRows, 11,
            metadata.copy(mediaId = "new", mediaUri = "uri:new"), 6, true))
    }
}
