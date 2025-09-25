package com.example.runnerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WorkoutRoutineDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: WorkoutRoutine): Long
    
    @Update
    suspend fun update(routine: WorkoutRoutine)
    
    @Query("SELECT * FROM workout_routines WHERE isPredefined = 1 ORDER BY type, difficulty")
    suspend fun getPredefinedRoutines(): List<WorkoutRoutine>
    
    @Query("SELECT * FROM workout_routines WHERE createdBy = :userEmail ORDER BY createdAt DESC")
    suspend fun getUserCustomRoutines(userEmail: String): List<WorkoutRoutine>
    
    @Query("SELECT * FROM workout_routines WHERE type = :type ORDER BY difficulty, name")
    suspend fun getRoutinesByType(type: String): List<WorkoutRoutine>
    
    @Query("SELECT * FROM workout_routines WHERE id = :id")
    suspend fun getRoutineById(id: Int): WorkoutRoutine?
    
    @Query("DELETE FROM workout_routines WHERE id = :id AND createdBy = :userEmail")
    suspend fun deleteCustomRoutine(id: Int, userEmail: String)
}
