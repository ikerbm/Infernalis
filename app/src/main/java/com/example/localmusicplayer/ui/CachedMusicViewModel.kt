package com.example.localmusicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.localmusicplayer.data.local.AppDatabase
import com.example.localmusicplayer.data.model.PlaybackState
import com.example.localmusicplayer.data.model.Track
import com.example.localmusicplayer.data.repository.CachedMusicRepository
import com.example.localmusicplayer.service.MusicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel with Room database caching for large music libraries
 * Optimized for 5000+ tracks
 */
class CachedMusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val cachedRepository = CachedMusicRepository(
        database.trackDao(),
        application.contentResolver,
        application.applicationContext
    )

    // Track list state
    private val _tracks = MutableLiveData<List<Track>>(emptyList())
    val tracks: LiveData<List<Track>> = _tracks

    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Scan progress (current, total)
    private val _scanProgress = MutableLiveData<ScanProgress?>(null)
    val scanProgress: LiveData<ScanProgress?> = _scanProgress

    // Error state
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // Current playback state
    private val _playbackState = MutableLiveData(PlaybackState())
    val playbackState: LiveData<PlaybackState> = _playbackState

    // Current track index
    private var currentTrackIndex = -1

    // Job for position updates
    private var positionUpdateJob: Job? = null

    init {
        collectPlaybackState()
    }

    private fun collectPlaybackState() {
        viewModelScope.launch {
            MusicPlaybackService.isPlaying.collectLatest { isPlaying ->
                updatePlaybackState { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }
        }

        viewModelScope.launch {
            MusicPlaybackService.currentTrack.collectLatest { track ->
                updatePlaybackState { it.copy(currentTrack = track) }
            }
        }

        viewModelScope.launch {
            MusicPlaybackService.duration.collectLatest { duration ->
                updatePlaybackState { it.copy(duration = duration) }
            }
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                MusicPlaybackService.getInstance()?.let {
                    updatePlaybackState { state ->
                        state.copy(currentPosition = it.getCurrentPosition())
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updatePlaybackState(update: (PlaybackState) -> PlaybackState) {
        _playbackState.value = update(_playbackState.value ?: PlaybackState())
    }

    /**
     * Load tracks from a folder using cache
     * - First shows cached tracks instantly
     * - Then scans for new/modified files
     */
    fun loadTracksFromFolder(folderPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _scanProgress.value = null

            try {
                // Step 1: Load cached tracks instantly
                val cached = cachedRepository.getCachedTracks(folderPath)
                if (cached.isNotEmpty()) {
                    _tracks.value = cached
                    _scanProgress.value = ScanProgress(
                        current = cached.size,
                        total = cached.size,
                        message = "Cargado desde caché: ${cached.size} canciones"
                    )
                }

                // Step 2: Scan for new/modified files with progress
                val updated = cachedRepository.scanAndCacheFolder(folderPath) { current, total ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _scanProgress.value = ScanProgress(
                            current = current,
                            total = total,
                            message = if (cached.isEmpty()) {
                                "Escaneando: $current de $total archivos"
                            } else {
                                "Verificando nuevos archivos: $current de $total"
                            }
                        )
                    }
                }

                _tracks.value = updated
                _scanProgress.value = ScanProgress(
                    current = updated.size,
                    total = updated.size,
                    message = "${updated.size} canciones"
                )

            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Force full rescan of folder (clears cache first)
     */
    fun forceRescanFolder(folderPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _scanProgress.value = ScanProgress(0, 0, "Limpiando caché...")

            try {
                val tracks = cachedRepository.forceRescan(folderPath) { current, total ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _scanProgress.value = ScanProgress(
                            current = current,
                            total = total,
                            message = "Escaneando: $current de $total archivos"
                        )
                    }
                }

                _tracks.value = tracks
                _scanProgress.value = ScanProgress(
                    current = tracks.size,
                    total = tracks.size,
                    message = "${tracks.size} canciones"
                )

            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Playback controls
    
    /**
     * Play a track from the current playlist
     * Uses setPlaylist to enable next/previous functionality
     */
    fun playTrack(track: Track) {
        val trackList = _tracks.value ?: return
        val index = trackList.indexOf(track)
        if (index >= 0) {
            // Set the entire playlist with the selected track as starting point
            MusicPlaybackService.getInstance()?.setPlaylist(trackList, index)
            currentTrackIndex = index
        } else {
            // Track not in current list, play as single
            MusicPlaybackService.getInstance()?.playTrack(track)
            currentTrackIndex = -1
        }
    }

    fun playAllTracks(startIndex: Int = 0) {
        val trackList = _tracks.value ?: return
        MusicPlaybackService.getInstance()?.setPlaylist(trackList, startIndex)
        currentTrackIndex = startIndex
    }

    fun togglePlayPause() {
        MusicPlaybackService.getInstance()?.togglePlayPause()
    }

    /**
     * Skip to next track
     * The service handles the actual track change via onMediaItemTransition
     */
    fun playNext() {
        MusicPlaybackService.getInstance()?.next()
        // Service will update currentTrack via onMediaItemTransition
    }

    /**
     * Go to previous track
     * The service handles the actual track change via onMediaItemTransition
     */
    fun playPrevious() {
        MusicPlaybackService.getInstance()?.previous()
        // Service will update currentTrack via onMediaItemTransition
    }

    fun seekTo(position: Long) {
        MusicPlaybackService.getInstance()?.seekTo(position)
        updatePlaybackState { it.copy(currentPosition = position) }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
    }
}

data class ScanProgress(
    val current: Int,
    val total: Int,
    val message: String
)
