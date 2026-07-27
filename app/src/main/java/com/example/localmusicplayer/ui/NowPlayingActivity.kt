package com.example.localmusicplayer.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.model.PlaybackState
import com.example.localmusicplayer.databinding.ActivityNowPlayingBinding
import com.example.localmusicplayer.service.MusicPlaybackService
import com.example.localmusicplayer.service.SleepTimerManager
import androidx.media3.common.Player
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Activity for displaying the currently playing track
 * with full playback controls
 */
class NowPlayingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNowPlayingBinding
    private lateinit var gestureDetector: GestureDetector

    // Minimum swipe distance (px) and velocity to trigger track change
    private val swipeMinDistance = 120
    private val swipeMinVelocity = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGestureDetector()
        setupControls()
        observePlaybackState()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // Tap on album art → toggle lyrics
                    if (binding.imageAlbumArt.visibility == View.VISIBLE) {
                        toggleLyrics(true)
                    }
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val e1 = e1 ?: return false
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y

                    // Only respond to predominantly horizontal swipes
                    if (Math.abs(deltaX) < Math.abs(deltaY)) return false
                    if (Math.abs(deltaX) < swipeMinDistance) return false
                    if (Math.abs(velocityX) < swipeMinVelocity) return false

                    if (deltaX < 0) {
                        // Swipe left → Next track
                        animateAlbumArtChange(goingForward = true) {
                            MusicPlaybackService.getInstance()?.next()
                        }
                    } else {
                        // Swipe right → Previous track
                        animateAlbumArtChange(goingForward = false) {
                            MusicPlaybackService.getInstance()?.previous()
                        }
                    }
                    return true
                }
            }
        )

        // Attach gesture detector to album art
        binding.imageAlbumArt.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    /**
     * Slide the album art off-screen in the swipe direction, trigger the track action,
     * then slide back in from the opposite side.
     */
    private fun animateAlbumArtChange(goingForward: Boolean, action: () -> Unit) {
        val view = binding.imageAlbumArt
        val width = view.width.toFloat().takeIf { it > 0 } ?: 800f
        val exitX = if (goingForward) -width else width
        val enterX = if (goingForward) width else -width

        // Slide + fade out
        val slideOut = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, exitX)
        val fadeOut = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        val outSet = AnimatorSet().apply {
            playTogether(slideOut, fadeOut)
            duration = 200
            interpolator = DecelerateInterpolator()
        }

        outSet.start()
        outSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Execute track change
                action()

                // Reset position to opposite side instantly
                view.translationX = enterX
                view.alpha = 0f

                // Slide + fade in
                val slideIn = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, enterX, 0f)
                val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
                AnimatorSet().apply {
                    playTogether(slideIn, fadeIn)
                    duration = 250
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        })
    }

    private fun setupControls() {
        binding.apply {
            buttonBack.setOnClickListener {
                finish()
            }

            buttonQueue.setOnClickListener {
                startActivity(Intent(this@NowPlayingActivity, QueueActivity::class.java))
            }

            buttonTimer.setOnClickListener {
                SleepTimerDialog().show(supportFragmentManager, SleepTimerDialog.TAG)
            }

            buttonPlayPause.setOnClickListener {
                MusicPlaybackService.getInstance()?.togglePlayPause()
            }

            buttonNext.setOnClickListener {
                MusicPlaybackService.getInstance()?.next()
            }

            buttonPrevious.setOnClickListener {
                MusicPlaybackService.getInstance()?.previous()
            }

            buttonShuffle.setOnClickListener {
                MusicPlaybackService.getInstance()?.toggleShuffle()
            }

            buttonRepeat.setOnClickListener {
                MusicPlaybackService.getInstance()?.cycleRepeatMode()
            }

            // scrollLyrics tap → back to album art
            scrollLyrics.setOnClickListener {
                toggleLyrics(false)
            }

            textLyrics.setOnClickListener {
                toggleLyrics(false)
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        MusicPlaybackService.getInstance()?.seekTo(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            MusicPlaybackService.currentTrack.collectLatest { track ->
                track?.let {
                    binding.textTitle.text = it.title
                    binding.textArtist.text = it.artist
                    binding.textLyrics.text = it.lyrics ?: "Sin letra disponible"

                    // Load album art if available
                    it.albumArtPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(path)
                            binding.imageAlbumArt.setImageBitmap(bitmap)
                        } else {
                            binding.imageAlbumArt.setImageResource(R.drawable.ic_music_placeholder)
                        }
                    } ?: run {
                        binding.imageAlbumArt.setImageResource(R.drawable.ic_music_placeholder)
                    }
                }
            }
        }

        lifecycleScope.launch {
            MusicPlaybackService.isPlaying.collectLatest { isPlaying ->
                binding.buttonPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
        }

        lifecycleScope.launch {
            MusicPlaybackService.duration.collectLatest { duration ->
                binding.seekBar.max = duration.toInt()
                binding.textTotalTime.text = formatTime(duration)
            }
        }

        lifecycleScope.launch {
            MusicPlaybackService.currentPosition.collectLatest { position ->
                binding.seekBar.progress = position.toInt()
                binding.textCurrentTime.text = formatTime(position)
            }
        }

        lifecycleScope.launch {
            MusicPlaybackService.isShuffleEnabled.collectLatest { isEnabled ->
                binding.buttonShuffle.setImageResource(
                    if (isEnabled) R.drawable.ic_shuffle_on
                    else R.drawable.ic_shuffle
                )
            }
        }

        lifecycleScope.launch {
            MusicPlaybackService.repeatMode.collectLatest { mode ->
                binding.buttonRepeat.setImageResource(
                    when (mode) {
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                        else -> R.drawable.ic_repeat
                    }
                )
            }
        }

        lifecycleScope.launch {
            SleepTimerManager.isActive.collectLatest { isActive ->
                binding.buttonTimer.setImageResource(
                    if (isActive) R.drawable.ic_timer_active
                    else R.drawable.ic_timer
                )
                binding.textTimerRemaining.visibility = if (isActive) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        lifecycleScope.launch {
            SleepTimerManager.remainingMillis.collectLatest { millis ->
                if (SleepTimerManager.isActive.value) {
                    binding.textTimerRemaining.text = SleepTimerManager.getRemainingFormatted()
                }
            }
        }
    }

    private fun toggleLyrics(showLyrics: Boolean) {
        if (showLyrics) {
            binding.imageAlbumArt.visibility = android.view.View.GONE
            binding.scrollLyrics.visibility = android.view.View.VISIBLE
        } else {
            binding.imageAlbumArt.visibility = android.view.View.VISIBLE
            binding.scrollLyrics.visibility = android.view.View.GONE
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
