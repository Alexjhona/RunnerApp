package com.example.runnerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log

class BatteryOptimizationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryOptimization"
        
        // Niveles de batería para diferentes modos
        const val BATTERY_CRITICAL = 15
        const val BATTERY_LOW = 30
        const val BATTERY_NORMAL = 50
        
        // Intervalos de actualización en milisegundos
        const val INTERVAL_CRITICAL = 10000L  // 10 segundos
        const val INTERVAL_LOW = 5000L        // 5 segundos
        const val INTERVAL_NORMAL = 2000L     // 2 segundos
        const val INTERVAL_HIGH = 1000L       // 1 segundo
        
        // Intervalos GPS
        const val GPS_INTERVAL_CRITICAL = 30000L  // 30 segundos
        const val GPS_INTERVAL_LOW = 15000L       // 15 segundos
        const val GPS_INTERVAL_NORMAL = 5000L     // 5 segundos
        const val GPS_INTERVAL_HIGH = 2000L       // 2 segundos
    }
    
    interface BatteryOptimizationListener {
        fun onBatteryLevelChanged(level: Int, isCharging: Boolean)
        fun onPowerSaveModeChanged(isPowerSaveMode: Boolean)
        fun onOptimizationLevelChanged(level: OptimizationLevel)
    }
    
    enum class OptimizationLevel {
        CRITICAL,   // Batería crítica < 15%
        LOW,        // Batería baja < 30%
        NORMAL,     // Batería normal < 50%
        HIGH        // Batería alta >= 50%
    }
    
    private var listener: BatteryOptimizationListener? = null
    private var currentBatteryLevel = 100
    private var isCharging = false
    private var isPowerSaveMode = false
    private var currentOptimizationLevel = OptimizationLevel.HIGH
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    
                    if (level >= 0 && scale > 0) {
                        currentBatteryLevel = (level * 100) / scale
                        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL
                        
                        updateOptimizationLevel()
                        listener?.onBatteryLevelChanged(currentBatteryLevel, isCharging)
                        
                        Log.d(TAG, "Battery: $currentBatteryLevel%, Charging: $isCharging")
                    }
                }
                
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val powerManager = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    isPowerSaveMode = powerManager?.isPowerSaveMode ?: false
                    
                    updateOptimizationLevel()
                    listener?.onPowerSaveModeChanged(isPowerSaveMode)
                    
                    Log.d(TAG, "Power save mode: $isPowerSaveMode")
                }
            }
        }
    }
    
    fun setListener(listener: BatteryOptimizationListener) {
        this.listener = listener
    }
    
    fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        
        context.registerReceiver(batteryReceiver, filter)
        
        // Obtener estado inicial
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let { batteryReceiver.onReceive(context, it) }
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isPowerSaveMode = powerManager.isPowerSaveMode
        
        updateOptimizationLevel()
        
        Log.d(TAG, "Battery monitoring started")
    }
    
    fun stopMonitoring() {
        try {
            context.unregisterReceiver(batteryReceiver)
            Log.d(TAG, "Battery monitoring stopped")
        } catch (e: IllegalArgumentException) {
            // Receiver ya no estaba registrado
        }
    }
    
    private fun updateOptimizationLevel() {
        val newLevel = when {
            isPowerSaveMode || currentBatteryLevel <= BATTERY_CRITICAL -> OptimizationLevel.CRITICAL
            currentBatteryLevel <= BATTERY_LOW -> OptimizationLevel.LOW
            currentBatteryLevel <= BATTERY_NORMAL -> OptimizationLevel.NORMAL
            else -> OptimizationLevel.HIGH
        }
        
        if (newLevel != currentOptimizationLevel) {
            currentOptimizationLevel = newLevel
            listener?.onOptimizationLevelChanged(newLevel)
            Log.d(TAG, "Optimization level changed to: $newLevel")
        }
    }
    
    fun getLocationUpdateInterval(): Long {
        return when (currentOptimizationLevel) {
            OptimizationLevel.CRITICAL -> GPS_INTERVAL_CRITICAL
            OptimizationLevel.LOW -> GPS_INTERVAL_LOW
            OptimizationLevel.NORMAL -> GPS_INTERVAL_NORMAL
            OptimizationLevel.HIGH -> GPS_INTERVAL_HIGH
        }
    }
    
    fun getSensorUpdateInterval(): Long {
        return when (currentOptimizationLevel) {
            OptimizationLevel.CRITICAL -> INTERVAL_CRITICAL
            OptimizationLevel.LOW -> INTERVAL_LOW
            OptimizationLevel.NORMAL -> INTERVAL_NORMAL
            OptimizationLevel.HIGH -> INTERVAL_HIGH
        }
    }
    
    fun shouldUseGPS(): Boolean {
        return when (currentOptimizationLevel) {
            OptimizationLevel.CRITICAL -> false  // Solo usar GPS si es absolutamente necesario
            OptimizationLevel.LOW -> !isPowerSaveMode
            OptimizationLevel.NORMAL -> true
            OptimizationLevel.HIGH -> true
        }
    }
    
    fun shouldUseNetworkLocation(): Boolean {
        return when (currentOptimizationLevel) {
            OptimizationLevel.CRITICAL -> true   // Usar ubicación de red como fallback
            OptimizationLevel.LOW -> true
            OptimizationLevel.NORMAL -> true
            OptimizationLevel.HIGH -> true
        }
    }
    
    fun getWakeLockTimeout(): Long {
        return when (currentOptimizationLevel) {
            OptimizationLevel.CRITICAL -> 2 * 60 * 1000L   // 2 minutos
            OptimizationLevel.LOW -> 5 * 60 * 1000L        // 5 minutos
            OptimizationLevel.NORMAL -> 10 * 60 * 1000L    // 10 minutos
            OptimizationLevel.HIGH -> 15 * 60 * 1000L      // 15 minutos
        }
    }
    
    fun getCurrentBatteryLevel(): Int = currentBatteryLevel
    fun isDeviceCharging(): Boolean = isCharging
    fun isPowerSaveModeEnabled(): Boolean = isPowerSaveMode
    fun getCurrentOptimizationLevel(): OptimizationLevel = currentOptimizationLevel
}
