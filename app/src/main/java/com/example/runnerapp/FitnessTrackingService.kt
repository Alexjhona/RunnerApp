package com.example.runnerapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.sqrt

class FitnessTrackingService : Service(), SensorEventListener, LocationListener,
    BatteryOptimizationManager.BatteryOptimizationListener {

    companion object {
        const val CHANNEL_ID = "fitness_tracking"
        const val NOTIFICATION_ID = 2001
        
        const val ACTION_START_TRACKING = "com.example.runnerapp.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.runnerapp.STOP_TRACKING"
        const val ACTION_PAUSE_TRACKING = "com.example.runnerapp.PAUSE_TRACKING"
        const val ACTION_RESUME_TRACKING = "com.example.runnerapp.RESUME_TRACKING"
        
        const val BROADCAST_STEP_UPDATE = "com.example.runnerapp.STEP_UPDATE"
        const val BROADCAST_LOCATION_UPDATE = "com.example.runnerapp.LOCATION_UPDATE"
        const val BROADCAST_TRACKING_STATE = "com.example.runnerapp.TRACKING_STATE"
        
        const val EXTRA_STEPS = "steps"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_IS_TRACKING = "is_tracking"
        const val EXTRA_IS_PAUSED = "is_paused"
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): FitnessTrackingService = this@FitnessTrackingService
    }

    // Wake lock para mantener el servicio activo
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Sensores y managers
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var stepCounter: Sensor? = null
    private var stepDetector: Sensor? = null
    private var accelerometer: Sensor? = null
    
    // Estado del tracking
    private var isTracking = false
    private var isPaused = false
    private var baseSteps = -1
    private var currentSteps = 0
    private var lastLocation: Location? = null
    
    // Configuración de precisión
    private val maxAccuracyMeters = 30f
    private val maxSpeedMps = 8.0
    private val minDistanceMeters = 5f
    
    // Fallback acelerómetro para pasos
    private var lastAccelMagnitude = 0f
    private var highPassFilter = 0f
    private var lastStepTime = 0L
    private val stepDebounceMs = 350L
    private var dynamicThreshold = 1.2f

    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private var currentLocationInterval = 2000L
    private var currentSensorDelay = SensorManager.SENSOR_DELAY_UI

    private lateinit var stepCounterManager: StepCounterManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        stepCounterManager = StepCounterManager.getInstance(this)
        
        // Inicializar sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        batteryOptimizationManager = BatteryOptimizationManager(this)
        batteryOptimizationManager.setListener(this)
        batteryOptimizationManager.startMonitoring()
        
        // Wake lock para mantener el servicio activo incluso con pantalla apagada
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RunnerApp::FitnessTrackingWakeLock"
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_PAUSE_TRACKING -> pauseTracking()
            ACTION_RESUME_TRACKING -> resumeTracking()
        }
        return START_STICKY // Reiniciar automáticamente si el sistema mata el servicio
    }

    private fun startTracking() {
        if (isTracking) return
        
        isTracking = true
        isPaused = false
        
        val wakeLockTimeout = batteryOptimizationManager.getWakeLockTimeout()
        wakeLock?.acquire(wakeLockTimeout)
        
        // Iniciar como servicio foreground
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Inicializar contadores
        baseSteps = -1
        currentSteps = 0
        
        // Iniciar sensores de pasos
        startStepCounting()
        
        // Iniciar GPS
        startLocationTracking()
        
        // Broadcast estado
        broadcastTrackingState()
    }

    private fun stopTracking() {
        if (!isTracking) return
        
        isTracking = false
        isPaused = false
        
        // Detener sensores
        stopStepCounting()
        stopLocationTracking()
        
        // Liberar wake lock
        wakeLock?.let { if (it.isHeld) it.release() }
        
        // Detener servicio foreground
        stopForeground(true)
        stopSelf()
        
        // Broadcast estado
        broadcastTrackingState()
    }

    private fun pauseTracking() {
        if (!isTracking || isPaused) return
        
        isPaused = true
        
        // Pausar sensores pero mantener el servicio activo
        stopStepCounting()
        stopLocationTracking()
        
        // Actualizar notificación
        updateNotification()
        
        // Broadcast estado
        broadcastTrackingState()
    }

    private fun resumeTracking() {
        if (!isTracking || !isPaused) return
        
        isPaused = false
        
        // Reanudar sensores
        startStepCounting()
        startLocationTracking()
        
        // Actualizar notificación
        updateNotification()
        
        // Broadcast estado
        broadcastTrackingState()
    }

    private fun startStepCounting() {
        currentSensorDelay = when (batteryOptimizationManager.getCurrentOptimizationLevel()) {
            BatteryOptimizationManager.OptimizationLevel.CRITICAL -> SensorManager.SENSOR_DELAY_NORMAL
            BatteryOptimizationManager.OptimizationLevel.LOW -> SensorManager.SENSOR_DELAY_UI
            BatteryOptimizationManager.OptimizationLevel.NORMAL -> SensorManager.SENSOR_DELAY_UI
            BatteryOptimizationManager.OptimizationLevel.HIGH -> SensorManager.SENSOR_DELAY_GAME
        }
        
        when {
            stepCounter != null -> {
                sensorManager.registerListener(
                    this, stepCounter, currentSensorDelay
                )
            }
            stepDetector != null -> {
                sensorManager.registerListener(
                    this, stepDetector, currentSensorDelay
                )
            }
            accelerometer != null -> {
                sensorManager.registerListener(
                    this, accelerometer, currentSensorDelay
                )
            }
        }
    }

    private fun stopStepCounting() {
        sensorManager.unregisterListener(this)
    }

    private fun startLocationTracking() {
        if (!hasLocationPermission()) return
        
        currentLocationInterval = batteryOptimizationManager.getLocationUpdateInterval()
        val useGPS = batteryOptimizationManager.shouldUseGPS()
        val useNetwork = batteryOptimizationManager.shouldUseNetworkLocation()
        
        try {
            if (useGPS && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    currentLocationInterval,
                    0f,
                    this
                )
            }
            
            if (useNetwork && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    currentLocationInterval * 2, // Network updates less frequent
                    0f,
                    this
                )
            }
        } catch (e: SecurityException) {
            // Permisos no concedidos
        }
    }

    private fun stopLocationTracking() {
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            // Ignorar
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isTracking || isPaused) return
        
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()
                if (baseSteps == -1) {
                    baseSteps = totalSteps
                }
                currentSteps = (totalSteps - baseSteps).coerceAtLeast(0)
                
                stepCounterManager.updateTotalSteps(totalSteps)
                stepCounterManager.updateSessionSteps(currentSteps)
                
                broadcastStepUpdate()
                updateNotification()
            }
            
            Sensor.TYPE_STEP_DETECTOR -> {
                currentSteps++
                broadcastStepUpdate()
                updateNotification()
            }
            
            Sensor.TYPE_ACCELEROMETER -> {
                // Fallback usando acelerómetro
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val magnitude = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val alpha = 0.8f
                highPassFilter = alpha * (highPassFilter + magnitude - lastAccelMagnitude)
                lastAccelMagnitude = magnitude
                
                val now = System.currentTimeMillis()
                if (abs(highPassFilter) > dynamicThreshold && 
                    (now - lastStepTime) > stepDebounceMs) {
                    lastStepTime = now
                    currentSteps++
                    broadcastStepUpdate()
                    updateNotification()
                    
                    // Ajustar threshold dinámicamente
                    dynamicThreshold = (dynamicThreshold * 0.9f) + (abs(highPassFilter) * 0.1f)
                    dynamicThreshold = dynamicThreshold.coerceIn(1.1f, 2.0f)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere acción
    }

    override fun onLocationChanged(location: Location) {
        if (!isTracking || isPaused) return
        
        // Filtrar ubicaciones imprecisas
        if (location.hasAccuracy() && location.accuracy > maxAccuracyMeters) {
            return
        }
        
        // Filtrar ubicaciones muy cercanas o velocidades irreales
        lastLocation?.let { prev ->
            val distance = location.distanceTo(prev)
            if (distance < minDistanceMeters) return
            
            val timeDelta = (location.time - prev.time) / 1000.0
            if (timeDelta > 0) {
                val speed = distance / timeDelta
                if (speed > maxSpeedMps) return
            }
        }
        
        lastLocation = location
        broadcastLocationUpdate(location)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun broadcastStepUpdate() {
        val intent = Intent(BROADCAST_STEP_UPDATE).apply {
            putExtra(EXTRA_STEPS, currentSteps)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastLocationUpdate(location: Location) {
        val intent = Intent(BROADCAST_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastTrackingState() {
        val intent = Intent(BROADCAST_TRACKING_STATE).apply {
            putExtra(EXTRA_IS_TRACKING, isTracking)
            putExtra(EXTRA_IS_PAUSED, isPaused)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FitnessTrackingService::class.java).apply {
                action = if (isPaused) ACTION_RESUME_TRACKING else ACTION_PAUSE_TRACKING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, FitnessTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val batteryLevel = batteryOptimizationManager.getCurrentBatteryLevel()
        val contentText = when {
            isPaused -> "Pausado - $currentSteps pasos"
            batteryLevel <= BatteryOptimizationManager.BATTERY_CRITICAL -> 
                "Rastreando - $currentSteps pasos (Batería: $batteryLevel%)"
            else -> "Rastreando - $currentSteps pasos"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Fitness Tracking Activo")
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Reanudar" else "Pausar",
                pauseResumeIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Detener",
                stopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fitness Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones para el seguimiento de fitness en segundo plano"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBatteryLevelChanged(level: Int, isCharging: Boolean) {
        // Update notification with battery info if critical
        if (level <= BatteryOptimizationManager.BATTERY_CRITICAL) {
            updateNotification()
        }
    }

    override fun onPowerSaveModeChanged(isPowerSaveMode: Boolean) {
        if (isTracking && !isPaused) {
            // Restart location tracking with new settings
            stopLocationTracking()
            startLocationTracking()
        }
    }

    override fun onOptimizationLevelChanged(level: BatteryOptimizationManager.OptimizationLevel) {
        if (isTracking && !isPaused) {
            // Restart sensors and location with new optimization settings
            stopStepCounting()
            stopLocationTracking()
            
            startStepCounting()
            startLocationTracking()
            
            // Update notification
            updateNotification()
        }
    }

    override fun onDestroy() {
        batteryOptimizationManager.stopMonitoring()
        stopTracking()
        super.onDestroy()
    }

    // Métodos públicos para la interfaz
    fun getCurrentSteps(): Int = currentSteps
    fun isCurrentlyTracking(): Boolean = isTracking
    fun isCurrentlyPaused(): Boolean = isPaused
    fun getLastLocation(): Location? = lastLocation
}
