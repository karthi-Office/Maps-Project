package com.example.mapsproject.view_models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapsproject.model.LatLangEntity
import com.example.mapsproject.repo.LatLngRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class MapViewModel @Inject constructor(private val repo : LatLngRepository) : ViewModel() {
         val optionLiveData = MutableLiveData(false)
    fun setMarkerFlagTrue() { optionLiveData.value = true }
    fun setMarkerFlagFalse() { optionLiveData.value = false }


// current location fetching
    val currentLocationFetching = MutableLiveData(false)
    fun setCurrentLocationFetchingFlagTrue() { optionLiveData.value = true }
    fun setCurrentLocationFetchingFlagFalse() { optionLiveData.value = false }

//    Calculation state
    val calculationLiveData = MutableLiveData(false)
    fun setCalculationFlagTrue() { calculationLiveData.value = true }
    fun setCalculationFlagFalse() { calculationLiveData.value = false }

     // live button
     val isLive = MutableLiveData(false)
    fun setLiveFlagTrue() { isLive.value = true }
    fun setLiveFlagFalse() { isLive.value = false }



   //DB actions

//    adding one set  of latLng
    fun addLatLng(latLangEntity: LatLangEntity) =
        viewModelScope.launch {
              repo.insertLatLngDetails(latLangEntity)
      }

    //Getting multiple latLng details Db
    val allLiveLatLng = repo.getAllLatLang()

    //Deleting all data
    fun deleteAllData() =
       viewModelScope.launch {
           repo.deleteAllData()
       }
    //get data count

    val countLiveData : LiveData<Int> = repo.getCount()



}

