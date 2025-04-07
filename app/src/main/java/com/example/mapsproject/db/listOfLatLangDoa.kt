package com.example.mapsproject.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mapsproject.model.LatLangEntity

@Dao
interface listOfLatLangDoa {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOneLocation(latLangEntity: LatLangEntity)

    @Query("SELECT * FROM ListOfLatLangTable ORDER BY id  ASC")
    fun getAllLatLang() : LiveData<List<LatLangEntity>>

    @Query("DELETE from LISTOFLATLANGTABLE")
    fun deleteTable()

    @Query("SELECT COUNT(*) FROM ListOfLatLangTable")
     fun getCount() :LiveData<Int>
}