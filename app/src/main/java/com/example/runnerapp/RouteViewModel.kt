package com.example.runnerapp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.osmdroid.util.GeoPoint

class RouteViewModel : ViewModel() {
    val isTracking = MutableLiveData(false)
    val points = MutableLiveData<MutableList<GeoPoint>>(mutableListOf())

    fun addPoint(p: GeoPoint) {
        val list = points.value ?: mutableListOf()
        list.add(p)
        points.postValue(list)
    }

    fun clearRoute() {
        points.postValue(mutableListOf())
    }
}
