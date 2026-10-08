package com.foldpod.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleAuthorizationStateTest {
    @Test fun freshProcessRestoresPendingResolutionWithoutToken() {
        val original = pendingState()
        original.rememberToken("memory-only-token")
        val restored = GoogleAuthorizationState(initialGeneration = original.generation)
        restored.restore(original.snapshot())
        val completion = restored.consumePending()
        assertEquals(restored.generation, completion)
        assertNull(restored.lastAccessToken)
    }

    @Test fun retainedDisconnectCannotBeUndoneBySavedState() {
        val state = pendingState()
        val saved = state.snapshot()
        state.disconnect()
        state.restore(saved)
        assertNotEquals(state.generation, state.consumePending())
        assertNull(state.lastAccessToken)
    }

    @Test fun retainedConsumedResultCannotBeReintroducedBySavedState() {
        val state = pendingState()
        val saved = state.snapshot()
        val completion = state.consumePending()
        assertEquals(state.generation, completion)
        state.restore(saved)
        assertNull(state.consumePending())
    }

    @Test fun retainedStateKeepsTokenForCacheInvalidationAcrossActivityReplacement() {
        val state = GoogleAuthorizationState()
        state.rememberToken("memory-only-token")
        val saved = state.snapshot()
        state.restore(saved)
        assertEquals("memory-only-token", state.lastAccessToken)
        state.disconnect()
        assertNull(state.lastAccessToken)
    }

    @Test fun startupRestoreRunsOnceAndMigratesOnlyWithoutOptOut() {
        val decisions = mutableListOf<Boolean>()
        val state = GoogleAuthorizationState(null) { enabled, _ -> enabled?.let { decisions += it } }
        val request = state.beginAutomaticRequest()!!
        assertNull(state.beginAutomaticRequest())
        assertEquals(emptyList<Boolean>(), decisions)
        state.acceptAuthorization(request, "memory-only")
        assertEquals(listOf(true), decisions)
        assertEquals("memory-only", state.lastAccessToken)
        assertNull(GoogleAuthorizationState(false).beginAutomaticRequest())
    }

    @Test fun disconnectRejectsLateAutomaticSuccessAndRemainsOptedOut() {
        val decisions = mutableListOf<Boolean>()
        val state = GoogleAuthorizationState(true) { enabled, _ -> enabled?.let { decisions += it } }
        val request = state.beginAutomaticRequest()!!
        state.disconnect()
        assertEquals(false, state.acceptAuthorization(request, "stale"))
        assertNull(state.lastAccessToken)
        assertEquals(listOf(true, false), decisions)
        assertNull(state.beginAutomaticRequest())
    }

    @Test fun explicitReconnectCanEnableRestorationAfterOptOut() {
        val decisions = mutableListOf<Boolean>()
        val state = GoogleAuthorizationState(false) { enabled, _ -> enabled?.let { decisions += it } }
        assertNull(state.beginAutomaticRequest())
        assertEquals(true, state.acceptAuthorization(state.beginRequest(), "new"))
        assertEquals(listOf(false, true), decisions)
    }

    @Test fun pendingExplicitResolutionAndRetainedStateDoNotStartAnotherRestore() {
        val original = pendingState()
        val restored = GoogleAuthorizationState(true, original.generation)
        restored.restore(original.snapshot())
        assertNull(restored.beginAutomaticRequest())
        val request = restored.consumePending()!!
        restored.acceptAuthorization(request, "token")
        assertNull(restored.beginAutomaticRequest())
        assertNull(original.beginAutomaticRequest())
    }

    @Test fun newerExplicitRequestRejectsOlderStartupResult() {
        val state = GoogleAuthorizationState(true)
        val startup = state.beginAutomaticRequest()!!
        val explicit = state.beginRequest()
        assertEquals(false, state.acceptAuthorization(startup, "old"))
        assertEquals(true, state.acceptAuthorization(explicit, "new"))
        assertEquals("new", state.lastAccessToken)
    }

    @Test fun processDeathCannotRestoreConsentSavedBeforeManualDisconnect() {
        var enabled: Boolean? = true
        var durableGeneration = 0
        val original = GoogleAuthorizationState(enabled, durableGeneration) { optIn, identity ->
            enabled = optIn
            durableGeneration = identity
        }
        original.markPending(original.beginRequest())
        val oldConsent = original.snapshot()
        original.disconnect()
        val recreated = GoogleAuthorizationState(enabled, durableGeneration)
        recreated.restore(oldConsent)
        assertNull(recreated.consumePending())
        assertEquals(false, recreated.acceptAuthorization(oldConsent.generation, "stale"))
        assertNull(recreated.lastAccessToken)
        assertEquals(false, enabled)
        assertNull(recreated.beginAutomaticRequest())
    }

    @Test fun processDeathPreservesNewExplicitReconnectAfterManualDisconnect() {
        var enabled: Boolean? = false
        var durableGeneration = 2
        val original = GoogleAuthorizationState(enabled, durableGeneration) { optIn, identity ->
            enabled = optIn
            durableGeneration = identity
        }
        original.markPending(original.beginRequest())
        val consent = original.snapshot()
        val recreated = GoogleAuthorizationState(enabled, durableGeneration) { optIn, identity ->
            enabled = optIn
            durableGeneration = identity
        }
        recreated.restore(consent)
        val request = recreated.consumePending()!!
        assertEquals(true, recreated.acceptAuthorization(request, "new"))
        assertEquals(true, enabled)
        assertEquals("new", recreated.lastAccessToken)
    }

    @Test fun failedLegacyStartupCanRetryNextProcessButManualDisconnectCannot() {
        var enabled: Boolean? = null
        var durableGeneration = 0
        fun newProcess() = GoogleAuthorizationState(enabled, durableGeneration) { optIn, identity ->
            enabled = optIn
            durableGeneration = identity
        }
        val first = newProcess()
        first.beginAutomaticRequest()!!
        // Offline/GIS failure has no accepted token and must not become an explicit opt-out.
        assertNull(first.beginAutomaticRequest())
        assertNull(enabled)
        val retry = newProcess()
        retry.beginAutomaticRequest()!!
        assertNull(retry.beginAutomaticRequest())
        retry.disconnect()
        assertEquals(false, enabled)
        assertNull(newProcess().beginAutomaticRequest())
    }

    @Test fun consumedGrantSnapshotCannotReturnAfterProcessDeath() {
        assertConsumedSnapshotInvalidated(accepted = true)
    }

    @Test fun cancelledConsentSnapshotCannotBlockReconnectAfterProcessDeath() {
        assertConsumedSnapshotInvalidated(accepted = false)
    }

    private fun assertConsumedSnapshotInvalidated(accepted: Boolean) {
        var enabled: Boolean? = false
        var durableGeneration = 0
        fun newProcess() = GoogleAuthorizationState(enabled, durableGeneration) { optIn, identity ->
            enabled = optIn
            durableGeneration = identity
        }
        val original = newProcess()
        original.markPending(original.beginRequest())
        val beforeResult = original.snapshot()
        val completion = original.consumePending()!!
        if (accepted) assertEquals(true, original.acceptAuthorization(completion, "actual-grant"))
        val recreated = newProcess()
        recreated.restore(beforeResult)
        assertNull(recreated.pendingGeneration)
        assertNull(recreated.consumePending())
        val newRequest = recreated.beginRequest()
        recreated.markPending(newRequest)
        assertEquals(true, recreated.acceptAuthorization(recreated.consumePending()!!, "reconnected"))
        assertEquals("reconnected", recreated.lastAccessToken)
        assertEquals(true, enabled)
    }

    private fun pendingState() = GoogleAuthorizationState().apply {
        markPending(beginRequest())
    }
}
