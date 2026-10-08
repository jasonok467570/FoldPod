package com.foldpod.app.media

import org.junit.Assert.*
import org.junit.Test

class DirectQueuePolicyTest {
    private val rows = (0..4).map { index ->
        QueueRowIdentity(index.toLong(), "media-$index", "content:track:$index", title = ('A' + index).toString())
    }
    private val all = DirectQueueCapabilities(true, true, true)
    private fun request(source: List<QueueRowIdentity> = rows) = QueueSelectionRequest(1, 2, source, 4)

    @Test fun directJumpSurvivesAutoplayRecommendationReplacementAroundTarget() {
        val source = rows.map { it.copy(mediaId = null, mediaUri = null) }
        val navigation = DirectQueueNavigation(request(source), DirectQueueCommand.SkipToQueueItem(4), 0)
        assertTrue(navigation.dispatch())
        val rebuilt = listOf(source[4], source[0], QueueRowIdentity(99, title = "New recommendation"))
        assertTrue(navigation.refresh(rebuilt))
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE,
            navigation.acknowledge(rebuilt, 0, QueueMetadataFingerprint(title = "E"), true, true))
    }

    @Test fun directJumpStillRejectsReusedTargetIdAndDuplicateOccurrences() {
        val navigation = DirectQueueNavigation(request(), DirectQueueCommand.SkipToQueueItem(4), 0)
        assertTrue(navigation.dispatch())
        assertFalse(navigation.refresh(listOf(rows[4].copy(title = "Another recording"))))
        assertFalse(navigation.refresh(listOf(rows[4], rows[4])))
    }

    @Test fun queueJumpCompatibilityIsLimitedToVerifiedYouTubeMusicPackage() {
        assertTrue(DirectQueuePolicy.supportsQueueJump(true, "example.player"))
        assertFalse(DirectQueuePolicy.supportsQueueJump(false, "example.player"))
        assertFalse(DirectQueuePolicy.supportsQueueJump(false, "com.spotify.music"))
        assertTrue(DirectQueuePolicy.supportsQueueJump(false, "com.google.android.apps.youtube.music"))
    }

    @Test fun youtubeMusicWithoutActionFlagJumpsAtoEOnceWithUniqueQueueIds() {
        val idOnlyRows = rows.map { it.copy(mediaId = null, mediaUri = null) }
        val capabilities = DirectQueueCapabilities(
            DirectQueuePolicy.supportsQueueJump(false, "com.google.android.apps.youtube.music"), false, false)
        val command = DirectQueuePolicy.command(idOnlyRows, 4, 0, 0, capabilities)
        assertEquals(DirectQueueCommand.SkipToQueueItem(4), command)
        val navigation = DirectQueueNavigation(request(idOnlyRows), command!!, 0)
        assertTrue(navigation.dispatch())
        assertFalse(navigation.dispatch())
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE,
            navigation.acknowledge(idOnlyRows, 4, QueueMetadataFingerprint(), true, true))
        assertNull(DirectQueuePolicy.command(idOnlyRows.map { it.copy(id = 9) }, 4, 0, 9, capabilities))
        assertNull(DirectQueuePolicy.command(idOnlyRows, 4, 0, 99, capabilities))
    }

    @Test fun aToEUsesExactlyOneAdvertisedQueueJump() {
        val command = DirectQueuePolicy.command(rows, 4, 0, 0, all)
        assertEquals(DirectQueueCommand.SkipToQueueItem(4), command)
        val navigation = DirectQueueNavigation(request(), command!!, 0)
        assertTrue(navigation.canDispatch(rows, 0))
        assertFalse(navigation.canDispatch(rows, 1))
        assertTrue(navigation.dispatch())
        assertFalse(navigation.dispatch())
        assertFalse(navigation.canDispatch(rows, 0))
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE,
            navigation.acknowledge(rows.drop(4), 0, QueueMetadataFingerprint(), true, true))
    }

    @Test fun uriAndMediaIdFallbacksNeverTraverseIntermediateRows() {
        assertEquals(DirectQueueCommand.PlayFromUri("content:track:4"),
            DirectQueuePolicy.command(rows, 4, 0, 0, all.copy(skipToQueueItem = false)))
        assertEquals(DirectQueueCommand.PlayFromMediaId("media-4"),
            DirectQueuePolicy.command(rows, 4, 0, 0, DirectQueueCapabilities(false, false, true)))
        assertNull(DirectQueuePolicy.command(rows, 4, 0, 0, DirectQueueCapabilities(false, false, false)))
        assertNull(DirectQueuePolicy.command(rows.map { it.copy(mediaId = null, mediaUri = null) },
            4, 0, 0, all.copy(skipToQueueItem = false)))
    }

    @Test fun spotifyMediaIdAloneNeverBecomesSpeculativeUriCommand() {
        val spotifyRows = rows.mapIndexed { index, row ->
            row.copy(mediaId = "spotify:track:Track$index", mediaUri = null)
        }
        val uriOnly = DirectQueueCapabilities(false, true, false)
        assertNull(DirectQueuePolicy.command(spotifyRows, 4, 0, 0, uriOnly))
        assertEquals(DirectQueueCommand.SkipToQueueItem(4),
            DirectQueuePolicy.command(spotifyRows, 4, 0, 0, all))
        assertEquals(DirectQueueCommand.PlayFromMediaId("spotify:track:Track4"),
            DirectQueuePolicy.command(spotifyRows, 4, 0, 0, DirectQueueCapabilities(false, false, true)))
        val trackUri = "spotify:track:4Jr2kaZY9EKvaP04OzbpUk"
        val duplicate = spotifyRows.mapIndexed { index, row ->
            if (index == 3 || index == 4) row.copy(mediaId = trackUri) else row
        }
        assertNull(DirectQueuePolicy.command(duplicate, 4, 0, 0, uriOnly))
        assertEquals(DirectQueueCommand.SkipToQueueItem(4),
            DirectQueuePolicy.command(duplicate, 4, 0, 0, all))
        // Pending requests still preserve queue occurrences; duplicate content cannot remap rebuilt IDs.
        assertNull(DirectQueuePolicy.preservedIndex(request(duplicate), duplicate.map { it.copy(id = it.id + 100) }))
    }

    @Test fun actualDescriptionUriCanStillSelectAndAcknowledgeExactRecording() {
        val uri = "spotify:track:4Jr2kaZY9EKvaP04OzbpUk"
        val source = rows.mapIndexed { index, row -> if (index == 4) row.copy(mediaUri = uri) else row }
        val command = DirectQueuePolicy.command(source, 4, 0, 0, DirectQueueCapabilities(false, true, false))
        assertEquals(DirectQueueCommand.PlayFromUri(uri), command)
        val navigation = DirectQueueNavigation(request(source), command!!, 0)
        assertTrue(navigation.dispatch())
        assertFalse(navigation.dispatch())
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE,
            navigation.acknowledge(source, null, QueueMetadataFingerprint(mediaId = uri), true, true))
    }

    @Test fun duplicateRecordingRequiresOccurrenceAddressableQueueId() {
        val duplicate = rows.mapIndexed { index, row ->
            if (index == 3) row.copy(mediaId = rows[4].mediaId, mediaUri = rows[4].mediaUri) else row
        }
        assertEquals(DirectQueueCommand.SkipToQueueItem(4), DirectQueuePolicy.command(duplicate, 4, 0, 0, all))
        assertNull(DirectQueuePolicy.command(duplicate, 4, 0, 0, all.copy(skipToQueueItem = false)))
        assertNull(DirectQueuePolicy.command(duplicate.map { it.copy(id = 9) }, 4, 0, 9, all))
        assertEquals(DirectQueueCommand.PlayFromUri("content:track:4"),
            DirectQueuePolicy.command(rows, 4, 0, 99, all)) // Invalid active ID disables queue-ID transport.
    }

    @Test fun rebuiltQueueAcknowledgesExactFreshReadyMetadataWithoutOldQueueId() {
        val command = DirectQueueCommand.PlayFromUri("content:track:4")
        val navigation = DirectQueueNavigation(request(), command, 0)
        val rebuilt = rows.reversed().map { it.copy(id = it.id + 100) }
        assertFalse(navigation.refresh(rebuilt)) // Before dispatch a changed source is stale.
        assertTrue(navigation.dispatch())
        assertTrue(navigation.refresh(rebuilt))
        val metadata = QueueMetadataFingerprint(mediaUri = "content:track:4")
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT,
            navigation.acknowledge(rebuilt, null, metadata, false, true))
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT,
            navigation.acknowledge(rebuilt, null, metadata, true, false))
        assertEquals(QueueNavigationPlan.Acknowledgement.WAIT,
            navigation.acknowledge(rebuilt, 0, metadata.copy(mediaUri = "content:track:3"), true, true))
        assertEquals(QueueNavigationPlan.Acknowledgement.COMPLETE,
            navigation.acknowledge(rebuilt, null, metadata, true, true))
    }

    @Test fun mediaIdAcknowledgementAndSpotifyUriRequireExactIdentity() {
        val idCommand = DirectQueueCommand.PlayFromMediaId("media-4")
        assertTrue(DirectQueuePolicy.metadataMatches(idCommand, QueueMetadataFingerprint(mediaId = "media-4")))
        assertFalse(DirectQueuePolicy.metadataMatches(idCommand, QueueMetadataFingerprint(title = "E", mediaId = "media-3")))
        val spotifyCommand = DirectQueueCommand.PlayFromUri("spotify:track:Track4")
        assertTrue(DirectQueuePolicy.metadataMatches(spotifyCommand, QueueMetadataFingerprint(mediaId = "spotify:track:Track4")))
        assertFalse(DirectQueuePolicy.metadataMatches(spotifyCommand, QueueMetadataFingerprint(mediaId = "spotify:track:Track40")))
    }

    @Test fun staleRequestsAndPendingIntentNeverSubstituteAnotherOccurrence() {
        val request = request()
        assertTrue(request.matches(1, 2, rows))
        assertFalse(request.matches(2, 2, rows))
        assertFalse(request.matches(1, 3, rows))
        assertFalse(request.matches(1, 2, rows.reversed()))
        val rebuilt = rows.reversed().map { it.copy(id = it.id + 100) }
        assertEquals(0, DirectQueuePolicy.preservedIndex(request, rebuilt))
        assertNull(DirectQueuePolicy.preservedIndex(request, rebuilt.drop(1)))
        assertNull(DirectQueuePolicy.preservedIndex(request, rebuilt + rebuilt.first()))
        val duplicate = rows + rows.last().copy(id = 8)
        assertNull(DirectQueuePolicy.preservedIndex(request(duplicate), rebuilt))
    }
}
