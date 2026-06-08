package com.example.localmusicplayer.data.model

import android.net.Uri

/**
 * Data class representing a music track
 * Contains all metadata for a single audio file
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val uri: Uri,
    val path: String,
    val dateAdded: Long,
    val size: Long, // in bytes
    val albumArtPath: String? = null, // path to album art image
    val lyrics: String? = null, // embedded lyrics from metadata
    val genre: String? = null // music genre from metadata
) {
    /**
     * Returns formatted duration as MM:SS
     */
    fun getFormattedDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Returns formatted file size
     */
    fun getFormattedSize(): String {
        val kb = size / 1024
        val mb = kb / 1024
        return if (mb > 0) "${mb} MB" else "${kb} KB"
    }
}

