package com.example.mapsproject.type_converters

import androidx.room.TypeConverter
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LatLngConverter {

    private val gson = Gson()

    @TypeConverter
    fun fromLatLngList(latLngList: List<LatLng>): String {
        return gson.toJson(latLngList)
    }

    @TypeConverter
    fun toLatLngList(data: String): List<LatLng> {
        val listType = object : TypeToken<List<LatLng>>() {}.type
        return gson.fromJson(data, listType)
    }
}