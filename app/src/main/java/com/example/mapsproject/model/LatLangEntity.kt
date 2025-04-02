package com.example.mapsproject.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.gms.maps.model.LatLng

@Entity(tableName = "ListOfLatLangTable")
data class LatLangEntity(
    @PrimaryKey(autoGenerate = true)
    val id :Int,
    val locationName : String,
    val latLangList : List<LatLng>
)
