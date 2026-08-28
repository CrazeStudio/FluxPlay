package com.example.fluxplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aistudio.fluxplay.player.R
import com.example.fluxplay.MainActivity

class PlaybackNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "fluxplay_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.example.fluxplay.ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "com.example.fluxplay.ACTION_STOP"
        const val ACTION_REWIND = "com.example.fluxplay.ACTION_REWIND"
        const val ACTION_FORWARD = "com.example.fluxplay.ACTION_FORWARD"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media controls for active video/audio stream"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(title: String, subtitle: String, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(ACTION_PLAY_PAUSE)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = Intent(ACTION_REWIND)
        val rewindPendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = Intent(ACTION_FORWARD)
        val forwardPendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_play)
            .setContentTitle(title.ifEmpty { "Fluxplay" })
            .setContentText(subtitle.ifEmpty { "Playing Stream" })
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_notif_rewind, "Rewind", rewindPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_notif_forward, "Forward", forwardPendingIntent)
            .addAction(R.drawable.ic_notif_stop, "Stop", stopPendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(subtitle.ifEmpty { "Playing Stream" })
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
