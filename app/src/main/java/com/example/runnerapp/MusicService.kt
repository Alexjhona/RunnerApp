@file:Suppress("DEPRECATION")

package com.example.runnerapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIF_ID = 1001

        const val ACTION_PLAY_URI = "com.example.runnerapp.PLAY_URI"
        const val ACTION_TOGGLE   = "com.example.runnerapp.TOGGLE"
        const val ACTION_STOP     = "com.example.runnerapp.STOP"

        const val ACTION_STATE  = "com.example.runnerapp.MUSIC_STATE"
        const val EXTRA_TITLE   = "title"
        const val EXTRA_PLAYING = "playing"
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService() = this@MusicService }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private var currentTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()

        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSessionCompat(this, "RunnerAppSession").apply { isActive = true }

        // Notificación inmediata (A13 requiere POST_NOTIFICATIONS, pídelo en la Activity)
        startForeground(NOTIF_ID, buildNotification(false, ""))

        player.addListener(object : com.google.android.exoplayer2.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
                broadcastState()
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_URI -> {
                val uri = IntentCompat.getParcelableExtra(intent, "uri", Uri::class.java)
                val title = intent.getStringExtra("title").orEmpty()
                if (uri != null) play(uri, title)
            }
            ACTION_TOGGLE -> toggle()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    fun play(uri: Uri, title: String) {
        currentTitle = title
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        updateNotification()
        broadcastState()
    }

    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun isPlaying(): Boolean = player.isPlaying
    fun nowPlaying(): String = currentTitle

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    private fun buildNotification(isPlaying: Boolean, title: String): Notification {
        val toggleIntent = PendingIntent.getService(
            this, 1, Intent(this, MusicService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, MusicService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppIntent = PendingIntent.getActivity(
            this, 3, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (title.isBlank()) getString(R.string.music_title) else title)
            .setContentText(if (isPlaying) "Reproduciendo" else "Pausado")
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pausar" else "Reproducir",
                toggleIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val n = buildNotification(player.isPlaying, currentTitle)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun broadcastState() {
        val i = Intent(ACTION_STATE)
            .putExtra(EXTRA_TITLE, currentTitle)
            .putExtra(EXTRA_PLAYING, player.isPlaying)
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de música",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }
}
