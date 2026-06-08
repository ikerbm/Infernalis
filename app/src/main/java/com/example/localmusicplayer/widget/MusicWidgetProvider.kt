package com.example.localmusicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.example.localmusicplayer.R
import com.example.localmusicplayer.service.MusicPlaybackService
import com.example.localmusicplayer.ui.NowPlayingActivity
import java.io.File

/**
 * AppWidgetProvider for home screen controls
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Normally updates on a schedule, but we'll push updates from the service
        // However, we still need to handle an initial update or manual refresh
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Handle widget-specific broadcast if needed
        if (intent.action == ACTION_WIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        const val ACTION_WIDGET_UPDATE = "com.example.localmusicplayer.ACTION_WIDGET_UPDATE"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            
            val service = MusicPlaybackService.getInstance()
            val currentTrack = MusicPlaybackService.currentTrack.value
            val isPlaying = MusicPlaybackService.isPlaying.value

            if (currentTrack != null) {
                views.setTextViewText(R.id.widgetTitle, currentTrack.title)
                views.setTextViewText(R.id.widgetArtist, currentTrack.artist)
                
                // Set play/pause icon
                views.setImageViewResource(
                    R.id.widgetPlayPause,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )

                // Load album art if available
                if (currentTrack.albumArtPath != null) {
                    try {
                        val file = File(currentTrack.albumArtPath)
                        if (file.exists()) {
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeFile(file.absolutePath, options)
                            
                            var scale = 1
                            while (options.outWidth / scale / 2 >= 300 && options.outHeight / scale / 2 >= 300) {
                                scale *= 2
                            }
                            
                            val finalOptions = BitmapFactory.Options().apply {
                                inSampleSize = scale
                            }
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath, finalOptions)
                            
                            if (bitmap != null) {
                                views.setImageViewBitmap(R.id.widgetThumbnail, bitmap)
                                views.setImageViewBitmap(R.id.widgetBackgroundArt, bitmap)
                                views.setInt(R.id.widgetMainContainer, "setBackgroundResource", R.drawable.bg_widget_dark)
                            } else {
                                views.setImageViewResource(R.id.widgetThumbnail, R.drawable.ic_music_placeholder)
                                views.setImageViewResource(R.id.widgetBackgroundArt, android.R.color.transparent)
                                views.setInt(R.id.widgetMainContainer, "setBackgroundResource", R.drawable.bg_widget)
                            }
                        } else {
                            views.setImageViewResource(R.id.widgetThumbnail, R.drawable.ic_music_placeholder)
                            views.setImageViewResource(R.id.widgetBackgroundArt, android.R.color.transparent)
                            views.setInt(R.id.widgetMainContainer, "setBackgroundResource", R.drawable.bg_widget)
                        }
                    } catch (e: Exception) {
                        views.setImageViewResource(R.id.widgetThumbnail, R.drawable.ic_music_placeholder)
                        views.setImageViewResource(R.id.widgetBackgroundArt, android.R.color.transparent)
                        views.setInt(R.id.widgetMainContainer, "setBackgroundResource", R.drawable.bg_widget)
                    }
                } else {
                    views.setImageViewResource(R.id.widgetThumbnail, R.drawable.ic_music_placeholder)
                    views.setImageViewResource(R.id.widgetBackgroundArt, android.R.color.transparent)
                    views.setInt(R.id.widgetMainContainer, "setBackgroundResource", R.drawable.bg_widget)
                }
            }

            // Setup click on widget body to open NowPlayingActivity
            val openAppIntent = Intent(context, NowPlayingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetMainContainer, openAppPendingIntent)

            // Setup button intents
            views.setOnClickPendingIntent(R.id.widgetPlayPause, getServicePendingIntent(context, MusicPlaybackService.ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widgetNext, getServicePendingIntent(context, MusicPlaybackService.ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widgetPrev, getServicePendingIntent(context, MusicPlaybackService.ACTION_PREVIOUS))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getServicePendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
