package com.example.localmusicplayer.ui

import android.widget.PopupMenu
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localmusicplayer.R
import com.example.localmusicplayer.databinding.ActivityMainBinding
import com.example.localmusicplayer.service.MusicPlaybackService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


/**
 * Main activity for the music player
 * Displays track list and playback controls
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "music_player_prefs"
        private const val KEY_SELECTED_FOLDER = "selected_folder_path"
    }

    // Use CachedMusicViewModel for optimized loading with Room database
    private val viewModel: CachedMusicViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkAllFilesAccess()
        } else {
            Toast.makeText(
                this,
                "Se requieren permisos para acceder a la música",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Launcher for MANAGE_EXTERNAL_STORAGE settings
    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            startMusicService()
            loadTracksFromSelectedFolder()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            startMusicService()
            loadTracksFromSelectedFolder()
        } else {
            startMusicService()
            loadTracksFromSelectedFolder()
            Toast.makeText(
                this,
                "Acceso limitado: algunos archivos pueden no aparecer",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Folder picker launcher
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUri ->
            // Persist permission for future access
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            // Convert to actual file path
            val path = getPathFromUri(treeUri)
            
            if (path != null) {
                // Save to preferences
                prefs.edit().putString(KEY_SELECTED_FOLDER, path).apply()
                updateFolderDisplay(path)
                
                // Reload tracks from the selected folder
                viewModel.loadTracksFromFolder(path)
                
                Toast.makeText(this, "Carpeta seleccionada: $path", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No se pudo acceder a la carpeta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupRecyclerView()
        setupFolderPicker()
        observeViewModel()
        checkPermissions()
    }

    private fun setupFolderPicker() {
        // Load saved folder path
        val savedFolder = prefs.getString(KEY_SELECTED_FOLDER, null)
        updateFolderDisplay(savedFolder)

        // Setup toolbar menu click listener
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_select_folder -> {
                    folderPickerLauncher.launch(null)
                    true
                }
                R.id.action_rescan -> {
                    val folder = prefs.getString(KEY_SELECTED_FOLDER, null)
                    if (folder != null) {
                        // Force rescan clears cache and re-scans everything
                        viewModel.forceRescanFolder(folder)
                    } else {
                        Toast.makeText(this, "Selecciona una carpeta primero", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_view_genres -> {
                    startActivity(Intent(this, GenreActivity::class.java))
                    true
                }
                R.id.action_view_playlists -> {
                    startActivity(Intent(this, PlaylistsActivity::class.java))
                    true
                }
                R.id.action_search -> {
                    startActivity(Intent(this, SearchActivity::class.java))
                    true
                }
                R.id.action_sleep_timer -> {
                    SleepTimerDialog().show(supportFragmentManager, SleepTimerDialog.TAG)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFolderDisplay(path: String?) {
        if (path != null) {
            binding.textSelectedFolder.text = getString(R.string.folder_selected, path)
        } else {
            binding.textSelectedFolder.text = getString(R.string.no_folder_selected)
        }
    }

    private fun loadTracksFromSelectedFolder() {
        val savedFolder = prefs.getString(KEY_SELECTED_FOLDER, null)
        if (savedFolder != null) {
            viewModel.loadTracksFromFolder(savedFolder)
        } else {
            // Show message to select a folder
            binding.textTrackCount.text = "Selecciona una carpeta para comenzar"
        }
    }

    /**
     * Convert a Document Tree URI to an absolute file path
     */
    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            
            if (type.equals("primary", ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory().absolutePath}/${split.getOrElse(1) { "" }}"
            } else {
                // External SD card or other storage
                "/storage/$type/${split.getOrElse(1) { "" }}"
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun setupRecyclerView() {
        trackAdapter = TrackAdapter(
            onTrackClick = { track ->
                viewModel.playTrack(track)
                // Launch Now Playing Activity
                startActivity(Intent(this, NowPlayingActivity::class.java))
            },
            onMoreClick = { track, anchorView ->
                showTrackPopupMenu(track, anchorView)
            }
        )

        binding.recyclerTracks.apply {
            adapter = trackAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
        }
    }

    private fun showTrackPopupMenu(track: com.example.localmusicplayer.data.model.Track, anchorView: android.view.View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_track_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_play_next -> {
                    val service = MusicPlaybackService.getInstance()
                    if (service != null) {
                        service.playNext(track)
                        Toast.makeText(this, R.string.track_added_to_queue, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No hay reproducción activa", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
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

    private fun observeViewModel() {
        viewModel.tracks.observe(this) { tracks ->
            trackAdapter.submitList(tracks)
            binding.textEmpty.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        // Observe scan progress for cache updates
        viewModel.scanProgress.observe(this) { progress ->
            progress?.let {
                binding.textTrackCount.text = it.message
            }
        }

        // Observe playback state for mini-player
        lifecycleScope.launch {
            MusicPlaybackService.currentTrack.collectLatest { track ->
                track?.let {
                    binding.playerContainer.visibility = View.VISIBLE
                    binding.textCurrentTitle.text = it.title
                    binding.textCurrentArtist.text = it.artist
                    
                    // Load album art in mini-player
                    val albumArtFile = it.albumArtPath?.let { path -> java.io.File(path) }
                    if (albumArtFile != null && albumArtFile.exists()) {
                        binding.imageMiniArt.setImageBitmap(
                            android.graphics.BitmapFactory.decodeFile(albumArtFile.absolutePath)
                        )
                    } else {
                        binding.imageMiniArt.setImageResource(R.drawable.ic_music_note)
                    }
                }
            }
        }

        lifecycleScope.launch {
            MusicPlaybackService.isPlaying.collectLatest { isPlaying ->
                binding.buttonMiniPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
        }

        // Mini-player click handlers
        binding.playerContainer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }

        binding.buttonMiniPlayPause.setOnClickListener {
            MusicPlaybackService.getInstance()?.togglePlayPause()
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            checkAllFilesAccess()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    /**
     * Check and request MANAGE_EXTERNAL_STORAGE permission on Android 11+
     * This is required to scan files not indexed by MediaStore
     */
    private fun checkAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Already have permission
                startMusicService()
                loadTracksFromSelectedFolder()
            } else {
                // Need to request permission
                AlertDialog.Builder(this)
                    .setTitle("Acceso a todos los archivos")
                    .setMessage(
                        "Para encontrar TODOS los archivos de música en tu dispositivo, " +
                        "incluyendo aquellos no indexados por el sistema, necesitamos " +
                        "permiso de acceso a todos los archivos.\n\n" +
                        "Si no lo otorgas, solo se mostrarán archivos indexados por MediaStore."
                    )
                    .setPositiveButton("Conceder acceso") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            allFilesAccessLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            allFilesAccessLauncher.launch(intent)
                        }
                    }
                    .setNegativeButton("Continuar sin acceso completo") { _, _ ->
                        startMusicService()
                        loadTracksFromSelectedFolder()
                    }
                    .setCancelable(false)
                    .show()
            }
        } else {
            // Android 10 or below - legacy storage access works fine
            startMusicService()
            loadTracksFromSelectedFolder()
        }
    }

    private fun startMusicService() {
        val intent = Intent(this, MusicPlaybackService::class.java)
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service sigue corriendo en background
    }
}
