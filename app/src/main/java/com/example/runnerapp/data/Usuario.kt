package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey val email: String,
    val password: String,
    val apodo: String? = null,
    val nombre: String? = null,
    val edad: Int? = null,
    val genero: String? = null, // "Masculino", "Femenino", "Otro"
    val estatura: Float? = null, // in cm
    val peso: Float? = null, // in kg
    val profileCompleted: Boolean = false
)
