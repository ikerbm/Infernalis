package com.example.localmusicplayer.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localmusicplayer.databinding.ActivityQueueBinding
import com.example.localmusicplayer.service.MusicPlaybackService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity displaying the current playback queue
 */
class QueueActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQueueBinding
    private lateinit var queueAdapter: TrackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadQueue()
        observeCurrentTrack()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        queueAdapter = TrackAdapter { track ->
            // Find track in queue and play from that position
            val queue = MusicPlaybackService.getInstance()?.getCurrentQueue() ?: return@TrackAdapter
            val index = queue.indexOf(track)
            if (index >= 0) {
                MusicPlaybackService.getInstance()?.setPlaylist(queue, index)
            }
        }

        binding.recyclerQueue.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(this@QueueActivity)
            setHasFixedSize(true)
        }
    }

    private fun loadQueue() {
        val queue = MusicPlaybackService.getInstance()?.getCurrentQueue() ?: emptyList()
        queueAdapter.submitList(queue)
        binding.textEmpty.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun observeCurrentTrack() {
        lifecycleScope.launch {
            MusicPlaybackService.currentTrack.collectLatest { track ->
                track?.let {
                    queueAdapter.setCurrentTrackId(it.id)
                }
            }
        }
    }
}
