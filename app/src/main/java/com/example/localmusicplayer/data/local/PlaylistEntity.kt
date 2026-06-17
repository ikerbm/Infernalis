package com.example.localmusicplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for user-created playlists
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
