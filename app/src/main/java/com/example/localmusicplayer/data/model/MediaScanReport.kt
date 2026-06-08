package com.example.localmusicplayer.data.model

data class MediaScanReport(
    val indexedCount: Int,
    val filesystemCount: Int,
    val missingFiles: List<String>
)
