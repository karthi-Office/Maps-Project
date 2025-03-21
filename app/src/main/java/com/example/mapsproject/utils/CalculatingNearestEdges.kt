package com.example.mapsproject.utils

import android.location.Location
import com.google.android.gms.maps.model.LatLng

object CalculatingNearestEdges {

    // Calculate distance using Haversine formula
    fun calculateDistance(from: LatLng, to: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0].toDouble()
    }

    // Find nearest point on line segment
    fun getNearestPointOnSegment(p: LatLng, a: LatLng, b: LatLng): LatLng {
        val aToP = LatLng(p.latitude - a.latitude, p.longitude - a.longitude)
        val aToB = LatLng(b.latitude - a.latitude, b.longitude - a.longitude)
        val aToBSquared = aToB.latitude * aToB.latitude + aToB.longitude * aToB.longitude
        val atbDotAtp = aToB.latitude * aToP.latitude + aToB.longitude * aToP.longitude
        val t = (atbDotAtp / aToBSquared).coerceIn(0.0, 1.0)

        return LatLng(
            a.latitude + aToB.latitude * t,
            a.longitude + aToB.longitude * t
        )
    }
}