package com.example.runnerapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.*

class HydrationReminderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val CHANNEL_ID = "hydration_reminders"
        private const val NOTIFICATION_ID = 1001
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        // Only show notifications during active hours (8 AM to 10 PM by default)
        if (currentHour in 8..22) {
            showHydrationNotification(context)
        }
    }
    
    private fun showHydrationNotification(context: Context) {
        createNotificationChannel(context)
        
        val intent = Intent(context, HydrationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val messages = listOf(
            "💧 ¡Es hora de hidratarte!",
            "🚰 Recuerda beber agua",
            "💦 Tu cuerpo necesita hidratación",
            "🌊 Mantente hidratado para rendir mejor",
            "💧 Un vaso de agua te hará sentir mejor"
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("Recordatorio de Hidratación")
            .setContentText(messages.random())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_add,
                "Agregar 250ml",
                createQuickAddPendingIntent(context, 250)
            )
            .addAction(
                R.drawable.ic_add,
                "Agregar 500ml",
                createQuickAddPendingIntent(context, 500)
            )
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Handle notification permission not granted
        }
    }
    
    private fun createQuickAddPendingIntent(context: Context, amount: Int): PendingIntent {
        val intent = Intent(context, QuickAddWaterReceiver::class.java).apply {
            putExtra("water_amount", amount)
        }
        return PendingIntent.getBroadcast(
            context, 
            amount, // Use amount as request code to make it unique
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de Hidratación"
            val descriptionText = "Notificaciones para recordarte beber agua"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
