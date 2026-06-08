package com.example.localmusicplayer.service

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the sleep timer functionality.
 * Handles countdown and pauses playback when the timer expires.
 */
object SleepTimerManager {

    private var countDownTimer: CountDownTimer? = null

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * Start a sleep timer for the given number of minutes.
     * Cancels any existing timer first.
     */
    fun start(minutes: Int) {
        cancel()

        val totalMillis = minutes * 60 * 1000L

        countDownTimer = object : CountDownTimer(totalMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingMillis.value = millisUntilFinished
            }

            override fun onFinish() {
                _remainingMillis.value = 0L
                _isActive.value = false
                // Pause playback when timer expires
                MusicPlaybackService.getInstance()?.pause()
            }
        }

        _isActive.value = true
        _remainingMillis.value = totalMillis
        countDownTimer?.start()
    }

    /**
     * Cancel the active sleep timer.
     */
    fun cancel() {
        countDownTimer?.cancel()
        countDownTimer = null
        _remainingMillis.value = 0L
        _isActive.value = false
    }

    /**
     * Format remaining time as "MM:SS" or "H:MM:SS" for display.
     */
    fun getRemainingFormatted(): String {
        val millis = _remainingMillis.value
        if (millis <= 0) return ""

        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
