package com.example.localmusicplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.model.Track
import com.example.localmusicplayer.databinding.ItemTrackBinding
import java.io.File

/**
 * RecyclerView adapter for displaying track list
 * Uses ListAdapter with DiffUtil for efficient updates
 * Uses Coil for efficient image loading with caching
 */
class TrackAdapter(
    private val onTrackClick: (Track) -> Unit
) : ListAdapter<Track, TrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    private var currentTrackId: Long? = null
    private var currentTrackIndex: Int = -1

    fun setCurrentTrackId(id: Long) {
        val oldId = currentTrackId
        val oldIndex = currentTrackIndex
        currentTrackId = id
        // Find the new current track index
        currentTrackIndex = currentList.indexOfFirst { it.id == id }
        // Refresh all items between old and new positions, plus the endpoints
        val minIndex = minOf(oldIndex, currentTrackIndex).coerceAtLeast(0)
        val maxIndex = maxOf(oldIndex, currentTrackIndex).coerceAtMost(currentList.size - 1)
        if (minIndex <= maxIndex) {
            notifyItemRangeChanged(minIndex, maxIndex - minIndex + 1)
        }
    }

    fun getCurrentTrackIndex(): Int = currentTrackIndex

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrackViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTrackClick(getItem(position))
                }
            }
        }

        fun bind(track: Track) {
            val position = bindingAdapterPosition
            binding.apply {
                textTitle.text = track.title
                textArtist.text = track.artist
                textDuration.text = track.getFormattedDuration()

                // Determine if this track has already been played
                val isCurrentTrack = track.id == currentTrackId
                val isPlayedTrack = currentTrackIndex >= 0 && position < currentTrackIndex

                // Highlight current track, dim played tracks
                when {
                    isCurrentTrack -> {
                        textTitle.setTextColor(root.context.getColor(R.color.accent))
                        textArtist.alpha = 1.0f
                        textDuration.alpha = 1.0f
                        imageTrack.alpha = 1.0f
                    }
                    isPlayedTrack -> {
                        textTitle.setTextColor(root.context.getColor(R.color.text_disabled))
                        textArtist.alpha = 0.45f
                        textDuration.alpha = 0.45f
                        imageTrack.alpha = 0.45f
                    }
                    else -> {
                        textTitle.setTextColor(root.context.getColor(R.color.text_primary))
                        textArtist.alpha = 1.0f
                        textDuration.alpha = 1.0f
                        imageTrack.alpha = 1.0f
                    }
                }
                
                // Load album art using Coil
                val albumArtFile = track.albumArtPath?.let { File(it) }
                if (albumArtFile != null && albumArtFile.exists()) {
                    imageTrack.load(albumArtFile) {
                        crossfade(true)
                        placeholder(R.drawable.ic_music_note)
                        error(R.drawable.ic_music_note)
                        transformations(RoundedCornersTransformation(8f))
                    }
                    imageTrack.setPadding(0, 0, 0, 0)
                } else {
                    imageTrack.load(R.drawable.ic_music_note) {
                        transformations(RoundedCornersTransformation(8f))
                    }
                    imageTrack.setPadding(12, 12, 12, 12)
                }
            }
        }
    }

    private class TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
        override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean {
            return oldItem == newItem
        }
    }
}

