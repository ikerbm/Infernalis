package com.example.localmusicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.model.Track
import com.example.localmusicplayer.ui.MainActivity
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import com.example.localmusicplayer.widget.MusicWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for music playback
 * Uses ExoPlayer for efficient audio playback
 */
class MusicPlaybackService : MediaSessionService() {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdatingPosition = false
    
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                _currentPosition.value = player.currentPosition
                if (player.duration > 0) {
                    _duration.value = player.duration
                }
            }
            if (isUpdatingPosition) {
                handler.postDelayed(this, 250L)
            }
        }
    }
    
    // Store the track list for reference
    private var trackList: List<Track> = emptyList()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1

        // Notification Actions
        const val ACTION_PLAY_PAUSE = "com.example.localmusicplayer.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.localmusicplayer.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.localmusicplayer.ACTION_NEXT"

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _currentTrack = MutableStateFlow<Track?>(null)
        val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

        private val _currentPosition = MutableStateFlow(0L)
        val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

        private val _duration = MutableStateFlow(0L)
        val duration: StateFlow<Long> = _duration.asStateFlow()

        private val _isShuffleEnabled = MutableStateFlow(false)
        val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

        // 0 = OFF, 1 = REPEAT_ALL, 2 = REPEAT_ONE
        private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
        val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

        private var serviceInstance: MusicPlaybackService? = null

        fun getInstance(): MusicPlaybackService? = serviceInstance
    }

    // Original track list (unshuffled) for restoring order
    private var originalTrackList: List<Track> = emptyList()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannel()
        initializePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREVIOUS -> previous()
            ACTION_NEXT -> next()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
                // Enable auto-advancement to next track
                repeatMode = Player.REPEAT_MODE_OFF
            }

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _duration.value = exoPlayer?.duration ?: 0L
                    _currentTrack.value?.let { updateNotification(it) }
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                }
            }
            notifyWidgetUpdate()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            // Update notification to reflect play/pause icon change
            _currentTrack.value?.let { updateNotification(it) }
            notifyWidgetUpdate()
        }

        /**
         * Called when the current media item changes
         * This handles next/previous buttons and auto-advancement
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Update current track based on ExoPlayer's current index
            val currentIndex = exoPlayer?.currentMediaItemIndex ?: return
            if (currentIndex in trackList.indices) {
                val newTrack = trackList[currentIndex]
                _currentTrack.value = newTrack
                // Update notification
                updateNotification(newTrack)
                notifyWidgetUpdate()
            }
        }
    }

    private fun notifyWidgetUpdate() {
        val intent = Intent(MusicWidgetProvider.ACTION_WIDGET_UPDATE).apply {
            setPackage(packageName)
            component = ComponentName(this@MusicPlaybackService, MusicWidgetProvider::class.java)
        }
        sendBroadcast(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Play a single track
     */
    fun playTrack(track: Track) {
        // Store as single-item list
        trackList = listOf(track)
        _currentTrack.value = track
        
        val mediaItem = MediaItem.fromUri(track.uri)
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
        startPositionUpdates()
        startForeground(NOTIFICATION_ID, createNotification(track))
    }

    /**
     * Play a list of tracks starting from a specific index
     * This enables next/previous functionality
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        // Store the track list
        trackList = tracks
        
        val mediaItems = tracks.map { MediaItem.fromUri(it.uri) }
        exoPlayer?.apply {
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            play()
        }
        _currentTrack.value = tracks[startIndex]
        startPositionUpdates()
        startForeground(NOTIFICATION_ID, createNotification(tracks[startIndex]))
    }

    /**
     * Set the track list without starting playback
     * Used to enable next/previous when user selects a single track
     */
    fun setPlaylist(tracks: List<Track>, currentIndex: Int) {
        originalTrackList = tracks // Store original order
        trackList = tracks
        _isShuffleEnabled.value = false // Reset shuffle when new playlist is set
        val mediaItems = tracks.map { MediaItem.fromUri(it.uri) }
        exoPlayer?.apply {
            setMediaItems(mediaItems, currentIndex, 0)
            prepare()
            play()
        }
        if (currentIndex in tracks.indices) {
            _currentTrack.value = tracks[currentIndex]
            startPositionUpdates()
            startForeground(NOTIFICATION_ID, createNotification(tracks[currentIndex]))
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    /**
     * Skip to next track
     */
    fun next() {
        exoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                // onMediaItemTransition will handle updating _currentTrack
            }
        }
    }

    /**
     * Go to previous track
     */
    fun previous() {
        exoPlayer?.let { player ->
            // If we're more than 3 seconds into the track, restart it
            // Otherwise go to previous
            if (player.currentPosition > 3000) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                // onMediaItemTransition will handle updating _currentTrack
            } else {
                // At the first track, just restart
                player.seekTo(0)
            }
        }
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    /**
     * Get current track index in the playlist
     */
    fun getCurrentIndex(): Int {
        return exoPlayer?.currentMediaItemIndex ?: -1
    }

    /**
     * Toggle shuffle mode
     * When enabling: shuffle remaining tracks after current
     * When disabling: restore original order
     */
    fun toggleShuffle() {
        val currentIndex = getCurrentIndex()
        if (trackList.isEmpty() || currentIndex == -1) return

        val currentTrack = trackList[currentIndex]
        val newShuffleState = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffleState

        if (newShuffleState) {
            // Enable shuffle: shuffle tracks after current
            val remainingTracks = trackList.filter { it.id != currentTrack.id }.shuffled()
            val newTrackList = mutableListOf(currentTrack).apply {
                addAll(remainingTracks)
            }
            trackList = newTrackList
        } else {
            // Disable shuffle: restore original order
            val originalIndex = originalTrackList.indexOfFirst { it.id == currentTrack.id }
            if (originalIndex >= 0) {
                trackList = originalTrackList
                exoPlayer?.let { player ->
                    val mediaItems = trackList.map { MediaItem.fromUri(it.uri) }
                    player.setMediaItems(mediaItems, originalIndex, player.currentPosition)
                }
                return
            }
        }

        // Update player with new order
        exoPlayer?.let { player ->
            val mediaItems = trackList.map { MediaItem.fromUri(it.uri) }
            player.setMediaItems(mediaItems, 0, player.currentPosition)
        }
    }

    /**
     * Get the current queue (shuffled or original)
     */
    fun getCurrentQueue(): List<Track> = trackList

    /**
     * Cycle repeat mode: OFF -> ALL -> ONE -> OFF
     */
    fun cycleRepeatMode() {
        val newMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = newMode
        exoPlayer?.repeatMode = newMode
    }

    private fun updateNotification(track: Track) {
        val notification = createNotification(track)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current playing track"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(track: Track): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isNowPlaying = exoPlayer?.isPlaying == true

        // Play/Pause action
        val playPauseAction = NotificationCompat.Action(
            if (isNowPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isNowPlaying) "Pause" else "Play",
            PendingIntent.getService(this, 0, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }, PendingIntent.FLAG_IMMUTABLE)
        )

        // Previous action
        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Previous",
            PendingIntent.getService(this, 0, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS }, PendingIntent.FLAG_IMMUTABLE)
        )

        // Next action
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            PendingIntent.getService(this, 0, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }, PendingIntent.FLAG_IMMUTABLE)
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)

        // Set MediaStyle if session is active
        mediaSession?.let { session ->
            builder.setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    private fun startPositionUpdates() {
        isUpdatingPosition = true
        handler.removeCallbacks(positionUpdateRunnable)
        handler.post(positionUpdateRunnable)
    }

    private fun stopPositionUpdates() {
        isUpdatingPosition = false
        handler.removeCallbacks(positionUpdateRunnable)
    }

    override fun onDestroy() {
        stopPositionUpdates()
        mediaSession?.run {
            player.release()
            release()
        }
        exoPlayer = null
        mediaSession = null
        serviceInstance = null
        trackList = emptyList()
        super.onDestroy()
    }
}
