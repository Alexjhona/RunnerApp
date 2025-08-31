package com.example.runnerapp.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTrackPoints(points: List<TrackPoint>?): String {
        return gson.toJson(points ?: emptyList<TrackPoint>())
    }

    @TypeConverter
    fun toTrackPoints(json: String?): List<TrackPoint> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<TrackPoint>>() {}.type
        return gson.fromJson(json, type)
    }
}
