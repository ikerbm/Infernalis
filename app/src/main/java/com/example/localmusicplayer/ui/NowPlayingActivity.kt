package com.example.localmusicplayer.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.model.PlaybackState
import com.example.localmusicplayer.databinding.ActivityNowPlayingBinding
import com.example.localmusicplayer.service.MusicPlaybackService
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupControls()
        observePlaybackState()
    }

    private fun setupControls() {
        binding.apply {
            buttonBack.setOnClickListener {
                finish()
            }

            buttonQueue.setOnClickListener {
                startActivity(Intent(this@NowPlayingActivity, QueueActivity::class.java))
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

            // Toggle between album art and lyrics
            imageAlbumArt.setOnClickListener {
                toggleLyrics(true)
            }

            scrollLyrics.setOnClickListener {
                toggleLyrics(false)
            }
            
            // Also handle internal text click just in case
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
