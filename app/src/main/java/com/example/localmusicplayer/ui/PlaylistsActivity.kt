package com.example.localmusicplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.local.AppDatabase
import com.example.localmusicplayer.data.local.PlaylistEntity
import com.example.localmusicplayer.databinding.ActivityPlaylistsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity that displays all playlists.
 * Shows the built-in "Recientes" playlist first, followed by user-created playlists.
 * Provides a FAB to create new playlists and long-press for rename/delete.
 */
class PlaylistsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistsBinding
    private lateinit var playlistAdapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
    }

    override fun onResume() {
        super.onResume()
        loadPlaylists()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            onClick = { playlistItem ->
                // Open playlist detail
                val intent = Intent(this, PlaylistDetailActivity::class.java).apply {
                    putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlistItem.id)
                    putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_NAME, playlistItem.name)
                    putExtra(PlaylistDetailActivity.EXTRA_IS_DEFAULT, playlistItem.isDefault)
                }
                startActivity(intent)
            },
            onLongClick = { playlistItem ->
                if (!playlistItem.isDefault) {
                    showPlaylistOptionsDialog(playlistItem)
                }
            }
        )

        binding.recyclerPlaylists.apply {
            adapter = playlistAdapter
            layoutManager = LinearLayoutManager(this@PlaylistsActivity)
            setHasFixedSize(false)
        }
    }

    private fun setupFab() {
        binding.fabCreatePlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun loadPlaylists() {
        val db = AppDatabase.getInstance(applicationContext)
        val playlistDao = db.playlistDao()
        val trackDao = db.trackDao()

        lifecycleScope.launch {
            val items = mutableListOf<PlaylistItem>()

            // Add default "Recientes" playlist first
            val recentCount = withContext(Dispatchers.IO) {
                playlistDao.getRecentTracks().size
            }
            items.add(
                PlaylistItem(
                    id = PlaylistDetailActivity.RECIENTES_PLAYLIST_ID,
                    name = getString(R.string.recent_tracks),
                    trackCount = recentCount,
                    isDefault = true
                )
            )

            // Add user-created playlists
            val playlists = withContext(Dispatchers.IO) {
                playlistDao.getAllPlaylists()
            }
            for (playlist in playlists) {
                val count = withContext(Dispatchers.IO) {
                    playlistDao.getTrackCountInPlaylist(playlist.id)
                }
                items.add(
                    PlaylistItem(
                        id = playlist.id,
                        name = playlist.name,
                        trackCount = count
                    )
                )
            }

            playlistAdapter.submitList(items)

            // Show empty state only if there are no user playlists (Recientes is always there)
            binding.textEmpty.visibility = if (items.size <= 1 && recentCount == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.playlist_name_hint)
            setPadding(64, 32, 64, 16)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_playlist_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createPlaylist(name)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createPlaylist(name: String) {
        val db = AppDatabase.getInstance(applicationContext)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.playlistDao().createPlaylist(PlaylistEntity(name = name))
            }
            Toast.makeText(
                this@PlaylistsActivity,
                getString(R.string.playlist_created, name),
                Toast.LENGTH_SHORT
            ).show()
            loadPlaylists()
        }
    }

    private fun showPlaylistOptionsDialog(playlistItem: PlaylistItem) {
        val options = arrayOf(
            getString(R.string.rename_playlist),
            getString(R.string.delete_playlist)
        )

        AlertDialog.Builder(this)
            .setTitle(playlistItem.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(playlistItem)
                    1 -> showDeleteConfirmation(playlistItem)
                }
            }
            .show()
    }

    private fun showRenameDialog(playlistItem: PlaylistItem) {
        val editText = EditText(this).apply {
            setText(playlistItem.name)
            setPadding(64, 32, 64, 16)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_playlist_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val db = AppDatabase.getInstance(applicationContext)
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            db.playlistDao().renamePlaylist(playlistItem.id, newName)
                        }
                        Toast.makeText(
                            this@PlaylistsActivity,
                            getString(R.string.playlist_renamed),
                            Toast.LENGTH_SHORT
                        ).show()
                        loadPlaylists()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteConfirmation(playlistItem: PlaylistItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_playlist))
            .setMessage(getString(R.string.delete_playlist_confirm, playlistItem.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val db = AppDatabase.getInstance(applicationContext)
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.playlistDao().deletePlaylist(playlistItem.id)
                    }
                    Toast.makeText(
                        this@PlaylistsActivity,
                        getString(R.string.playlist_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadPlaylists()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
