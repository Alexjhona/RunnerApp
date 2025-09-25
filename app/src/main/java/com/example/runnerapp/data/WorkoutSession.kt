package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routineId: Int,
    val routineName: String,
    val userEmail: String,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val completedExercises: List<CompletedExercise>,
    val totalCaloriesBurned: Double,
    val notes: String = ""
)

data class CompletedExercise(
    val exerciseName: String,
    val setsCompleted: Int,
    val repsCompleted: Int,
    val weightUsed: Float,
    val distanceCompleted: Float,
    val timeCompleted: Int, // in seconds
    val caloriesBurned: Double
)
