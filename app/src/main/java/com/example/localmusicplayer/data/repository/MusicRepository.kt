package com.example.localmusicplayer.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import com.example.localmusicplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.os.Environment
import java.io.File
import com.example.localmusicplayer.data.model.MediaScanReport


import android.media.MediaScannerConnection
import android.content.Context

/**
 * Repository for accessing music files from device storage
 * Uses MediaStore API for efficient scanning of large libraries
 */
class MusicRepository(private val contentResolver: ContentResolver) {

    private fun enrichWithMetadataFallback(track: Track): Track {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(track.path)

            val title = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_TITLE
            )
            val artist = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ARTIST
            )
            val album = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ALBUM
            )

            retriever.release()

            track.copy(
                title = title ?: track.title,
                artist = artist ?: track.artist,
                album = album ?: track.album
            )
        } catch (e: Exception) {
            track
        }
    }

    /**
     * Scans device storage for audio files
     * Optimized for large libraries (5000+ tracks)
     */
    suspend fun getAllTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.DURATION} > 0"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)

                var track = Track(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Unknown",
                    artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                    album = cursor.getString(albumColumn) ?: "Unknown Album",
                    duration = cursor.getLong(durationColumn),
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ),
                    path = cursor.getString(pathColumn) ?: "",
                    dateAdded = cursor.getLong(dateAddedColumn),
                    size = cursor.getLong(sizeColumn)
                )
                if (
                    track.title == "Unknown" ||
                    track.artist == "Unknown Artist" ||
                    track.album == "Unknown Album"
                ) {
                    track = enrichWithMetadataFallback(track)
                }

                tracks.add(track)
            }
        }

        tracks
    }

    /**
     * Search tracks by title, artist, or album
     */
    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        getAllTracks().filter { track ->
            track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true)
        }
    }

    /**
     * Get tracks by specific artist
     */
    suspend fun getTracksByArtist(artist: String): List<Track> = withContext(Dispatchers.IO) {
        getAllTracks().filter { it.artist.equals(artist, ignoreCase = true) }
    }

    /**
     * Get tracks by specific album
     */
    suspend fun getTracksByAlbum(album: String): List<Track> = withContext(Dispatchers.IO) {
        getAllTracks().filter { it.album.equals(album, ignoreCase = true) }
    }

    /**
     * Scans a specific directory for audio files directly from filesystem
     * Does NOT rely on MediaStore indexation - finds ALL audio files
     * 
     * @param directoryPath Path to the directory to scan (e.g., "/storage/emulated/0/Music")
     * @param recursive If true, scans subdirectories as well
     */
    suspend fun scanDirectoryForTracks(
        directoryPath: String,
        recursive: Boolean = true
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val supportedExtensions = listOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "wma")
        
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        val files = if (recursive) {
            directory.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
                .toList()
        } else {
            directory.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in supportedExtensions }
                ?: emptyList()
        }

        var idCounter = System.currentTimeMillis()
        
        files.forEach { file ->
            try {
                val track = extractTrackFromFile(file, idCounter++)
                if (track != null) {
                    tracks.add(track)
                }
            } catch (e: Exception) {
                // Skip files that can't be read
            }
        }

        tracks.sortedBy { it.title.lowercase() }
    }

    /**
     * Extracts track metadata directly from an audio file using MediaMetadataRetriever
     */
    private fun extractTrackFromFile(file: File, id: Long): Track? {
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

            retriever.release()

            Track(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                uri = Uri.fromFile(file),
                path = file.absolutePath,
                dateAdded = file.lastModified() / 1000,
                size = file.length()
            )
        } catch (e: Exception) {
            try { retriever.release() } catch (_: Exception) {}
            // Fallback: create track with minimal info from filename
            Track(
                id = id,
                title = file.nameWithoutExtension,
                artist = "Artista desconocido",
                album = "Álbum desconocido",
                duration = 0L,
                uri = Uri.fromFile(file),
                path = file.absolutePath,
                dateAdded = file.lastModified() / 1000,
                size = file.length()
            )
        }
    }

    /**
     * Gets all tracks using both MediaStore AND direct filesystem scan
     * Combines results and removes duplicates based on file path
     * This ensures no files are missed regardless of indexation status
     */
    suspend fun getAllTracksWithFilesystemFallback(
        additionalDirectories: List<String> = emptyList()
    ): List<Track> = withContext(Dispatchers.IO) {
        val allTracks = mutableMapOf<String, Track>()
        
        // 1. First get tracks from MediaStore
        try {
            getAllTracks().forEach { track ->
                allTracks[track.path] = track
            }
        } catch (e: Exception) {
            // MediaStore failed, continue with filesystem scan
        }

        // 2. Scan standard music directories
        val standardDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            "${Environment.getExternalStorageDirectory().absolutePath}/Music",
            "${Environment.getExternalStorageDirectory().absolutePath}/Download"
        )
        
        // 3. Combine with additional user-specified directories
        val allDirs = (standardDirs + additionalDirectories).distinct()
        
        allDirs.forEach { dirPath ->
            try {
                scanDirectoryForTracks(dirPath, recursive = true).forEach { track ->
                    // Only add if not already present (MediaStore version takes priority)
                    if (!allTracks.containsKey(track.path)) {
                        allTracks[track.path] = track
                    }
                }
            } catch (e: Exception) {
                // Skip directories that can't be scanned
            }
        }

        allTracks.values.toList().sortedBy { it.title.lowercase() }
    }

    /**
     * Scans all common storage locations for audio files
     * Includes internal storage, external SD cards, and common music folders
     */
    suspend fun scanAllStorageLocations(): List<Track> = withContext(Dispatchers.IO) {
        val allPaths = mutableListOf<String>()
        
        // Standard locations
        allPaths.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)
        allPaths.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        
        // Root of external storage
        val externalRoot = Environment.getExternalStorageDirectory()
        if (externalRoot.exists()) {
            allPaths.add(externalRoot.absolutePath)
        }
        
        getAllTracksWithFilesystemFallback(allPaths.distinct())
    }

    suspend fun diagnoseMediaStore(): MediaScanReport = withContext(Dispatchers.IO) {


        // 1️⃣ Archivos indexados por MediaStore
        val indexedPaths = mutableSetOf<String>()

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                indexedPaths.add(cursor.getString(pathColumn))
            }
        }

        // 2️⃣ Archivos reales en /Music
        val musicDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

        val realFiles = musicDir
            .walkTopDown()
            .filter {
                it.isFile && it.extension.lowercase() in listOf(
                    "mp3", "flac", "wav", "m4a", "ogg"
                )
            }
            .map { it.absolutePath }
            .toList()

        // 3️⃣ Diferencia
        val missing = realFiles.filterNot { indexedPaths.contains(it) }

        MediaScanReport(
            indexedCount = indexedPaths.size,
            filesystemCount = realFiles.size,
            missingFiles = missing
        )
    }

    fun forceRescan(
        context: Context,
        paths: List<String>,
        onFinished: (() -> Unit)? = null
    ) {
        MediaScannerConnection.scanFile(
            context,
            paths.toTypedArray(),
            null
        ) { _, _ ->
            onFinished?.invoke()
        }
    }


}




