package com.example.runnerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.runnerapp.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class QuickAddWaterReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val amount = intent.getIntExtra("water_amount", 0)
        if (amount > 0) {
            addWaterFromNotification(context, amount)
            
            // Dismiss the notification
            NotificationManagerCompat.from(context).cancel(1001)
        }
    }
    
    private fun addWaterFromNotification(context: Context, amount: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val sessionManager = SessionManager(context)
                val userEmail = sessionManager.getUserSession()
                
                if (userEmail != null) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    database.hydrationDao().addWaterIntake(userEmail, today, amount)
                    
                    // Show a success notification
                    showSuccessNotification(context, amount)
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }
    
    private fun showSuccessNotification(context: Context, amount: Int) {
        // This would show a brief success notification
        // Implementation depends on your notification setup
    }
}
