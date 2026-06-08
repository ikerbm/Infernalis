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

    fun setCurrentTrackId(id: Long) {
        val oldId = currentTrackId
        currentTrackId = id
        // Refresh items that changed
        currentList.forEachIndexed { index, track ->
            if (track.id == oldId || track.id == id) {
                notifyItemChanged(index)
            }
        }
    }

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
            binding.apply {
                textTitle.text = track.title
                textArtist.text = track.artist
                textDuration.text = track.getFormattedDuration()

                // Highlight current track
                val isCurrentTrack = track.id == currentTrackId
                if (isCurrentTrack) {
                    textTitle.setTextColor(root.context.getColor(R.color.accent))
                } else {
                    textTitle.setTextColor(root.context.getColor(R.color.text_primary))
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

