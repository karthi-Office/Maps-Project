package com.example.mapsproject.utils

import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.SphericalUtil

object CalculatingCentroid {

    //Calculate total polygon area
    fun calculatePolygonArea(latLngList: List<LatLng>) : Double{

        // Calculate area using SphericalUtil
        val areaInSquareMeters = SphericalUtil.computeArea(latLngList)
        val areaInSquareKm = areaInSquareMeters / 1_000_000.0

        // Calculate Centroid

         return areaInSquareMeters

    }


    //    Calculating the Mid Point
    fun calculateCentroid(latLngList: List<LatLng>): LatLng {
        var latitudeSum = 0.0
        var longitudeSum = 0.0

        for (point in latLngList) {
            latitudeSum += point.latitude
            longitudeSum += point.longitude
        }
        return LatLng(latitudeSum / latLngList.size, longitudeSum / latLngList.size)
    }
}