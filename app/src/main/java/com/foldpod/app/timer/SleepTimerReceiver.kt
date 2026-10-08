package com.foldpod.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SleepTimerScheduler.ACTION_EXPIRE) return
        val id = intent.getStringExtra(SleepTimerScheduler.EXTRA_ID) ?: return
        val token = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(SleepTimerScheduler.EXTRA_TOKEN, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<MediaSession.Token>(SleepTimerScheduler.EXTRA_TOKEN)
        } ?: return
        SleepTimerScheduler.get(context).receive(id, token)
    }
}
