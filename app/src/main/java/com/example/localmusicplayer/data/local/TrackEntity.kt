package com.example.localmusicplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching track metadata
 * Avoids re-scanning files on every app launch
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val path: String,           // File path as unique identifier
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,         // in milliseconds
    val size: Long,             // in bytes
    val lastModified: Long,     // for detecting file changes
    val dateAdded: Long,        // timestamp when added to cache
    val albumArtPath: String? = null,  // path to cached album art image
    val lyrics: String? = null,  // embedded lyrics from metadata
    val genre: String? = null    // music genre from metadata
)
