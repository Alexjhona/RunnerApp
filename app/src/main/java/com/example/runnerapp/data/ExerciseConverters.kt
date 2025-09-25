package com.example.runnerapp.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ExerciseConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromExerciseList(exercises: List<Exercise>?): String {
        return gson.toJson(exercises ?: emptyList<Exercise>())
    }

    @TypeConverter
    fun toExerciseList(json: String?): List<Exercise> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Exercise>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun fromCompletedExerciseList(exercises: List<CompletedExercise>?): String {
        return gson.toJson(exercises ?: emptyList<CompletedExercise>())
    }

    @TypeConverter
    fun toCompletedExerciseList(json: String?): List<CompletedExercise> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<CompletedExercise>>() {}.type
        return gson.fromJson(json, type)
    }
}
