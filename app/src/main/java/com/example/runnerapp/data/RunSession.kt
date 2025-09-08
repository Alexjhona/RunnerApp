package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSec: Int,
    val steps: Int,
    val sport: String,            // RUN / BIKE / SKATE
    val distanceMeters: Double,
    val route: List<TrackPoint>,   // se guarda como JSON con Converters
    val caloriesBurned: Double = 0.0  // Added calories burned field
)
