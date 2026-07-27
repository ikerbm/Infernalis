package com.example.localmusicplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.model.Track
import com.example.localmusicplayer.databinding.ItemGenreHeaderBinding
import com.example.localmusicplayer.databinding.ItemTrackBinding
import java.io.File

/**
 * RecyclerView adapter for displaying tracks grouped by genre.
 * Supports expandable genre headers with track items inside.
 */
class GenreAdapter(
    private val onTrackClick: (Track, List<Track>) -> Unit,
    private val onMoreClick: ((Track, View) -> Unit)? = null,
    private val onGenreMoreClick: ((genre: String, tracks: List<Track>, anchorView: View) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TRACK = 1
    }

    /**
     * Represents a single item in the flat list: either a genre header or a track.
     */
    sealed class ListItem {
        data class Header(
            val genre: String,
            val trackCount: Int,
            var isExpanded: Boolean = false
        ) : ListItem()

        data class TrackItem(
            val track: Track,
            val genre: String
        ) : ListItem()
    }

    // All genre data (genre -> tracks)
    private var genreMap: Map<String, List<Track>> = emptyMap()

    // Flattened list of items currently visible
    private var items: MutableList<ListItem> = mutableListOf()

    fun submitData(data: Map<String, List<Track>>) {
        genreMap = data
        rebuildList()
        notifyDataSetChanged()
    }

    /**
     * Rebuild the flat list from the genre map,
     * showing tracks only for expanded genres.
     */
    private fun rebuildList() {
        val newItems = mutableListOf<ListItem>()
        // Sort genres alphabetically, but put "Género desconocido" last
        val sortedGenres = genreMap.keys.sortedWith(compareBy { 
            if (it == "Género desconocido") "\uFFFF" else it.lowercase() 
        })
        for (genre in sortedGenres) {
            val tracks = genreMap[genre] ?: continue
            val existingHeader = items.filterIsInstance<ListItem.Header>()
                .find { it.genre == genre }
            val isExpanded = existingHeader?.isExpanded ?: false

            newItems.add(ListItem.Header(genre, tracks.size, isExpanded))
            if (isExpanded) {
                tracks.forEach { track ->
                    newItems.add(ListItem.TrackItem(track, genre))
                }
            }
        }
        items = newItems
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.Header -> TYPE_HEADER
            is ListItem.TrackItem -> TYPE_TRACK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemGenreHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GenreHeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemTrackBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                TrackViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> (holder as GenreHeaderViewHolder).bind(item)
            is ListItem.TrackItem -> (holder as TrackViewHolder).bind(item)
        }
    }

    inner class GenreHeaderViewHolder(
        private val binding: ItemGenreHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val header = items[pos] as? ListItem.Header ?: return@setOnClickListener
                    header.isExpanded = !header.isExpanded
                    rebuildList()
                    notifyDataSetChanged()
                }
            }

            binding.buttonGenreMore.setOnClickListener { view ->
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val header = items[pos] as? ListItem.Header ?: return@setOnClickListener
                    val tracks = genreMap[header.genre] ?: emptyList()
                    onGenreMoreClick?.invoke(header.genre, tracks, view)
                }
            }
        }

        fun bind(header: ListItem.Header) {
            binding.textGenreName.text = header.genre
            binding.textTrackCount.text = "${header.trackCount} canciones"
            // Rotate arrow based on expanded state
            binding.imageExpand.rotation = if (header.isExpanded) 180f else 0f
            // Show/hide the more button based on whether a callback is provided
            binding.buttonGenreMore.visibility =
                if (onGenreMoreClick != null) View.VISIBLE else View.GONE
        }
    }

    inner class TrackViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val trackItem = items[pos] as? ListItem.TrackItem ?: return@setOnClickListener
                    val genreTracks = genreMap[trackItem.genre] ?: listOf(trackItem.track)
                    onTrackClick(trackItem.track, genreTracks)
                }
            }
        }

        fun bind(item: ListItem.TrackItem) {
            val track = item.track
            binding.textTitle.text = track.title
            binding.textArtist.text = track.artist
            binding.textDuration.text = track.getFormattedDuration()
            binding.textTitle.setTextColor(
                binding.root.context.getColor(R.color.text_primary)
            )
            binding.buttonMore.visibility = if (onMoreClick != null) {
                View.VISIBLE
            } else {
                View.GONE
            }
            binding.buttonMore.setOnClickListener { view ->
                onMoreClick?.invoke(track, view)
            }

            // Load album art using Coil
            val albumArtFile = track.albumArtPath?.let { File(it) }
            if (albumArtFile != null && albumArtFile.exists()) {
                binding.imageTrack.load(albumArtFile) {
                    crossfade(true)
                    placeholder(R.drawable.ic_music_note)
                    error(R.drawable.ic_music_note)
                    transformations(RoundedCornersTransformation(8f))
                }
                binding.imageTrack.setPadding(0, 0, 0, 0)
            } else {
                binding.imageTrack.load(R.drawable.ic_music_note) {
                    transformations(RoundedCornersTransformation(8f))
                }
                binding.imageTrack.setPadding(12, 12, 12, 12)
            }
        }
    }
}
