package com.example.runnerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RunSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: RunSession): Long

    @Query("SELECT * FROM runs ORDER BY id DESC")
    suspend fun getAll(): List<RunSession>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun getById(id: Int): RunSession?
}
