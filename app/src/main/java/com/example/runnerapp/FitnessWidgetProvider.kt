package com.example.runnerapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.util.Locale

class FitnessWidgetProvider : AppWidgetProvider(), SensorEventListener {

    companion object {
        private const val ACTION_PLAY_MUSIC = "com.example.runnerapp.widget.PLAY_MUSIC"
        private const val ACTION_UPDATE_STEPS = "com.example.runnerapp.widget.UPDATE_STEPS"
        private const val PREFS_NAME = "fitness_widget_prefs"
        private const val KEY_IS_PLAYING = "widget_is_playing"
    }

    private var sensorManager: SensorManager? = null
    private var stepCounter: Sensor? = null
    private var isListening = false
    private var context: Context? = null
    
    private var stepCounterManager: StepCounterManager? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        stepCounterManager = StepCounterManager.getInstance(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_PLAY_MUSIC -> {
                handlePlayMusic(context)
            }
            ACTION_UPDATE_STEPS -> {
                updateAllWidgets(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        this.context = context
        stepCounterManager = StepCounterManager.getInstance(context)
        startStepCounting(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopStepCounting()
        this.context = null
    }

    private fun handlePlayMusic(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        
        if (!isPlaying) {
            startMusicAndStepCounting(context)
            prefs.edit().putBoolean(KEY_IS_PLAYING, true).apply()
        } else {
            pauseMusicAndStepCounting(context)
            prefs.edit().putBoolean(KEY_IS_PLAYING, false).apply()
        }
        
        updateAllWidgets(context)
    }

    private fun startMusicAndStepCounting(context: Context) {
        val fitnessIntent = Intent(context, FitnessTrackingService::class.java).apply {
            action = FitnessTrackingService.ACTION_START_TRACKING
        }
        ContextCompat.startForegroundService(context, fitnessIntent)
        
        val musicIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_TOGGLE
        }
        ContextCompat.startForegroundService(context, musicIntent)
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_PLAYING, true).apply()
    }

    private fun pauseMusicAndStepCounting(context: Context) {
        val fitnessIntent = Intent(context, FitnessTrackingService::class.java).apply {
            action = FitnessTrackingService.ACTION_PAUSE_TRACKING
        }
        context.startService(fitnessIntent)
        
        val musicIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_TOGGLE
        }
        context.startService(musicIntent)
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_PLAYING, false).apply()
    }

    private fun startStepCounting(context: Context) {
        if (isListening) return
        
        this.context = context
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        stepCounter?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            isListening = true
        }
    }

    private fun stopStepCounting() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()
            
            stepCounterManager?.updateTotalSteps(totalSteps)
            
            context?.let { ctx ->
                updateAllWidgets(ctx)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, FitnessWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        
        val steps = stepCounterManager?.getTotalSteps() ?: 0

        val views = RemoteViews(context.packageName, R.layout.widget_fitness)
        
        views.setTextViewText(R.id.tvWidgetSteps, String.format(Locale.getDefault(), "%d", steps))
        
        if (isPlaying) {
            views.setImageViewResource(R.id.btnWidgetPlay, android.R.drawable.ic_media_pause)
            views.setTextViewText(R.id.tvWidgetStatus, "Activo")
        } else {
            views.setImageViewResource(R.id.btnWidgetPlay, android.R.drawable.ic_media_play)
            views.setTextViewText(R.id.tvWidgetStatus, "Pausado")
        }

        val playIntent = Intent(context, FitnessWidgetProvider::class.java).apply {
            action = ACTION_PLAY_MUSIC
        }
        val playPendingIntent = PendingIntent.getBroadcast(
            context, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnWidgetPlay, playPendingIntent)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetContainer, openAppPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
