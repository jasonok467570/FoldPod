package com.foldpod.app.media

import org.junit.Assert.*
import org.junit.Test

class QueueNavigationPolicyTest {
    private val rows = (1L..8L).map { QueueRowIdentity(it, title = "Track $it") }
    private fun request(index: Int, source: List<QueueRowIdentity> = rows) = QueueSelectionRequest(1, 1, source, index)

    @Test fun rapidWheelStepsAccumulateAndCenterReadsLatestSelectionWithoutRecomposition() {
        var selected = QueueSelection(1, rows, 0)
        repeat(5) { selected = QueueNavigationPolicy.stepSelection(selected, 1, rows, 1) }
        assertEquals(5, QueueNavigationPolicy.selection(selected, 1, rows))
        repeat(20) { selected = QueueNavigationPolicy.stepSelection(selected, 1, rows, 1) }
        assertEquals(rows.lastIndex, selected.index)
        repeat(3) { selected = QueueNavigationPolicy.stepSelection(selected, 1, rows, -1) }
        assertEquals(4, selected.index)
        // A queue reorder between events reconciles identity before applying one step.
        val reordered = rows.reversed()
        selected = QueueNavigationPolicy.stepSelection(selected, 1, reordered, 1)
        assertEquals(4, selected.index)
        assertEquals(rows[3], reordered[selected.index])
        // A large raw direction still advances one row, preserving wheel semantics.
        selected = QueueNavigationPolicy.stepSelection(selected, 1, reordered, -100)
        assertEquals(3, selected.index)
    }

    @Test fun staleRequestCannotMatchLiveSessionRevisionOrSnapshot() {
        val request = request(4)
        assertTrue(request.matches(1, 1, rows))
        assertFalse(request.matches(2, 1, rows))
        assertFalse(request.matches(1, 2, rows))
        assertFalse(request.matches(1, 1, rows.reversed()))
        assertFalse(request.copy(index = 100).matches(1, 1, rows))
    }

    @Test fun queuedTargetRemapsByOccurrenceWithoutClampingOrRecordingSubstitution() {
        val source = listOf(QueueRowIdentity(217, "current"), QueueRowIdentity(228, "same"), QueueRowIdentity(237, "same"))
        val queued = QueueSelection(1, source, 2)
        assertEquals(1, QueueNavigationPolicy.preservedIndex(queued, source.drop(1)))
        assertNull(QueueNavigationPolicy.preservedIndex(queued, source.take(2)))
        assertNull(QueueNavigationPolicy.preservedIndex(queued, source.drop(1).map { it.copy(title = "Replacement") }))
        val unknown = listOf(QueueRowIdentity(-1, "a"), QueueRowIdentity(-1, "b"))
        assertEquals(0, QueueNavigationPolicy.preservedIndex(QueueSelection(1, unknown, 1), unknown.drop(1)))
        assertNull(QueueNavigationPolicy.preservedIndex(QueueSelection(1, unknown, 1), unknown.drop(1) + unknown.last()))
    }

