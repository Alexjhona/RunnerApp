package com.example.runnerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UsuarioDao {
    @Insert
    suspend fun insert(usuario: Usuario)

    @Query("SELECT * FROM usuarios WHERE email = :email")
    suspend fun getUsuarioByEmail(email: String): Usuario?

    @Query("SELECT * FROM usuarios")
    suspend fun getAllUsuarios(): List<Usuario>

    @Query("UPDATE usuarios SET nombre = :nombre, edad = :edad, genero = :genero, estatura = :estatura, peso = :peso, profileCompleted = :completed WHERE email = :email")
    suspend fun updateUserProfile(email: String, nombre: String, edad: Int, genero: String, estatura: Float, peso: Float, completed: Boolean)
}
