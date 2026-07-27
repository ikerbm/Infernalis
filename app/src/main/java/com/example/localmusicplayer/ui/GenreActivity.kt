package com.example.localmusicplayer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.model.Track
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localmusicplayer.data.local.AppDatabase
import com.example.localmusicplayer.data.repository.CachedMusicRepository
import com.example.localmusicplayer.databinding.ActivityGenreBinding
import com.example.localmusicplayer.service.MusicPlaybackService
import kotlinx.coroutines.launch

/**
 * Activity that displays tracks grouped by genre.
 * Genres are shown as expandable headers.
 */
class GenreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGenreBinding
    private lateinit var genreAdapter: GenreAdapter

    companion object {
        private const val PREFS_NAME = "music_player_prefs"
        private const val KEY_SELECTED_FOLDER = "selected_folder_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadGenres()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        genreAdapter = GenreAdapter(
            onTrackClick = { track, genreTracks ->
                // Play the selected track within its genre playlist
                val index = genreTracks.indexOf(track)
                if (index >= 0) {
                    MusicPlaybackService.getInstance()?.setPlaylist(genreTracks, index)
                } else {
                    MusicPlaybackService.getInstance()?.playTrack(track)
                }
                // Open Now Playing
                startActivity(Intent(this, NowPlayingActivity::class.java))
            },
            onMoreClick = { track, anchorView ->
                showTrackPopupMenu(track, anchorView)
            },
            onGenreMoreClick = { genre, tracks, anchorView ->
                showGenrePopupMenu(genre, tracks, anchorView)
            }
        )

        binding.recyclerGenres.apply {
            adapter = genreAdapter
            layoutManager = LinearLayoutManager(this@GenreActivity)
            setHasFixedSize(false)
        }
    }

    private fun loadGenres() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val folderPath = prefs.getString(KEY_SELECTED_FOLDER, null)

        if (folderPath == null) {
            binding.textEmpty.visibility = View.VISIBLE
            binding.textEmpty.text = "Selecciona una carpeta primero"
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        val db = AppDatabase.getInstance(applicationContext)
        val repository = CachedMusicRepository(
            db.trackDao(),
            contentResolver,
            applicationContext
        )

        lifecycleScope.launch {
            try {
                val genreMap = repository.getTracksGroupedByGenre(folderPath)
                binding.progressBar.visibility = View.GONE

                if (genreMap.isEmpty()) {
                    binding.textEmpty.visibility = View.VISIBLE
                    binding.recyclerGenres.visibility = View.GONE
                } else {
                    binding.textEmpty.visibility = View.GONE
                    binding.recyclerGenres.visibility = View.VISIBLE
                    genreAdapter.submitData(genreMap)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.textEmpty.visibility = View.VISIBLE
                Toast.makeText(
                    this@GenreActivity,
                    "Error al cargar géneros: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showTrackPopupMenu(track: Track, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_track_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_to_playlist -> {
                    AddToPlaylistDialog.newInstance(track.path, track.title)
                        .show(supportFragmentManager, AddToPlaylistDialog.TAG)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showGenrePopupMenu(genre: String, tracks: List<Track>, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_genre_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_genre_to_queue -> {
                    val service = MusicPlaybackService.getInstance()
                    if (service != null) {
                        service.addToQueue(tracks)
                        Toast.makeText(
                            this,
                            "${tracks.size} canciones de \"$genre\" añadidas a la cola",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "El servicio de reproducción no está disponible",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
                R.id.action_play_genre -> {
                    if (tracks.isNotEmpty()) {
                        MusicPlaybackService.getInstance()?.setPlaylist(tracks, 0)
                        startActivity(Intent(this, NowPlayingActivity::class.java))
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
