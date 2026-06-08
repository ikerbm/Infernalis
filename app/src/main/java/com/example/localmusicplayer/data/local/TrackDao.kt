package com.example.localmusicplayer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for track cache operations
 * Provides efficient queries for large libraries
 */
@Dao
interface TrackDao {

    /**
     * Get all cached tracks, sorted by title
     */
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    suspend fun getAllTracks(): List<TrackEntity>

    /**
     * Get all cached tracks as Flow for reactive updates
     */
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    /**
     * Get tracks from a specific folder
     */
    @Query("SELECT * FROM tracks WHERE path LIKE :folderPath || '%' ORDER BY title ASC")
    suspend fun getTracksInFolder(folderPath: String): List<TrackEntity>

    /**
     * Get a single track by path
     */
    @Query("SELECT * FROM tracks WHERE path = :path")
    suspend fun getTrackByPath(path: String): TrackEntity?

    /**
     * Check if a track exists in cache
     */
    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE path = :path)")
    suspend fun trackExists(path: String): Boolean

    /**
     * Get all cached file paths for comparison
     */
    @Query("SELECT path FROM tracks")
    suspend fun getAllPaths(): List<String>

    /**
     * Get paths in a specific folder
     */
    @Query("SELECT path FROM tracks WHERE path LIKE :folderPath || '%'")
    suspend fun getPathsInFolder(folderPath: String): List<String>

    /**
     * Insert or replace a single track
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    /**
     * Insert or replace multiple tracks (batch operation)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    /**
     * Delete a track by path
     */
    @Query("DELETE FROM tracks WHERE path = :path")
    suspend fun deleteTrack(path: String)

    /**
     * Delete tracks that no longer exist (cleanup)
     */
    @Query("DELETE FROM tracks WHERE path IN (:paths)")
    suspend fun deleteTracks(paths: List<String>)

    /**
     * Delete all tracks in a folder
     */
    @Query("DELETE FROM tracks WHERE path LIKE :folderPath || '%'")
    suspend fun deleteTracksInFolder(folderPath: String)

    /**
     * Clear entire cache
     */
    @Query("DELETE FROM tracks")
    suspend fun clearCache()

    /**
     * Get total track count
     */
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    /**
     * Get track count in a folder
     */
    @Query("SELECT COUNT(*) FROM tracks WHERE path LIKE :folderPath || '%'")
    suspend fun getTrackCountInFolder(folderPath: String): Int

    /**
     * Get distinct genres in a folder
     */
    @Query("SELECT DISTINCT genre FROM tracks WHERE path LIKE :folderPath || '%' AND genre IS NOT NULL ORDER BY genre ASC")
    suspend fun getDistinctGenresInFolder(folderPath: String): List<String>

    /**
     * Get tracks by genre in a folder
     */
    @Query("SELECT * FROM tracks WHERE path LIKE :folderPath || '%' AND genre = :genre ORDER BY title ASC")
    suspend fun getTracksByGenreInFolder(folderPath: String, genre: String): List<TrackEntity>

    /**
     * Get tracks with no genre in a folder
     */
    @Query("SELECT * FROM tracks WHERE path LIKE :folderPath || '%' AND genre IS NULL ORDER BY title ASC")
    suspend fun getTracksWithNoGenreInFolder(folderPath: String): List<TrackEntity>

    /**
     * Search tracks by title, artist, album, or genre
     */
    @Query("""
        SELECT * FROM tracks 
        WHERE path LIKE :folderPath || '%' 
        AND (
            title LIKE '%' || :query || '%' 
            OR artist LIKE '%' || :query || '%' 
            OR album LIKE '%' || :query || '%' 
            OR genre LIKE '%' || :query || '%'
        )
        ORDER BY title ASC
    """)
    suspend fun searchTracks(folderPath: String, query: String): List<TrackEntity>
}
