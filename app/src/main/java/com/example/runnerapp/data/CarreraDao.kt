package com.example.runnerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CarreraDao {

    @Insert
    suspend fun insertarCarrera(carrera: Carrera)

    @Query("SELECT * FROM carreras ORDER BY id DESC")
    suspend fun obtenerTodas(): List<Carrera>
}
