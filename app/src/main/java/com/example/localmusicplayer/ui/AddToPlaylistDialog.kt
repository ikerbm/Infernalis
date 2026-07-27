package com.example.localmusicplayer.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.localmusicplayer.R
import com.example.localmusicplayer.data.local.AppDatabase
import com.example.localmusicplayer.data.local.PlaylistEntity
import com.example.localmusicplayer.data.local.PlaylistTrackCrossRef
import com.example.localmusicplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog that shows available playlists and allows adding a track to one.
 * Also provides an option to create a new playlist inline.
 */
class AddToPlaylistDialog : DialogFragment() {

    companion object {
        const val TAG = "AddToPlaylistDialog"

        fun newInstance(trackPath: String, trackTitle: String): AddToPlaylistDialog {
            return AddToPlaylistDialog().apply {
                arguments = Bundle().apply {
                    putString("track_path", trackPath)
                    putString("track_title", trackTitle)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val trackPath = arguments?.getString("track_path") ?: return super.onCreateDialog(savedInstanceState)
        val trackTitle = arguments?.getString("track_title") ?: ""

        val db = AppDatabase.getInstance(requireContext())
        val playlistDao = db.playlistDao()

        val view = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_add_to_playlist, null
        )

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerPlaylists)
        val adapter = PlaylistAdapter(
            onClick = { playlistItem ->
                // Add track to this playlist
                lifecycleScope.launch {
                    val alreadyExists = withContext(Dispatchers.IO) {
                        playlistDao.isTrackInPlaylist(playlistItem.id, trackPath)
                    }
                    if (alreadyExists) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.track_already_in_playlist, playlistItem.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            val position = playlistDao.getNextPosition(playlistItem.id)
                            playlistDao.addTrackToPlaylist(
                                PlaylistTrackCrossRef(
                                    playlistId = playlistItem.id,
                                    trackPath = trackPath,
                                    position = position
                                )
                            )
                        }
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.track_added_to_playlist, playlistItem.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    dismiss()
                }
            }
        )

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Load playlists
        lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) {
                playlistDao.getAllPlaylists()
            }
            val items = playlists.map { playlist ->
                val count = withContext(Dispatchers.IO) {
                    playlistDao.getTrackCountInPlaylist(playlist.id)
                }
                PlaylistItem(
                    id = playlist.id,
                    name = playlist.name,
                    trackCount = count
                )
            }
            adapter.submitList(items)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_to_playlist_title))
            .setView(view)
            .setNeutralButton(getString(R.string.create_playlist)) { _, _ ->
                showCreatePlaylistAndAdd(trackPath)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    private fun showCreatePlaylistAndAdd(trackPath: String) {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name_hint)
            setPadding(64, 32, 64, 16)
            setTextColor(requireContext().getColor(R.color.text_primary))
            setHintTextColor(requireContext().getColor(R.color.text_secondary))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.create_playlist_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(requireContext())
                        val playlistDao = db.playlistDao()
                        withContext(Dispatchers.IO) {
                            val playlistId = playlistDao.createPlaylist(
                                PlaylistEntity(name = name)
                            )
                            playlistDao.addTrackToPlaylist(
                                PlaylistTrackCrossRef(
                                    playlistId = playlistId,
                                    trackPath = trackPath,
                                    position = 0
                                )
                            )
                        }
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.playlist_created, name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
