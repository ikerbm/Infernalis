package com.example.localmusicplayer.data.local

import androidx.room.*

/**
 * Data Access Object for playlist operations
 */
@Dao
interface PlaylistDao {

    // ── Playlist CRUD ──

    /**
     * Create a new playlist, returns the generated ID
     */
    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    /**
     * Delete a playlist (cascade deletes its track associations)
     */
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    /**
     * Rename a playlist
     */
    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    /**
     * Get all user-created playlists, ordered by creation date (newest first)
     */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    /**
     * Get a playlist by ID
     */
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    // ── Track association ──

    /**
     * Add a track to a playlist
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    /**
     * Remove a track from a playlist
     */
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackPath = :trackPath")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackPath: String)

    /**
     * Get all tracks in a playlist, ordered by position then addedAt
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.path = pt.trackPath
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC, pt.addedAt ASC
    """)
    suspend fun getTracksInPlaylist(playlistId: Long): List<TrackEntity>

    /**
     * Get track count in a playlist
     */
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCountInPlaylist(playlistId: Long): Int

    /**
     * Check if a track is already in a playlist
     */
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND trackPath = :trackPath)")
    suspend fun isTrackInPlaylist(playlistId: Long, trackPath: String): Boolean

    /**
     * Get the next position value for a playlist
     */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    // ── Default "Recientes" playlist ──

    /**
     * Get the last 200 tracks added to the database, ordered by dateAdded descending.
     * Used for the built-in "Recientes" playlist.
     */
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT 200")
    suspend fun getRecentTracks(): List<TrackEntity>
}
