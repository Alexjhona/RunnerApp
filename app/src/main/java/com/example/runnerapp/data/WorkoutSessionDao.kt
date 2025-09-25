package com.example.runnerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkoutSessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSession): Long
    
    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    suspend fun getAll(): List<WorkoutSession>
    
    @Query("SELECT * FROM workout_sessions WHERE userEmail = :userEmail ORDER BY startTime DESC")
    suspend fun getUserWorkoutSessions(userEmail: String): List<WorkoutSession>
    
    @Query("SELECT * FROM workout_sessions WHERE userEmail = :userEmail AND startTime >= :startTime ORDER BY startTime DESC")
    suspend fun getUserWorkoutSessionsAfter(userEmail: String, startTime: Long): List<WorkoutSession>
    
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): WorkoutSession?
    
    @Query("SELECT COUNT(*) FROM workout_sessions WHERE userEmail = :userEmail AND routineId = :routineId")
    suspend fun getRoutineCompletionCount(userEmail: String, routineId: Int): Int
}
