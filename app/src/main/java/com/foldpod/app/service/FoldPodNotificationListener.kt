package com.foldpod.app.service

import android.service.notification.NotificationListenerService
import com.foldpod.app.media.MediaControllerRepository

class FoldPodNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaControllerRepository.get(this).refreshActiveSession()
    }
}
