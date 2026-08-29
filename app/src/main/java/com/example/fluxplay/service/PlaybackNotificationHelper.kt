package com.example.fluxplay.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aistudio.fluxplay.player.R
import com.example.fluxplay.MainActivity
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlaybackNotificationHelper {

    const val CHANNEL_ID = "fluxplay_media_playback"
    const val CHANNEL_NAME = "Fluxplay Media Playback"
    const val NOTIFICATION_ID = 1001

    const val ACTION_PLAY = "com.example.fluxplay.ACTION_PLAY"
    const val ACTION_PAUSE = "com.example.fluxplay.ACTION_PAUSE"
    const val ACTION_REWIND = "com.example.fluxplay.ACTION_REWIND"
    const val ACTION_FORWARD = "com.example.fluxplay.ACTION_FORWARD"
    const val ACTION_STOP = "com.example.fluxplay.ACTION_STOP"

    private var actionListener: ((String) -> Unit)? = null
    private var isReceiverRegistered = false

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                actionListener?.invoke(action)
            }
        }
    }

    fun registerActionListener(context: Context, listener: (String) -> Unit) {
        actionListener = listener
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PLAY)
                addAction(ACTION_PAUSE)
                addAction(ACTION_REWIND)
                addAction(ACTION_FORWARD)
                addAction(ACTION_STOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(notificationReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    fun unregisterReceiver(context: Context) {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(notificationReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
            actionListener = null
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and information for active media streaming"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPlaybackNotification(
        context: Context,
        media: MediaItemEntity,
        isPlaying: Boolean,
        coroutineScope: CoroutineScope
    ) {
        createNotificationChannel(context)

        coroutineScope.launch(Dispatchers.IO) {
            var artworkBitmap: Bitmap? = null
            if (media.poster.isNotBlank()) {
                try {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(media.poster)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        artworkBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    }
                } catch (_: Exception) {
                }
            }

            withContext(Dispatchers.Main) {
                val contentIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val contentPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val rewindIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent(ACTION_REWIND).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
                val playPauseIntent = PendingIntent.getBroadcast(
                    context,
                    2,
                    Intent(playPauseAction).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val forwardIntent = PendingIntent.getBroadcast(
                    context,
                    3,
                    Intent(ACTION_FORWARD).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val stopIntent = PendingIntent.getBroadcast(
                    context,
                    4,
                    Intent(ACTION_STOP).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(media.title.ifBlank { "Playing Stream" })
                    .setContentText(if (media.source.isNotBlank()) "${media.source} • ${media.type}" else "Fluxplay Stream Player")
                    .setSubText(if (isPlaying) "Playing" else "Paused")
                    .setContentIntent(contentPendingIntent)
                    .setOngoing(isPlaying)
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(android.R.drawable.ic_media_rew, "Rewind 10s", rewindIntent)
                    .addAction(
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (isPlaying) "Pause" else "Play",
                        playPauseIntent
                    )
                    .addAction(android.R.drawable.ic_media_ff, "Forward 10s", forwardIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)

                if (artworkBitmap != null) {
                    builder.setLargeIcon(artworkBitmap)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
