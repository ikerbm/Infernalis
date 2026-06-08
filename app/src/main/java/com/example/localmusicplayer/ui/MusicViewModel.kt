package com.example.localmusicplayer.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localmusicplayer.data.model.PlaybackState
import com.example.localmusicplayer.data.model.Track
import com.example.localmusicplayer.data.repository.MusicRepository
import com.example.localmusicplayer.service.MusicPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.localmusicplayer.data.model.MediaScanReport

/**
 * ViewModel for managing music library and playback state
 * Implements MVVM architecture pattern
 */
class MusicViewModel(private val repository: MusicRepository) : ViewModel() {

    // Track list state
    private val _tracks = MutableLiveData<List<Track>>(emptyList())
    val tracks: LiveData<List<Track>> = _tracks

    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error state
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // Current playback state
    private val _playbackState = MutableLiveData(PlaybackState())
    val playbackState: LiveData<PlaybackState> = _playbackState

    // MediaStore diagnostic state
    private val _mediaScanReport = MutableLiveData<MediaScanReport>()
    val mediaScanReport: LiveData<MediaScanReport> = _mediaScanReport

    // Current track index in the list
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
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
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
                val service = MusicPlaybackService.getInstance()
                service?.let {
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
     * Load all tracks from device storage
     * Uses filesystem fallback to find files not indexed by MediaStore
     */
    fun loadTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Use filesystem fallback to ensure all files are found
                val trackList = repository.getAllTracksWithFilesystemFallback()
                _tracks.value = trackList
                
                // Also run diagnosis to show if there are differences
                runMediaDiagnosis()
            } catch (e: Exception) {
                _error.value = "Error loading tracks: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load tracks from a specific folder only
     * Uses direct filesystem scanning - does NOT use MediaStore
     * 
     * @param folderPath Absolute path to the folder to scan
     */
    fun loadTracksFromFolder(folderPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val trackList = repository.scanDirectoryForTracks(folderPath, recursive = true)
                _tracks.value = trackList
            } catch (e: Exception) {
                _error.value = "Error loading tracks: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Diagnose MediaStore vs filesystem
     */
    fun runMediaDiagnosis() {
        viewModelScope.launch {
            try {
                val report = repository.diagnoseMediaStore()
                _mediaScanReport.value = report
            } catch (e: Exception) {
                _error.value = "Media diagnosis failed: ${e.message}"
            }
        }
    }

    /**
     * Force rescan of files ignored by MediaStore
     */
    fun rescanMissingFiles(context: Context) {
        val missing = _mediaScanReport.value?.missingFiles ?: return
        if (missing.isEmpty()) return

        repository.forceRescan(
            context = context,
            paths = missing
        )
    }

    /**
     * Search tracks by query
     */
    fun searchTracks(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = repository.searchTracks(query)
                _tracks.value = results
            } catch (e: Exception) {
                _error.value = "Error searching: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Play a specific track
     */
    fun playTrack(track: Track) {
        val service = MusicPlaybackService.getInstance()
        service?.playTrack(track)

        currentTrackIndex = _tracks.value?.indexOf(track) ?: -1
    }

    /**
     * Play all tracks starting from the given index
     */
    fun playAllTracks(startIndex: Int = 0) {
        val trackList = _tracks.value ?: return
        val service = MusicPlaybackService.getInstance()
        service?.playTracks(trackList, startIndex)
        currentTrackIndex = startIndex
    }

    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        MusicPlaybackService.getInstance()?.togglePlayPause()
    }

    /**
     * Play next track
     */
    fun playNext() {
        MusicPlaybackService.getInstance()?.next()
        val trackList = _tracks.value ?: return
        if (currentTrackIndex < trackList.size - 1) {
            currentTrackIndex++
            updatePlaybackState { it.copy(currentTrack = trackList[currentTrackIndex]) }
        }
    }

    /**
     * Play previous track
     */
    fun playPrevious() {
        MusicPlaybackService.getInstance()?.previous()
        val trackList = _tracks.value ?: return
        if (currentTrackIndex > 0) {
            currentTrackIndex--
            updatePlaybackState { it.copy(currentTrack = trackList[currentTrackIndex]) }
        }
    }

    /**
     * Seek to position in current track
     */
    fun seekTo(position: Long) {
        MusicPlaybackService.getInstance()?.seekTo(position)
        updatePlaybackState { it.copy(currentPosition = position) }
    }

    /**
     * Clear any error messages
     */
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
    }
}