    @Test fun duplicateQueueIdsUseUniqueMediaOccurrencesAcrossSafeRollingSnapshots() {
        val source = listOf(QueueRowIdentity(9, "a"), QueueRowIdentity(9, "b"), QueueRowIdentity(9, "c"))
        val plan = QueueNavigationPlan(request(2, source), 0, false)
        assertTrue(plan.canDispatch(source, 0))
        assertEquals(1, plan.command())
        val live = source.drop(1) + QueueRowIdentity(9, "d")
        assertEquals(QueueNavigationPlan.Acknowledgement.ADVANCE, plan.acknowledge(live, 0))
        assertTrue(plan.canDispatch(live, 0))
        assertEquals(1, plan.command())
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE, plan.acknowledge(live.drop(1), 0))
    }

    @Test fun rollingSpotifyQueuePreservesOccurrenceThroughRepeatedMediaIds() {
        val source = (217L..234L).map { id ->
            QueueRowIdentity(id, mediaId = if (id == 228L || id == 231L) "same-recording" else "media-$id", title = "Track $id")
        }
        val plan = QueueNavigationPlan(request(11, source), 0, false)
        var live = source
        for (id in 218L..228L) {
            assertTrue(plan.canDispatch(live, 0))
            assertEquals(1, plan.command())
            live = live.drop(1)
            val appended = if (id == 220L) listOf(QueueRowIdentity(235, "appended", title = "Appended")) else emptyList()
            live = live + appended
            assertEquals(0, QueueNavigationPolicy.activeIndex(live, id, live.first().mediaId, null))
            assertEquals(if (id == 228L) QueueNavigationPlan.Acknowledgement.COMPLETE else QueueNavigationPlan.Acknowledgement.ADVANCE,
                plan.acknowledge(live, 0))
            assertEquals(0, plan.currentIndex)
        }
        assertEquals(0, plan.targetIndex)
        assertNull(plan.command())
    }

    @Test fun rollingRefreshRejectsReorderReplacementLostTargetAndUnexpectedLivePosition() {
        val plan = QueueNavigationPlan(request(4), 0, false)
        assertTrue(plan.canDispatch(rows, 0))
        assertFalse(plan.canDispatch(rows, 1))
        plan.command()
        assertFalse(plan.refresh(rows.drop(1).reversed()))
        assertFalse(plan.refresh(rows.drop(1).map { if (it.id == 5L) it.copy(title = "Replacement") else it }))
        assertFalse(plan.refresh(rows.drop(5)))
        assertFalse(plan.refresh(rows.drop(1) + rows.first()))
        assertTrue(plan.refresh(rows.drop(1)))
        assertEquals(0, plan.expectedIndex)
        assertEquals(3, plan.targetIndex)
        assertEquals(QueueNavigationPlan.Acknowledgement.ADVANCE, plan.acknowledge(rows.drop(1), 0))
    }

    @Test fun uniqueActiveIdDisambiguatesRepeatedRecordingButConflictsStillReject() {
        val source = listOf(QueueRowIdentity(228, "same"), QueueRowIdentity(237, "same"), QueueRowIdentity(238, "different"))
        assertEquals(0, QueueNavigationPolicy.activeIndex(source, 228, "same", null))
        assertEquals(1, QueueNavigationPolicy.activeIndex(source, 237, "same", null))
        assertNull(QueueNavigationPolicy.activeIndex(source, 228, "different", null))
        assertNull(QueueNavigationPolicy.activeIndex(source, -1, "same", null))
    }

    @Test fun uniqueActiveIdAndConflictingMetadata() {
        assertEquals(2, QueueNavigationPolicy.activeIndex(rows, 3, null, null))
        val mediaRows = rows.mapIndexed { i, row -> row.copy(mediaId = "media-$i") }
        assertNull(QueueNavigationPolicy.activeIndex(mediaRows, 3, "media-1", null))
        assertNull(QueueNavigationPolicy.activeIndex(rows, -1, null, null))
    }

    @Test fun duplicateUnknownIdsNeverSelectFirstButUniqueMediaIdentityWorks() {
        val duplicates = listOf(QueueRowIdentity(-1, "a"), QueueRowIdentity(-1, "b"))
        assertNull(QueueNavigationPolicy.activeIndex(duplicates, -1, null, null))
        assertEquals(1, QueueNavigationPolicy.activeIndex(duplicates, -1, "b", null))
        val reused = duplicates.map { it.copy(id = 9) }
        assertEquals(1, QueueNavigationPolicy.activeIndex(reused, 9, "b", null))
        assertNull(QueueNavigationPolicy.activeIndex(reused.map { it.copy(mediaId = "same") }, 9, "same", null))
    }

    @Test fun selectionPreservesUniqueIdentityAcrossReorderAndResetsSession() {
        val old = QueueSelection(1, rows, 2)
        val reordered = listOf(rows[2], rows[0], rows[1])
        assertEquals(0, QueueNavigationPolicy.selection(old, 1, reordered))
        assertEquals(0, QueueNavigationPolicy.selection(old, 2, rows))
        assertEquals(1, QueueNavigationPolicy.selection(old, 1, rows.take(2)))
    }

    @Test fun selectionDuplicateAndReusedIdClampsInsteadOfRemapping() {
        val duplicate = listOf(QueueRowIdentity(4, title = "same"), QueueRowIdentity(4, title = "same"))
        assertEquals(1, QueueNavigationPolicy.selection(QueueSelection(1, duplicate, 1), 1, duplicate.reversed()))
        val replacement = listOf(rows[2].copy(title = "Different song"), rows[1], rows[0])
        assertEquals(2, QueueNavigationPolicy.selection(QueueSelection(1, rows, 2), 1, replacement))
        val media = listOf(QueueRowIdentity(-1, "a"), QueueRowIdentity(-1, "b"))
        assertEquals(0, QueueNavigationPolicy.selection(QueueSelection(1, media, 1), 1, media.reversed()))
    }

    @Test fun oneRelativeCommandWaitsForAcknowledgementAndFreshReadyMetadata() {
        val plan = QueueNavigationPlan(request(4), 0, false)
        assertEquals(1, plan.command())
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT, plan.acknowledge(rows, 0))
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT, plan.acknowledge(rows, 1, metadataConfirmed = false))
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT, plan.acknowledge(rows, 1, playbackReady = false))
        try { plan.command(); fail("A second command must not be issued while pending") } catch (_: IllegalStateException) { }
        for (index in 1..4) {
            assertEquals(if (index == 4) QueueNavigationPlan.Acknowledgement.COMPLETE else QueueNavigationPlan.Acknowledgement.ADVANCE,
                plan.acknowledge(rows, index))
            if (index < 4) assertEquals(index + 1, plan.command())
        }
        assertNull(plan.command())
    }

    @Test fun reverseRestartWaitsAndUnexpectedPositionOrQueueReplacementRejects() {
        val plan = QueueNavigationPlan(request(1), 4, false)
        assertEquals(3, plan.command())
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT, plan.acknowledge(rows, 4))
        assertEquals(QueueNavigationPlan.Acknowledgement.REJECT, plan.acknowledge(rows, 2))
        assertEquals(QueueNavigationPlan.Acknowledgement.REJECT, plan.acknowledge(rows.reversed(), 3))
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT, plan.acknowledge(rows, null))
    }

    @Test fun delayedRestartGateRejectsTargetTransitionBufferingAndMetadataSkew() {
        val before = QueueMetadataFingerprint("야경", "artist", null, null)
        val guard = PreviousRestartAcknowledgement(10_000, 10_000, 20_000, before)
        assertTrue(guard.consume(0, 20_010, true, true))
        assertFalse(guard.confirmStableReset(300, false, true, before))
        assertFalse(guard.confirmStableReset(300, true, false, before))
        assertFalse(guard.confirmStableReset(300, true, true, before.copy(title = "Next track")))
        assertFalse(guard.confirmStableReset(300, true, true, before.copy(mediaId = "new-media")))
        assertFalse(guard.confirmStableReset(1_001, true, true, before))
        assertTrue(guard.confirmStableReset(300, true, true, before))
        assertFalse(guard.confirmStableReset(400, true, true, before))
        assertFalse(PreviousRestartAcknowledgement(10_000, 10_000, 20_000, before)
            .confirmStableReset(300, true, true, before))
    }

    @Test fun previousRestartUsesProjectedPositionAndRetriesOnlyOnceAfterFreshReset() {
        val before = PreviousRestartAcknowledgement.projectedPosition(0, 10_000, 1f, true, 25_000)
        assertEquals(15_000L, before)
        val guard = PreviousRestartAcknowledgement(before, 10_000, 25_000)
        assertFalse(guard.consume(0, 10_000, true, true))
        assertFalse(guard.consume(0, 24_999, true, true))
        assertFalse(guard.consume(0, 25_010, false, true))
        assertFalse(guard.consume(0, 25_010, true, false))
        assertFalse(guard.consume(4_000, 25_010, true, true))
        assertTrue(guard.consume(0, 25_010, true, true))
        assertFalse(guard.consume(0, 25_020, true, true))
    }

    @Test fun previousRestartDoesNotRetryNearBeginningOrProjectPausedTime() {
        assertEquals(1_000L, PreviousRestartAcknowledgement.projectedPosition(1_000, 10_000, 1f, false, 25_000))
        assertFalse(PreviousRestartAcknowledgement(3_000, 10_000, 25_000).consume(0, 25_010, true, true))
        assertFalse(PreviousRestartAcknowledgement(-1, 10_000, 25_000).consume(0, 25_010, true, true))
        assertEquals(-1L, PreviousRestartAcknowledgement.projectedPosition(-1, 10_000, 1f, true, 25_000))
    }

    @Test fun previousRestartRetryPreservesPendingNeighborAcknowledgement() {
        val plan = QueueNavigationPlan(request(1), 4, false)
        assertEquals(3, plan.command())
        val guard = PreviousRestartAcknowledgement(10_000, 10_000, 20_000)
        assertTrue(guard.consume(0, 20_010, true, true))
        assertEquals(3, plan.expectedIndex)
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT, plan.acknowledge(rows, 4, metadataConfirmed = false))
        assertEquals(QueueNavigationPlan.Acknowledgement.ADVANCE, plan.acknowledge(rows, 3))
        assertEquals(2, plan.command())
    }

    @Test fun transientMetadataActiveIdConflictWaitsThenConsistentOccurrenceAcknowledges() {
        val plan = QueueNavigationPlan(request(1), 0, false)
        assertEquals(1, plan.command())
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT,
            plan.acknowledge(rows, null, metadataConfirmed = true, playbackReady = true))
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE,
            plan.acknowledge(rows, 1, metadataConfirmed = true, playbackReady = true))
    }

    @Test fun supportedDirectJumpAcknowledgesTargetAfterRollingPrefixConsumption() {
        val plan = QueueNavigationPlan(request(4), 0, true)
        assertEquals(4, plan.command())
        val live = rows.drop(4)
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE, plan.acknowledge(live, 0))
        assertEquals(0, plan.targetIndex)
        assertNull(plan.command())
    }

    @Test fun directJumpStillRequiresFinalTargetAcknowledgement() {
        val plan = QueueNavigationPlan(request(4), 0, true)
        assertEquals(4, plan.command())
        assertEquals(QueueNavigationPlan.Acknowledgement.REJECT, plan.acknowledge(rows, 2))
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE, plan.acknowledge(rows, 4))
    }
}
