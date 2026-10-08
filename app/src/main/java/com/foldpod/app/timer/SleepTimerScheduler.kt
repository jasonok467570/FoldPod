package com.foldpod.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.foldpod.app.service.FoldPodNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Application lifetime scheduler. Only the exact captured media session may be paused. */
class SleepTimerScheduler private constructor(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("foldpod_sleep_timer", Context.MODE_PRIVATE)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val sessions = app.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(app, FoldPodNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var capturedToken: MediaSession.Token? = null
    private var capturedTicketId: String? = null
    private val mutableState = MutableStateFlow(SleepTimerState(exactScheduling = exactAlarmAllowed()))
    val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    private val tick = object : Runnable {
        override fun run() {
            synchronized(this@SleepTimerScheduler) {
                val ticket = readTicket()
                if (ticket == null) return
                if (!SleepTimerPolicy.valid(ticket, bootCount())) {
                    clearTicket(ticket, null)
                    return
                }
                publish(ticket)
                if (SleepTimerPolicy.remainingMs(ticket, SystemClock.elapsedRealtime()) == 0L) {
                    val token = capturedToken.takeIf { capturedTicketId == ticket.id }
                    if (token != null) {
                        receive(ticket.id, token)
                    } else {
                        // Process recreation: the OS-owned immutable PendingIntent still holds the token.
                        // Sending the original ticket preserves session identity without persisting a token.
                        val pending = existingIntent(ticket.id)
                        if (pending == null) clearTicket(ticket, "player_unavailable")
                        else try { pending.send() } catch (_: PendingIntent.CanceledException) {
                            clearTicket(ticket, "player_unavailable")
                        }
                    }
                }
                if (readTicket() != null) handler.postDelayed(this, 1_000L)
            }
        }
    }

    init { refresh() }

    @Synchronized
    fun start(durationMinutes: Int, packageName: String): Boolean {
        if (durationMinutes !in SleepTimerPolicy.allowedMinutes || packageName.isBlank()) return false
        val controller = activeControllers().filter { it.packageName == packageName }.singleOrNull()
            ?: return false
        val token = controller.sessionToken
        if (bootCount() < 0) return false
        readTicket()?.let { if (!clearTicket(it, null)) return false }
        val exact = exactAlarmAllowed()
        var ticket = SleepTimerTicket(
            UUID.randomUUID().toString(),
            SystemClock.elapsedRealtime() + durationMinutes * 60_000L,
            bootCount(), packageName, durationMinutes, exact,
        )
        if (!writeTicket(ticket)) return false
        val pending = PendingIntent.getBroadcast(
            app, 0, intent(ticket.id).putExtra(EXTRA_TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (exact) {
                try {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, ticket.deadlineElapsedMs, pending)
                } catch (_: SecurityException) {
                    ticket = ticket.copy(exactScheduling = false)
                    if (!writeTicket(ticket)) throw IllegalStateException("Unable to persist timer")
                    alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, ticket.deadlineElapsedMs, pending)
                }
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, ticket.deadlineElapsedMs, pending)
            }
        } catch (_: RuntimeException) {
            clearTicket(ticket, "schedule_unavailable")
            return false
        }
        capturedToken = token
        capturedTicketId = ticket.id
        publish(ticket)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return true
    }

    @Synchronized
    fun cancel() {
        val ticket = readTicket()
        if (ticket != null) clearTicket(ticket, null)
        else mutableState.value = SleepTimerState(exactScheduling = exactAlarmAllowed())
    }

    @Synchronized
    fun refresh() {
        handler.removeCallbacks(tick)
        var ticket = readTicket() ?: run {
            mutableState.value = mutableState.value.copy(exactScheduling = exactAlarmAllowed())
            return
        }
        if (!SleepTimerPolicy.valid(ticket, bootCount())) clearTicket(ticket, null)
        else {
            val exactAllowed = exactAlarmAllowed()
            if (ticket.exactScheduling != exactAllowed) {
                existingIntent(ticket.id)?.let { pending ->
                    try {
                        if (exactAllowed) alarms.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, ticket.deadlineElapsedMs, pending,
                        ) else alarms.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, ticket.deadlineElapsedMs, pending,
                        )
                        val updated = ticket.copy(exactScheduling = exactAllowed)
                        if (writeTicket(updated)) ticket = updated
                    } catch (_: RuntimeException) { }
                }
            }
            publish(ticket)
            handler.post(tick)
        }
    }

    fun exactAlarmSettingsIntent(): Intent? = if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${app.packageName}"))
    } else null

    @Synchronized
    internal fun receive(id: String, token: MediaSession.Token) {
        val ticket = readTicket()
        if (!SleepTimerPolicy.canClaim(ticket, id, bootCount(), SystemClock.elapsedRealtime())) return
        ticket ?: return
        // Commit invalidation before touching transport controls: duplicate/stale broadcasts cannot claim again.
        if (!preferences.edit().remove(KEY_ID).commit()) return
        val controller = activeControllers().firstOrNull {
            it.packageName == ticket.packageName && it.sessionToken == token
        }
        var outcome = "player_unavailable"
        if (controller != null) {
            try {
                controller.transportControls.pause()
                outcome = "pause_requested"
            } catch (_: RuntimeException) { }
        }
        clearTicket(ticket, outcome)
    }

    private fun activeControllers(): List<MediaController> = try {
        sessions.getActiveSessions(listener)
    } catch (_: RuntimeException) { emptyList() }

    private fun exactAlarmAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    private fun bootCount(): Int = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun intent(id: String): Intent = Intent(app, SleepTimerReceiver::class.java)
        .setAction(ACTION_EXPIRE)
        .setData(Uri.parse("foldpod://sleep-timer/$id"))
        .putExtra(EXTRA_ID, id)

    private fun existingIntent(id: String): PendingIntent? = PendingIntent.getBroadcast(
        app, 0, intent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun clearTicket(ticket: SleepTimerTicket, outcome: String?): Boolean {
        // Persist invalidation first; an already queued alarm is harmless even if cancellation races.
        val invalidated = preferences.edit().clear().commit()
        if (!invalidated && preferences.contains(KEY_ID)) return false
        existingIntent(ticket.id)?.let { alarms.cancel(it); it.cancel() }
        handler.removeCallbacks(tick)
        capturedToken = null
        capturedTicketId = null
        mutableState.value = SleepTimerState(exactScheduling = exactAlarmAllowed(), outcome = outcome)
        return true
    }

    private fun readTicket(): SleepTimerTicket? {
        val id = preferences.getString(KEY_ID, null) ?: return null
        return SleepTimerTicket(
            id, preferences.getLong("deadline", 0L), preferences.getInt("boot", -1),
            preferences.getString("package", "").orEmpty(), preferences.getInt("minutes", 0),
            preferences.getBoolean("exact", false),
        )
    }

    private fun writeTicket(ticket: SleepTimerTicket): Boolean = preferences.edit().clear()
        .putString(KEY_ID, ticket.id).putLong("deadline", ticket.deadlineElapsedMs)
        .putInt("boot", ticket.bootCount).putString("package", ticket.packageName)
        .putInt("minutes", ticket.durationMinutes).putBoolean("exact", ticket.exactScheduling).commit()

    private fun publish(ticket: SleepTimerTicket) {
        mutableState.value = SleepTimerState(
            active = true, deadlineElapsedRealtimeMs = ticket.deadlineElapsedMs,
            remainingMs = SleepTimerPolicy.remainingMs(ticket, SystemClock.elapsedRealtime()),
            packageName = ticket.packageName, durationMinutes = ticket.durationMinutes,
            exactScheduling = ticket.exactScheduling &&
                (exactAlarmAllowed()),
        )
    }

    companion object {
        internal const val ACTION_EXPIRE = "com.foldpod.app.SLEEP_TIMER_EXPIRE"
        internal const val EXTRA_ID = "timer_id"
        internal const val EXTRA_TOKEN = "session_token"
        private const val KEY_ID = "id"
        @Volatile private var instance: SleepTimerScheduler? = null
        fun get(context: Context): SleepTimerScheduler = instance ?: synchronized(this) {
            instance ?: SleepTimerScheduler(context).also { instance = it }
        }
    }
}

data class SleepTimerState(
    val active: Boolean = false,
    val deadlineElapsedRealtimeMs: Long = 0L,
    val remainingMs: Long = 0L,
    val packageName: String? = null,
    val durationMinutes: Int = 0,
    val exactScheduling: Boolean = false,
    val outcome: String? = null,
)
