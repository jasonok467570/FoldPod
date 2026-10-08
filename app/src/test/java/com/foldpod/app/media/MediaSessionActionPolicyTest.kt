package com.foldpod.app.media

import org.junit.Assert.*
import org.junit.Test

class MediaSessionActionPolicyTest {
    @Test fun advertisedLabelsIdentifyOfferedShuffleAndRepeatWithoutInferringToggleState() {
        for (label in listOf("셔플 기능", "셔플 사용", "셔플 사용 안함", "Enable shuffle", "Shuffle off")) {
            assertEquals(MediaActionSemantic.SHUFFLE, MediaSessionActionPolicy.semantic(label))
        }
        for (label in listOf("한 트랙 반복 시작하기", "반복 사용 안함", "Repeat one", "Disable repeat")) {
            assertEquals(MediaActionSemantic.REPEAT, MediaSessionActionPolicy.semantic(label))
        }
        for (label in listOf("좋아요", "라디오 시작하기", "Collection", "Reshuffle library", "반복적인 추천", "Shuffle and repeat", "")) {
            assertNull(MediaSessionActionPolicy.semantic(label))
        }
    }

    @Test fun exactLiveActionIsRequiredAndDuplicateIdsNeverPickFirst() {
        val request = MediaSessionAction("player.exact.shuffle", "셔플 사용", MediaActionSemantic.SHUFFLE, 3)
        assertEquals(0, MediaSessionActionPolicy.matchingIndex(request, 3, listOf(request)))
        assertNull(MediaSessionActionPolicy.matchingIndex(request, 4, listOf(request)))
        assertNull(MediaSessionActionPolicy.matchingIndex(request, 3, listOf(request.copy(label = "셔플 사용 안함"))))
        assertNull(MediaSessionActionPolicy.matchingIndex(request, 3, listOf(request.copy(actionId = "different.shuffle"))))
        assertNull(MediaSessionActionPolicy.matchingIndex(request, 3, listOf(request, request)))
        assertNull(MediaSessionActionPolicy.matchingIndex(request.copy(semantic = null), 3, listOf(request)))
        assertNull(MediaSessionActionPolicy.matchingIndex(request, 3, emptyList()))
    }

    @Test fun savedPlayerWinsWhenPresentAndTemporaryAbsenceDoesNotRequireClearingPreference() {
        val preference = "saved.player"
        val fallback = listOf(SessionPreferenceCandidate("first.player", false), SessionPreferenceCandidate("playing.player", true))
        assertEquals(1, SessionPreferencePolicy.selectedIndex(preference, fallback))
        assertEquals(2, SessionPreferencePolicy.selectedIndex(preference, fallback + SessionPreferenceCandidate(preference, false)))
        assertEquals(0, SessionPreferencePolicy.selectedIndex(preference, fallback.map { it.copy(playing = false) }))
        assertNull(SessionPreferencePolicy.selectedIndex(preference, emptyList()))
    }

    @Test fun relativeSeekUsesElapsedPlayingPositionAndClampsBothEnds() {
        assertEquals(13_000L, RelativeSeekPolicy.target(5_000, 1_000, 1f, true, 4_000, 20_000, 5_000))
        assertEquals(10_000L, RelativeSeekPolicy.target(5_000, 1_000, 1f, false, 4_000, 20_000, 5_000))
        assertEquals(20_000L, RelativeSeekPolicy.target(19_000, 1_000, 1f, true, 4_000, 20_000, 5_000))
        assertEquals(0L, RelativeSeekPolicy.target(5_000, 1_000, 1f, false, 4_000, 20_000, -10_000))
        assertEquals(10_000L, RelativeSeekPolicy.target(5_000, 5_000, 1f, true, 4_000, 0, 5_000))
        assertEquals(Long.MAX_VALUE, RelativeSeekPolicy.target(Long.MAX_VALUE - 1, 0, 1f, false, 4_000, 0, 5_000))
        assertEquals(0L, RelativeSeekPolicy.target(5_000, 0, 1f, false, 4_000, 0, Long.MIN_VALUE))
        assertNull(RelativeSeekPolicy.target(-1, 0, 1f, true, 4_000, 0, 5_000))
        assertNull(RelativeSeekPolicy.target(5_000, 0, Float.NaN, true, 4_000, 0, 5_000))
    }
}
