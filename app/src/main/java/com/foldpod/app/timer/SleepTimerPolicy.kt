package com.foldpod.app.timer

internal data class SleepTimerTicket(
    val id: String,
    val deadlineElapsedMs: Long,
    val bootCount: Int,
    val packageName: String,
    val durationMinutes: Int,
    val exactScheduling: Boolean,
)

internal object SleepTimerPolicy {
    val allowedMinutes = setOf(15, 30, 60)

    fun valid(ticket: SleepTimerTicket, bootCount: Int): Boolean =
        bootCount >= 0 && ticket.bootCount == bootCount && ticket.id.isNotBlank() &&
            ticket.deadlineElapsedMs > 0L && ticket.packageName.isNotBlank() &&
            ticket.durationMinutes in allowedMinutes

    fun remainingMs(ticket: SleepTimerTicket, nowElapsedMs: Long): Long =
        (ticket.deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0L)

    fun canClaim(ticket: SleepTimerTicket?, id: String, bootCount: Int, nowElapsedMs: Long): Boolean =
        ticket != null && valid(ticket, bootCount) && ticket.id == id &&
            nowElapsedMs >= ticket.deadlineElapsedMs
}
