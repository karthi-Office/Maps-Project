package com.example.mapsproject.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mapsproject.model.LatLangEntity
import com.example.mapsproject.type_converters.LatLngConverter

@Database(entities = [LatLangEntity::class], exportSchema = false, version = 1)
@TypeConverters(LatLngConverter::class)
abstract class LatLangRoomDb : RoomDatabase() {
   abstract val latLangDoa : listOfLatLangDoa

}