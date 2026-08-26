package com.example.fluxplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.fluxplay.MainActivity
import com.example.fluxplay.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "fluxplay_playback_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.fluxplay.action.START"
        const val ACTION_UPDATE = "com.example.fluxplay.action.UPDATE"
        const val ACTION_STOP = "com.example.fluxplay.action.STOP"
        const val ACTION_TOGGLE_PLAY = "com.example.fluxplay.action.TOGGLE_PLAY"
        const val ACTION_REWIND = "com.example.fluxplay.action.REWIND"
        const val ACTION_FORWARD = "com.example.fluxplay.action.FORWARD"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"

        private var activeListener: PlaybackControlListener? = null
        private var currentSession: MediaSession? = null

        fun setPlaybackControlListener(listener: PlaybackControlListener?) {
            activeListener = listener
        }

        fun setMediaSession(session: MediaSession?) {
            currentSession = session
        }

        fun start(
            context: Context,
            title: String,
            subtitle: String,
            isPlaying: Boolean,
            positionMs: Long = 0,
            durationMs: Long = 0
        ) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun update(
            context: Context,
            title: String,
            subtitle: String,
            isPlaying: Boolean,
            positionMs: Long = 0,
            durationMs: Long = 0
        ) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    interface PlaybackControlListener {
        fun onTogglePlayPause()
        fun onSkip(seconds: Int)
        fun onStopPlayback()
    }

    private var currentTitle = "Fluxplay Stream"
    private var currentSubtitle = "Playing in Background"
    private var currentIsPlaying = false
    private var currentPositionMs: Long = 0
    private var currentDurationMs: Long = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return currentSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START, ACTION_UPDATE -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentSubtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: currentSubtitle
                currentIsPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, currentIsPlaying)
                currentPositionMs = intent.getLongExtra(EXTRA_POSITION_MS, currentPositionMs)
                currentDurationMs = intent.getLongExtra(EXTRA_DURATION_MS, currentDurationMs)

                val notification = buildPlaybackNotification()
                if (action == ACTION_START) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
            ACTION_TOGGLE_PLAY -> {
                activeListener?.onTogglePlayPause()
            }
            ACTION_REWIND -> {
                activeListener?.onSkip(-10)
            }
            ACTION_FORWARD -> {
                activeListener?.onSkip(10)
            }
            ACTION_STOP -> {
                activeListener?.onStopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildPlaybackNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_REWIND
        }
        val rewindPendingIntent = PendingIntent.getService(
            this,
            1,
            rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePlayIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }
        val togglePlayPendingIntent = PendingIntent.getService(
            this,
            2,
            togglePlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_FORWARD
        }
        val forwardPendingIntent = PendingIntent.getService(
            this,
            3,
            forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (currentIsPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        val playPauseTitle = if (currentIsPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fluxplay_logo)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(currentIsPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_notif_rewind, "-10s", rewindPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, togglePlayPendingIntent)
            .addAction(R.drawable.ic_notif_forward, "+10s", forwardPendingIntent)
            .addAction(R.drawable.ic_notif_stop, "Stop", stopPendingIntent)

        // Add progress if duration is valid
        if (currentDurationMs > 0) {
            val progressPercent = ((currentPositionMs.toFloat() / currentDurationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            builder.setProgress(100, progressPercent, false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fluxplay Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for active background streams and videos"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Fluxplay:PlaybackWakeLock").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours max safe timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentSession = null
        activeListener = null
        super.onDestroy()
    }
}
