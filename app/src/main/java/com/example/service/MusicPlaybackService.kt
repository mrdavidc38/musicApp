package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Track
import com.example.player.MusicPlayerManager
import com.example.player.PlayerState
import com.example.player.RepeatMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class MusicPlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private lateinit var playerManager: MusicPlayerManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "music_playback_channel_v1"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.ACTION_NEXT"
        const val ACTION_PREV = "com.example.ACTION_PREV"
        const val ACTION_TOGGLE_REPEAT = "com.example.ACTION_TOGGLE_REPEAT"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_START_SERVICE = "com.example.ACTION_START_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (ex: Exception) {}
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        playerManager = MusicPlayerManager.getInstance(applicationContext)

        createNotificationChannel()
        initMediaSession()
        observePlayerState()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción Multimedia",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción y estado musical"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = MediaSession(this, "MusicPlaybackService").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        playerManager.resume()
                    }

                    override fun onPause() {
                        playerManager.pause()
                    }

                    override fun onSkipToNext() {
                        playerManager.playNext()
                    }

                    override fun onSkipToPrevious() {
                        playerManager.playPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        playerManager.seekTo(pos.toInt())
                    }

                    override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                        when (action) {
                            ACTION_TOGGLE_REPEAT -> playerManager.toggleRepeatMode()
                        }
                    }

                    override fun onStop() {
                        playerManager.pause()
                        stopForegroundCompat(false)
                    }
                })
                isActive = true
            }
        }
    }

    private fun observePlayerState() {
        serviceScope.launch {
            playerManager.currentState.collectLatest { state ->
                updatePlaybackState(state)
                updateNotification()
            }
        }

        serviceScope.launch {
            playerManager.currentTrack.collectLatest {
                updateNotification()
            }
        }

        serviceScope.launch {
            playerManager.repeatMode.collectLatest {
                updateNotification()
            }
        }
    }

    private fun updatePlaybackState(state: PlayerState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val stateCode = when (state) {
                is PlayerState.Playing -> PlaybackState.STATE_PLAYING
                is PlayerState.Paused -> PlaybackState.STATE_PAUSED
                is PlayerState.Buffering -> PlaybackState.STATE_BUFFERING
                is PlayerState.Error -> PlaybackState.STATE_ERROR
                PlayerState.Idle -> PlaybackState.STATE_NONE
            }

            val position = when (state) {
                is PlayerState.Playing -> state.progressMs.toLong()
                is PlayerState.Paused -> state.progressMs.toLong()
                else -> 0L
            }

            val actions = PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP

            val playbackState = PlaybackState.Builder()
                .setActions(actions)
                .setState(stateCode, position, 1.0f)
                .build()

            mediaSession?.setPlaybackState(playbackState)
        }
    }

    private fun updateNotification() {
        val track = playerManager.currentTrack.value ?: return
        val state = playerManager.currentState.value
        val repeatMode = playerManager.repeatMode.value
        val isPlaying = state is PlayerState.Playing

        val notification = buildNotification(track, isPlaying, repeatMode)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        track: Track,
        isPlaying: Boolean,
        repeatMode: RepeatMode
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val prevPendingIntent = createActionPendingIntent(ACTION_PREV, 1)
        val playPausePendingIntent = createActionPendingIntent(ACTION_PLAY_PAUSE, 2)
        val nextPendingIntent = createActionPendingIntent(ACTION_NEXT, 3)
        val repeatPendingIntent = createActionPendingIntent(ACTION_TOGGLE_REPEAT, 4)

        val repeatIcon = when (repeatMode) {
            RepeatMode.ALL -> R.drawable.ic_repeat
            RepeatMode.ONE -> R.drawable.ic_repeat_one
            RepeatMode.OFF -> R.drawable.ic_repeat_off
        }

        val repeatTitle = repeatMode.getDisplayName()

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val playPauseTitle = if (isPlaying) "Pausar" else "Reproducir"

        val trackBitmap = createTrackBitmap(track)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(repeatTitle)
            .setLargeIcon(trackBitmap)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_skip_previous, "Anterior", prevPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
            .addAction(R.drawable.ic_skip_next, "Siguiente", nextPendingIntent)
            .addAction(repeatIcon, repeatTitle, repeatPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            builder.setStyle(
                NotificationCompat.DecoratedCustomViewStyle()
            )
        }

        return builder.build()
    }

    private fun createActionPendingIntent(actionStr: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            action = actionStr
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createTrackBitmap(track: Track): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background Gradient
        val color1 = android.graphics.Color.rgb(
            (track.primaryColor.red * 255).toInt(),
            (track.primaryColor.green * 255).toInt(),
            (track.primaryColor.blue * 255).toInt()
        )
        val color2 = android.graphics.Color.rgb(
            (track.secondaryColor.red * 255).toInt(),
            (track.secondaryColor.green * 255).toInt(),
            (track.secondaryColor.blue * 255).toInt()
        )

        val gradient = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            color1, color2,
            Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }

        // Draw rounded rectangle
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Draw vinyl disk & note
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(60, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.35f, circlePaint)
        canvas.drawCircle(size / 2f, size / 2f, size * 0.20f, circlePaint)

        // Draw text initial
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val initial = if (track.title.isNotEmpty()) track.title.take(1).uppercase() else "♪"
        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initial, size / 2f, yPos, textPaint)

        return bitmap
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playerManager.resume()
            ACTION_PAUSE -> playerManager.pause()
            ACTION_PLAY_PAUSE -> playerManager.togglePlayPause()
            ACTION_NEXT -> playerManager.playNext()
            ACTION_PREV -> playerManager.playPrevious()
            ACTION_TOGGLE_REPEAT -> playerManager.toggleRepeatMode()
            ACTION_STOP -> {
                playerManager.pause()
                stopForegroundCompat(true)
                stopSelf()
            }
            ACTION_START_SERVICE -> {
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        }
    }
}
