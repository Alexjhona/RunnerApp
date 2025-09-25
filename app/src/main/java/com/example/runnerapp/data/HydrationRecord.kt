package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hydration_records")
data class HydrationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val date: String, // YYYY-MM-DD format
    val waterIntakeMl: Int,
    val targetIntakeMl: Int,
    val recordedAt: Long = System.currentTimeMillis()
)
