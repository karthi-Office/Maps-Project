package com.example.mapsproject.addMarkers

import com.google.android.gms.maps.model.LatLng

class AddMarkers {

    private val EPSILON = 1e-9 // Small tolerance to handle floating-point precision

    fun isSelfIntersectingPolygon(latLngList: List<LatLng>): Boolean {
        if (latLngList.size < 3) return false

        // Check for intersections
        for (i in 0 until latLngList.size - 1) {
            for (j in i + 1 until latLngList.size - 1) {
                if (j != i + 1 && j != i) {
                    if (doLinesIntersect(
                            latLngList[i], latLngList[i + 1],
                            latLngList[j], latLngList[j + 1]
                        )
                    ) {
                        return true
                    }
                }
            }
        }

        // Check closing line intersection
        val lastIndex = latLngList.size - 1
        for (i in 0 until lastIndex - 1) {
            if (doLinesIntersect(
                    latLngList[lastIndex], latLngList[0],
                    latLngList[i], latLngList[i + 1]
                )
            ) {
                return true
            }
        }

        return false
    }

    private fun doLinesIntersect(p1: LatLng, p2: LatLng, q1: LatLng, q2: LatLng): Boolean {

        fun orientation(a: LatLng, b: LatLng, c: LatLng): Int {
            val value = (b.latitude - a.latitude) * (c.longitude - b.longitude) -
                    (b.longitude - a.longitude) * (c.latitude - b.latitude)

            return when {
                Math.abs(value) < EPSILON -> 0 // Collinear
                value > 0.0 -> 1 // Clockwise
                else -> -1 // Counterclockwise
            }
        }

        val o1 = orientation(p1, p2, q1)
        val o2 = orientation(p1, p2, q2)
        val o3 = orientation(q1, q2, p1)
        val o4 = orientation(q1, q2, p2)

        // General case: Check if lines intersect
        if (o1 != o2 && o3 != o4) {
            return true
        }

        // Special cases: Check for collinear points lying on the segment
        return (o1 == 0 && onSegment(p1, q1, p2)) ||
                (o2 == 0 && onSegment(p1, q2, p2)) ||
                (o3 == 0 && onSegment(q1, p1, q2)) ||
                (o4 == 0 && onSegment(q1, p2, q2))
    }

    private fun onSegment(p: LatLng, q: LatLng, r: LatLng): Boolean {
        return (q.latitude <= maxOf(p.latitude, r.latitude) + EPSILON &&
                q.latitude >= minOf(p.latitude, r.latitude) - EPSILON &&
                q.longitude <= maxOf(p.longitude, r.longitude) + EPSILON &&
                q.longitude >= minOf(p.longitude, r.longitude) - EPSILON)
    }
}
