package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_routines")
data class WorkoutRoutine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val type: String, // "STRENGTH", "CARDIO", "HIIT", "CUSTOM"
    val difficulty: String, // "BEGINNER", "INTERMEDIATE", "ADVANCED"
    val durationMinutes: Int,
    val exercises: List<Exercise>, // JSON list of exercises
    val isPredefined: Boolean = false,
    val createdBy: String? = null, // user email for custom routines
    val createdAt: Long = System.currentTimeMillis()
)

data class Exercise(
    val name: String,
    val description: String,
    val sets: Int = 1,
    val reps: Int = 0,
    val durationSeconds: Int = 0,
    val restSeconds: Int = 30,
    val weight: Float = 0f, // for strength exercises
    val distance: Float = 0f, // for cardio exercises
    val speed: Float = 0f, // for cardio exercises
    val exerciseType: String // "STRENGTH", "CARDIO", "HIIT", "FLEXIBILITY"
)
