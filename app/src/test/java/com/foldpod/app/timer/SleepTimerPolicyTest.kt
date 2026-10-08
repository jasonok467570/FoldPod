package com.foldpod.app.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerPolicyTest {
    private val ticket = SleepTimerTicket("first", 900_100L, 7, "music.app", 15, true)

    @Test fun deadlineUsesElapsedTimeAndNeverReportsNegativeRemaining() {
        assertEquals(900_000L, SleepTimerPolicy.remainingMs(ticket, 100L))
        assertEquals(1L, SleepTimerPolicy.remainingMs(ticket, 900_099L))
        assertEquals(0L, SleepTimerPolicy.remainingMs(ticket, 900_100L))
        assertEquals(0L, SleepTimerPolicy.remainingMs(ticket, 990_000L))
        assertFalse(SleepTimerPolicy.canClaim(ticket, "first", 7, 900_099L))
        assertTrue(SleepTimerPolicy.canClaim(ticket, "first", 7, 900_100L))
    }

    @Test fun rebootOrMissingBootIdentityInvalidatesPersistedElapsedDeadline() {
        assertTrue(SleepTimerPolicy.valid(ticket, 7))
        assertFalse(SleepTimerPolicy.valid(ticket, 8))
        assertFalse(SleepTimerPolicy.valid(ticket.copy(bootCount = -1), -1))
        assertFalse(SleepTimerPolicy.canClaim(ticket, "first", 8, 990_000L))
    }

    @Test fun cancelledOrReplacedTicketRejectsOldAlarm() {
        assertFalse(SleepTimerPolicy.canClaim(null, "first", 7, 990_000L))
        assertFalse(SleepTimerPolicy.canClaim(ticket.copy(id = "replacement"), "first", 7, 990_000L))
        assertTrue(SleepTimerPolicy.canClaim(ticket.copy(id = "replacement"), "replacement", 7, 990_000L))
    }

    @Test fun claimInvalidationMakesDuplicateDeliveryHarmless() {
        var stored: SleepTimerTicket? = ticket
        var pauseCount = 0
        fun deliver() {
            if (SleepTimerPolicy.canClaim(stored, "first", 7, 900_100L)) {
                stored = null // Scheduler commits this invalidation before its pause command.
                pauseCount++
            }
        }
        deliver()
        deliver()
        assertEquals(1, pauseCount)
    }

    @Test fun onlyApprovedDurationsAndWellFormedTicketsAreValid() {
        for (minutes in listOf(15, 30, 60)) assertTrue(SleepTimerPolicy.valid(ticket.copy(durationMinutes = minutes), 7))
        for (minutes in listOf(0, 1, 14, 120)) assertFalse(SleepTimerPolicy.valid(ticket.copy(durationMinutes = minutes), 7))
        assertFalse(SleepTimerPolicy.valid(ticket.copy(id = ""), 7))
        assertFalse(SleepTimerPolicy.valid(ticket.copy(packageName = ""), 7))
        assertFalse(SleepTimerPolicy.valid(ticket.copy(deadlineElapsedMs = 0), 7))
    }
}
