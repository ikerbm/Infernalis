package com.example.localmusicplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.localmusicplayer.data.local.TrackDao
import com.example.localmusicplayer.data.local.TrackEntity
import com.example.localmusicplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Repository that uses Room database for caching track metadata
 * Optimized for large libraries (5000+ tracks)
 * 
 * Strategy:
 * 1. Load existing cached tracks immediately
 * 2. Scan filesystem for new/modified files
 * 3. Update cache incrementally
 * 4. Extract and cache album art
 */
class CachedMusicRepository(
    private val trackDao: TrackDao,
    private val contentResolver: ContentResolver,
    private val context: Context
) {
    companion object {
        private val SUPPORTED_EXTENSIONS = listOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "wma")
        private const val ALBUM_ART_DIR = "album_art"
    }

    // Directory for caching album art
    private val albumArtCacheDir: File by lazy {
        File(context.cacheDir, ALBUM_ART_DIR).also { it.mkdirs() }
    }

    /**
     * Get all cached tracks from a folder (instant load)
     */
    suspend fun getCachedTracks(folderPath: String): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getTracksInFolder(folderPath).map { it.toTrack() }
    }

    /**
     * Get cached tracks as Flow for reactive updates
     */
    fun getCachedTracksFlow(): Flow<List<Track>> {
        return trackDao.getAllTracksFlow().map { entities ->
            entities.map { it.toTrack() }
        }
    }

    /**
     * Scan a folder and update cache with new/modified files
     * Returns the complete track list
     * 
     * @param folderPath Path to scan
     * @param onProgress Callback with (current, total) for progress updates
     */
    suspend fun scanAndCacheFolder(
        folderPath: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<Track> = withContext(Dispatchers.IO) {
        val directory = File(folderPath)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext getCachedTracks(folderPath)
        }

        // Get all audio files in the folder
        val audioFiles = directory.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            .toList()

        val totalFiles = audioFiles.size

        // Get already cached paths for this folder
        val cachedPaths = trackDao.getPathsInFolder(folderPath).toSet()

        // Find files that need to be scanned (new or modified)
        val filesToScan = mutableListOf<File>()
        val existingPaths = mutableSetOf<String>()

        audioFiles.forEachIndexed { index, file ->
            existingPaths.add(file.absolutePath)
            
            if (file.absolutePath !in cachedPaths) {
                // New file
                filesToScan.add(file)
            } else {
                // Check if file was modified (optional: can skip for faster load)
                val cached = trackDao.getTrackByPath(file.absolutePath)
                if (cached != null && cached.lastModified != file.lastModified()) {
                    filesToScan.add(file)
                }
            }
            
            // Report progress for existing check
            onProgress?.invoke(index + 1, totalFiles)
        }

        // Remove deleted files from cache
        val deletedPaths = cachedPaths.filter { it !in existingPaths }
        if (deletedPaths.isNotEmpty()) {
            trackDao.deleteTracks(deletedPaths)
            // Also delete cached album art
            deletedPaths.forEach { path ->
                getAlbumArtFile(path).delete()
            }
        }

        // Scan new/modified files and add to cache
        if (filesToScan.isNotEmpty()) {
            val newTracks = mutableListOf<TrackEntity>()
            var scanned = 0

            filesToScan.forEach { file ->
                try {
                    val entity = extractMetadataToEntity(file)
                    if (entity != null) {
                        newTracks.add(entity)
                    }
                } catch (e: Exception) {
                    // Skip problematic files
                }
                scanned++
                onProgress?.invoke(totalFiles - filesToScan.size + scanned, totalFiles)
            }

            // Batch insert for efficiency
            if (newTracks.isNotEmpty()) {
                trackDao.insertTracks(newTracks)
            }
        }

        // Return complete cached list
        getCachedTracks(folderPath)
    }

    /**
     * Force full rescan of a folder
     */
    suspend fun forceRescan(
        folderPath: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<Track> = withContext(Dispatchers.IO) {
        // Clear cache for this folder
        trackDao.deleteTracksInFolder(folderPath)
        
        // Full scan
        scanAndCacheFolder(folderPath, onProgress)
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(folderPath: String): CacheStats = withContext(Dispatchers.IO) {
        val count = trackDao.getTrackCountInFolder(folderPath)
        CacheStats(cachedTrackCount = count)
    }

    /**
     * Get album art file path for a track
     */
    private fun getAlbumArtFile(trackPath: String): File {
        val hash = trackPath.hashCode().toString()
        return File(albumArtCacheDir, "$hash.jpg")
    }

    /**
     * Extract and save album art from audio file
     * Returns the path to the saved image, or null if no art found
     */
    private fun extractAndSaveAlbumArt(file: File, retriever: MediaMetadataRetriever): String? {
        return try {
            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                val artFile = getAlbumArtFile(file.absolutePath)
                FileOutputStream(artFile).use { fos ->
                    fos.write(artBytes)
                }
                artFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract metadata from file and create entity
     */
    private fun extractMetadataToEntity(file: File): TrackEntity? {
        if (!file.exists() || !file.canRead()) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Artista desconocido"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Álbum desconocido"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)

            // Extract and save album art
            val albumArtPath = extractAndSaveAlbumArt(file, retriever)

            // Try to extract embedded lyrics (USLT frame in ID3)
            val lyrics = try {
                // MediaMetadataRetriever doesn't directly support lyrics
                // For now, we'll attempt to read from common metadata keys
                null 
            } catch (e: Exception) {
                null
            }

            retriever.release()

            TrackEntity(
                path = file.absolutePath,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                size = file.length(),
                lastModified = file.lastModified(),
                dateAdded = System.currentTimeMillis(),
                albumArtPath = albumArtPath,
                lyrics = lyrics,
                genre = genre
            )
        } catch (e: Exception) {
            try { retriever.release() } catch (_: Exception) {}
            // Fallback with filename
            TrackEntity(
                path = file.absolutePath,
                title = file.nameWithoutExtension,
                artist = "Artista desconocido",
                album = "Álbum desconocido",
                duration = 0L,
                size = file.length(),
                lastModified = file.lastModified(),
                dateAdded = System.currentTimeMillis(),
                albumArtPath = null,
                lyrics = null
            )
        }
    }

    /**
     * Convert entity to domain model
     */
    private fun TrackEntity.toTrack(): Track {
        return Track(
            id = path.hashCode().toLong(),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = Uri.fromFile(File(path)),
            path = path,
            dateAdded = dateAdded / 1000,
            size = size,
            albumArtPath = albumArtPath,
            lyrics = lyrics,
            genre = genre
        )
    }

    /**
     * Search tracks by query
     */
    suspend fun searchTracks(folderPath: String, query: String): List<Track> = withContext(Dispatchers.IO) {
        trackDao.searchTracks(folderPath, query).map { it.toTrack() }
    }

    /**
     * Get tracks grouped by genre for a folder
     */
    suspend fun getTracksGroupedByGenre(folderPath: String): Map<String, List<Track>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, List<Track>>()

        // Get named genres
        val genres = trackDao.getDistinctGenresInFolder(folderPath)
        for (genre in genres) {
            val tracks = trackDao.getTracksByGenreInFolder(folderPath, genre).map { it.toTrack() }
            if (tracks.isNotEmpty()) {
                result[genre] = tracks
            }
        }

        // Get tracks without genre
        val noGenreTracks = trackDao.getTracksWithNoGenreInFolder(folderPath).map { it.toTrack() }
        if (noGenreTracks.isNotEmpty()) {
            result["Género desconocido"] = noGenreTracks
        }

        result
    }
}

data class CacheStats(
    val cachedTrackCount: Int
)
