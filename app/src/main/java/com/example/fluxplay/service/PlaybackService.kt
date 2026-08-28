package com.example.fluxplay.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
class PlaybackService : Service() {

    private val binder = LocalBinder()
    private var exoPlayer: ExoPlayer? = null
    private lateinit var notificationHelper: PlaybackNotificationHelper

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = PlaybackNotificationHelper(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun startForegroundService(title: String, subtitle: String, isPlaying: Boolean) {
        val notification = notificationHelper.buildNotification(title, subtitle, isPlaying)
        startForeground(PlaybackNotificationHelper.NOTIFICATION_ID, notification)
    }

    fun updateNotification(title: String, subtitle: String, isPlaying: Boolean) {
        val notification = notificationHelper.buildNotification(title, subtitle, isPlaying)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(PlaybackNotificationHelper.NOTIFICATION_ID, notification)
    }

    fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
