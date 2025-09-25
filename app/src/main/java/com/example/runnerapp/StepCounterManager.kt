package com.example.runnerapp

import android.content.Context
import android.content.SharedPreferences
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.Intent

/**
 * Administrador centralizado para sincronizar el contador de pasos
 * entre el widget, dashboard y servicio de fitness
 */
class StepCounterManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "step_counter_shared"
        private const val KEY_TOTAL_STEPS = "total_steps"
        private const val KEY_BASE_STEPS = "base_steps"
        private const val KEY_SESSION_STEPS = "session_steps"
        private const val KEY_LAST_UPDATE = "last_update"
        
        const val BROADCAST_STEP_SYNC = "com.example.runnerapp.STEP_SYNC"
        const val EXTRA_TOTAL_STEPS = "total_steps"
        const val EXTRA_SESSION_STEPS = "session_steps"
        
        @Volatile
        private var INSTANCE: StepCounterManager? = null
        
        fun getInstance(context: Context): StepCounterManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StepCounterManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Actualiza el contador total de pasos desde el sensor
     */
    fun updateTotalSteps(sensorSteps: Int) {
        val baseSteps = prefs.getInt(KEY_BASE_STEPS, -1)
        val currentBase = if (baseSteps == -1) {
            prefs.edit().putInt(KEY_BASE_STEPS, sensorSteps).apply()
            sensorSteps
        } else {
            baseSteps
        }
        
        val totalSteps = (sensorSteps - currentBase).coerceAtLeast(0)
        
        prefs.edit()
            .putInt(KEY_TOTAL_STEPS, totalSteps)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
        
        broadcastStepUpdate(totalSteps, getSessionSteps())
    }
    
    /**
     * Actualiza los pasos de la sesión actual (para entrenamientos)
     */
    fun updateSessionSteps(sessionSteps: Int) {
        prefs.edit()
            .putInt(KEY_SESSION_STEPS, sessionSteps)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
        
        broadcastStepUpdate(getTotalSteps(), sessionSteps)
    }
    
    /**
     * Reinicia el contador de sesión
     */
    fun resetSessionSteps() {
        prefs.edit()
            .putInt(KEY_SESSION_STEPS, 0)
            .apply()
        
        broadcastStepUpdate(getTotalSteps(), 0)
    }
    
    /**
     * Obtiene el total de pasos acumulados
     */
    fun getTotalSteps(): Int = prefs.getInt(KEY_TOTAL_STEPS, 0)
    
    /**
     * Obtiene los pasos de la sesión actual
     */
    fun getSessionSteps(): Int = prefs.getInt(KEY_SESSION_STEPS, 0)
    
    /**
     * Obtiene la última actualización
     */
    fun getLastUpdate(): Long = prefs.getLong(KEY_LAST_UPDATE, 0)
    
    /**
     * Sincroniza todos los componentes con los valores actuales
     */
    fun syncAllComponents() {
        broadcastStepUpdate(getTotalSteps(), getSessionSteps())
    }
    
    private fun broadcastStepUpdate(totalSteps: Int, sessionSteps: Int) {
        val intent = Intent(BROADCAST_STEP_SYNC).apply {
            putExtra(EXTRA_TOTAL_STEPS, totalSteps)
            putExtra(EXTRA_SESSION_STEPS, sessionSteps)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}
