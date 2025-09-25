package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "carreras")
data class Carrera(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tiempoSegundos: Int,
    val pasos: Int
)
