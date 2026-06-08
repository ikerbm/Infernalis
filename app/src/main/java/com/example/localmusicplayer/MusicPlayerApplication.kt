package com.example.localmusicplayer

import android.app.Application

/**
 * Application class for LocalMusicPlayer
 * Initializes app-wide dependencies and configurations
 */
class MusicPlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MusicPlayerApplication
            private set
    }
}
