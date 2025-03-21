package com.example.mapsproject.view_models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor() : ViewModel() {
         val optionLiveData = MutableLiveData(false)
    fun setMarkerFlagTrue() { optionLiveData.value = true }
    fun setMarkerFlagFalse() { optionLiveData.value = false }


//    Calculation state
    val calculationLiveData = MutableLiveData(false)
    fun setCalculationFlagTrue() { calculationLiveData.value = true }
    fun setCalculationFlagFalse() { calculationLiveData.value = false }

     // live button
     val isLive = MutableLiveData(false)
    fun setLiveFlagTrue() { isLive.value = true }
    fun setLiveFlagFalse() { isLive.value = false }

    // Calculate nearest boundary live data
    val nearestBoundaryLive = MutableLiveData(false)
    fun setNearestBoundaryLiveFlagTrue() { nearestBoundaryLive.value = true }
    fun setNearestBoundaryLiveFlagFalse() { nearestBoundaryLive.value = false }

    // Calculate nearest Marker live data
    val nearestMarkerLive = MutableLiveData(false)
    fun setNearestMarkerLiveFlagTrue() { nearestMarkerLive.value = true }
    fun setNearestMarkerLiveFlagFalse() { nearestMarkerLive.value = false }


    // Calculate nearest Marker live data
    val currentToCenter = MutableLiveData(false)
    fun setCurrentToCenterLiveFlagTrue() { currentToCenter.value = true }
    fun setCurrentToCenterLiveFlagFalse() { currentToCenter.value = false }


}