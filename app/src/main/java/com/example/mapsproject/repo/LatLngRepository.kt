package com.example.mapsproject.repo

import androidx.lifecycle.LiveData
import com.example.mapsproject.db.listOfLatLangDoa
import com.example.mapsproject.model.LatLangEntity
import javax.inject.Inject

class LatLngRepository @Inject constructor(private val listOfLatLangDoa: listOfLatLangDoa){

    fun insertLatLngDetails(latLangEntity : LatLangEntity) = listOfLatLangDoa.insertOneLocation(latLangEntity)

    fun getAllLatLang() : LiveData<List<LatLangEntity>> = listOfLatLangDoa.getAllLatLang()

    fun deleteAllData() = listOfLatLangDoa.deleteTable()

    fun getCount() : LiveData<Int> = listOfLatLangDoa.getCount()
}