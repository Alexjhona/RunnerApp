package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hydration_settings")
data class HydrationSettings(
    @PrimaryKey val userEmail: String,
    val dailyTargetMl: Int = 2000, // default 2 liters
    val reminderIntervalMinutes: Int = 60, // default 1 hour
    val reminderEnabled: Boolean = true,
    val reminderStartHour: Int = 8, // 8 AM
    val reminderEndHour: Int = 22 // 10 PM
)
