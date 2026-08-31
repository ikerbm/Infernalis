package com.example.localmusicplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.local.AppDatabase
import com.example.localmusicplayer.data.model.Track
import com.example.localmusicplayer.databinding.ActivityPlaylistDetailBinding
import com.example.localmusicplayer.service.MusicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Activity that displays the tracks inside a playlist.
 * Handles both user-created playlists and the built-in "Recientes" playlist.
 */
class PlaylistDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
        const val EXTRA_PLAYLIST_NAME = "playlist_name"
        const val EXTRA_IS_DEFAULT = "is_default"
        const val RECIENTES_PLAYLIST_ID = -1L
    }

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var trackAdapter: TrackAdapter
    private var playlistId: Long = 0
    private var isDefaultPlaylist = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, 0)
        val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: ""
        isDefaultPlaylist = intent.getBooleanExtra(EXTRA_IS_DEFAULT, false)

        setupToolbar(playlistName)
        setupRecyclerView()
        loadTracks()
    }

    private fun setupToolbar(name: String) {
        binding.toolbar.title = name
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        trackAdapter = TrackAdapter(
            onTrackClick = { track ->
                // Play the selected track within the playlist as context
                val trackList = trackAdapter.currentList
                val index = trackList.indexOf(track)
                if (index >= 0) {
                    MusicPlaybackService.getInstance()?.setPlaylist(trackList, index)
                } else {
                    MusicPlaybackService.getInstance()?.playTrack(track)
                }
                startActivity(Intent(this, NowPlayingActivity::class.java))
            },
            onMoreClick = { track, anchorView ->
                showTrackPopupMenu(track, anchorView)
            }
        )

        binding.recyclerTracks.apply {
            adapter = trackAdapter
            layoutManager = LinearLayoutManager(this@PlaylistDetailActivity)
            setHasFixedSize(true)
        }
    }

    private fun showTrackPopupMenu(track: Track, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_track_options, popup.menu)
        
        // Add "Remove from this playlist" option if it's a user-created playlist
        if (playlistId != RECIENTES_PLAYLIST_ID && !isDefaultPlaylist) {
            popup.menu.add(0, 2, 2, "Eliminar de esta lista")
        }
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_play_next -> {
                    val service = MusicPlaybackService.getInstance()
                    if (service != null) {
                        service.playNext(track)
                        android.widget.Toast.makeText(this, R.string.track_added_to_queue, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(this, "No hay reproducción activa", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_add_to_playlist -> {
                    AddToPlaylistDialog.newInstance(track.path, track.title)
                        .show(supportFragmentManager, AddToPlaylistDialog.TAG)
                    true
                }
                2 -> {
                    removeTrackFromPlaylist(track)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun removeTrackFromPlaylist(track: Track) {
        val db = AppDatabase.getInstance(applicationContext)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db.playlistDao().removeTrackFromPlaylist(playlistId, track.path)
                }
                Toast.makeText(
                    this@PlaylistDetailActivity,
                    "Canción eliminada de la lista",
                    Toast.LENGTH_SHORT
                ).show()
                loadTracks() // Refresh track list
            } catch (e: Exception) {
                Toast.makeText(
                    this@PlaylistDetailActivity,
                    "Error al eliminar: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadTracks() {
        binding.progressBar.visibility = View.VISIBLE

        val db = AppDatabase.getInstance(applicationContext)
        val playlistDao = db.playlistDao()

        lifecycleScope.launch {
            try {
                val trackEntities = withContext(Dispatchers.IO) {
                    if (playlistId == RECIENTES_PLAYLIST_ID) {
                        // Load last 200 recent tracks
                        playlistDao.getRecentTracks()
                    } else {
                        // Load tracks in this user playlist
                        playlistDao.getTracksInPlaylist(playlistId)
                    }
                }

                val tracks = trackEntities.map { entity ->
                    Track(
                        id = entity.path.hashCode().toLong(),
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        duration = entity.duration,
                        uri = Uri.fromFile(File(entity.path)),
                        path = entity.path,
                        dateAdded = entity.dateAdded / 1000,
                        size = entity.size,
                        albumArtPath = entity.albumArtPath,
                        lyrics = entity.lyrics,
                        genre = entity.genre
                    )
                }

                binding.progressBar.visibility = View.GONE
                trackAdapter.submitList(tracks)

                binding.textTrackCount.text = getString(R.string.tracks_count, tracks.size)

                if (tracks.isEmpty()) {
                    binding.textEmpty.visibility = View.VISIBLE
                    binding.recyclerTracks.visibility = View.GONE
                } else {
                    binding.textEmpty.visibility = View.GONE
                    binding.recyclerTracks.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.textEmpty.visibility = View.VISIBLE
                Toast.makeText(
                    this@PlaylistDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
