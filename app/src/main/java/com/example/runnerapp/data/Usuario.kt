package com.example.runnerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey val email: String,
    val password: String,
    val apodo: String? = null
)