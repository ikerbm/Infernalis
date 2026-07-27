package com.example.localmusicplayer.ui

import android.content.Context
import android.content.Intent
import android.widget.PopupMenu
import com.example.localmusicplayer.R
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localmusicplayer.data.local.AppDatabase
import com.example.localmusicplayer.data.model.Track
import com.example.localmusicplayer.data.repository.CachedMusicRepository
import com.example.localmusicplayer.databinding.ActivitySearchBinding
import com.example.localmusicplayer.service.MusicPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity for searching tracks by title, artist, album, or genre.
 * Debounced search triggers after 300ms of typing inactivity.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var repository: CachedMusicRepository
    private var searchJob: Job? = null
    private var folderPath: String? = null
    private var allSearchResults: List<Track> = emptyList()

    companion object {
        private const val PREFS_NAME = "music_player_prefs"
        private const val KEY_SELECTED_FOLDER = "selected_folder_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        folderPath = prefs.getString(KEY_SELECTED_FOLDER, null)

        val db = AppDatabase.getInstance(applicationContext)
        repository = CachedMusicRepository(db.trackDao(), contentResolver, applicationContext)

        setupUI()
        focusSearchInput()
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonClear.setOnClickListener {
            binding.editSearch.text.clear()
        }

        trackAdapter = TrackAdapter(
            onTrackClick = { track ->
                // Play the selected track within search results
                val index = allSearchResults.indexOf(track)
                if (index >= 0) {
                    MusicPlaybackService.getInstance()?.setPlaylist(allSearchResults, index)
                } else {
                    MusicPlaybackService.getInstance()?.playTrack(track)
                }
                startActivity(Intent(this, NowPlayingActivity::class.java))
            },
            onMoreClick = { track, anchorView ->
                showTrackPopupMenu(track, anchorView)
            }
        )

        binding.recyclerResults.apply {
            adapter = trackAdapter
            layoutManager = LinearLayoutManager(this@SearchActivity)
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                binding.buttonClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
        })
    }

    private fun focusSearchInput() {
        binding.editSearch.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.editSearch.postDelayed({
            imm.showSoftInput(binding.editSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.isEmpty()) {
            binding.recyclerResults.visibility = View.GONE
            binding.textResultCount.visibility = View.GONE
            binding.textEmpty.visibility = View.VISIBLE
            allSearchResults = emptyList()
            trackAdapter.submitList(emptyList())
            return
        }

        searchJob = lifecycleScope.launch {
            delay(300) // debounce

            val folder = folderPath ?: return@launch

            try {
                val results = repository.searchTracks(folder, query)
                allSearchResults = results

                if (results.isEmpty()) {
                    binding.recyclerResults.visibility = View.GONE
                    binding.textResultCount.visibility = View.GONE
                    binding.textEmpty.visibility = View.VISIBLE
                    binding.textEmpty.text = "No se encontraron resultados para \"$query\""
                } else {
                    binding.textEmpty.visibility = View.GONE
                    binding.recyclerResults.visibility = View.VISIBLE
                    binding.textResultCount.visibility = View.VISIBLE
                    binding.textResultCount.text = "${results.size} resultado${if (results.size != 1) "s" else ""}"
                    trackAdapter.submitList(results)
                }
            } catch (_: Exception) {
                // Query cancelled or error
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
}
