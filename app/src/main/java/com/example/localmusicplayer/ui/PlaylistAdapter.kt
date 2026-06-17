package com.example.localmusicplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.localmusicplayer.R
import com.example.localmusicplayer.databinding.ItemPlaylistBinding

/**
 * Data class representing a playlist item in the list.
 * Supports both user-created playlists and the built-in "Recientes" playlist.
 */
data class PlaylistItem(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val isDefault: Boolean = false  // true for "Recientes"
)

/**
 * RecyclerView adapter for displaying playlist list
 */
class PlaylistAdapter(
    private val onClick: (PlaylistItem) -> Unit,
    private val onLongClick: ((PlaylistItem) -> Unit)? = null
) : ListAdapter<PlaylistItem, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongClick?.invoke(getItem(position))
                }
                true
            }
        }

        fun bind(item: PlaylistItem) {
            binding.textPlaylistName.text = item.name
            binding.textTrackCount.text = binding.root.context.getString(
                R.string.tracks_count, item.trackCount
            )

            // Use different icon for the "Recientes" default playlist
            if (item.isDefault) {
                binding.imagePlaylist.setImageResource(R.drawable.ic_recent)
            } else {
                binding.imagePlaylist.setImageResource(R.drawable.ic_playlist)
            }
        }
    }

    private class PlaylistDiffCallback : DiffUtil.ItemCallback<PlaylistItem>() {
        override fun areItemsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem == newItem
        }
    }
}
