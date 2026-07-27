package com.example.localmusicplayer.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference entity for many-to-many relationship
 * between playlists and tracks
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackPath"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["path"],
            childColumns = ["trackPath"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackPath"])
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackPath: String,
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int = 0
)
