package com.example.runnerapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Carrera::class, Usuario::class, RunSession::class, WorkoutRoutine::class, WorkoutSession::class, HydrationRecord::class, HydrationSettings::class],
    version = 7,                 // Incremented version for separated HydrationSettings entity
    exportSchema = false
)
@TypeConverters(Converters::class, ExerciseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun carreraDao(): CarreraDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun runSessionDao(): RunSessionDao
    abstract fun workoutRoutineDao(): WorkoutRoutineDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun hydrationDao(): HydrationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "carreras_db"
                )
                    .fallbackToDestructiveMigration()  // recrea la BD en cambios
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
