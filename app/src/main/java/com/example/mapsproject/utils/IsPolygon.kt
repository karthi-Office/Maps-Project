package com.example.mapsproject.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object IsPolygon {

    // Function to check if the markers form a valid polygon
    fun isPolygon(points: List<LatLng>): Boolean {
        if (points.size < 3) return true // Allow adding markers until at least 3 are present

        // Convert LatLng to Point
        val pointList = points.map { Point(it.latitude, it.longitude) }

        // Check if points are collinear, colliding, or if edges intersect
        val isCollinear = arePointsCollinear(pointList)
        val isColliding = arePointsColliding(pointList)
        val hasIntersectingEdges = hasIntersectingEdges(pointList)

        // Debugging: Log the results
        println("isCollinear: $isCollinear, isColliding: $isColliding, hasIntersectingEdges: $hasIntersectingEdges")

        // Return true only if points are not collinear, not colliding, and edges do not intersect
        return !isCollinear && !isColliding && !hasIntersectingEdges
    }

    // Function to check if points are collinear
    private fun arePointsCollinear(points: List<Point>): Boolean {
        if (points.size < 3) return false // Need at least 3 points to check collinearity

        // Check collinearity for every set of 3 consecutive points
        for (i in 0 until points.size - 2) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[i + 1]
            val (x3, y3) = points[i + 2]

            // Calculate the area of the triangle formed by the three points
            val area = x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)
            if (abs(area) < 1e-10) { // If area is approximately zero, points are collinear
                return true
            }
        }
        return false
    }

    // Function to check if points are colliding
    private fun arePointsColliding(points: List<Point>, threshold: Double = 1.0): Boolean {
        for (i in 0 until points.size - 1) {
            for (j in i + 1 until points.size) {
                val distance = calculateDistance(points[i], points[j])
                if (distance < threshold) {
                    // Debugging: Log the colliding points
                    println("Points $i and $j are colliding: distance = $distance meters")
                    return true
                }
            }
        }
        return false
    }

    // Function to check if edges intersect
    private fun hasIntersectingEdges(points: List<Point>): Boolean {
        if (points.size < 4) return false // Need at least 4 points to have intersecting edges

        // Check all pairs of non-consecutive edges
        for (i in 0 until points.size - 1) {
            for (j in i + 1 until points.size - 1) {
                if (abs(i - j) > 1) { // Skip consecutive edges
                    val edge1Start = points[i]
                    val edge1End = points[i + 1]
                    val edge2Start = points[j]
                    val edge2End = points[j + 1]

                    if (doEdgesIntersect(edge1Start, edge1End, edge2Start, edge2End)) {
                        // Debugging: Log the intersecting edges
                        println("Edges $i-${i + 1} and $j-${j + 1} intersect")
                        return true
                    }
                }
            }
        }
        return false
    }

    // Function to check if two edges intersect
    private fun doEdgesIntersect(
        edge1Start: Point, edge1End: Point,
        edge2Start: Point, edge2End: Point
    ): Boolean {
        // Calculate orientations
        val orientation1 = calculateOrientation(edge1Start, edge1End, edge2Start)
        val orientation2 = calculateOrientation(edge1Start, edge1End, edge2End)
        val orientation3 = calculateOrientation(edge2Start, edge2End, edge1Start)
        val orientation4 = calculateOrientation(edge2Start, edge2End, edge1End)

        // General case: edges intersect
        if (orientation1 != orientation2 && orientation3 != orientation4) {
            return true
        }

        // Special cases: edges are collinear and overlapping
        if (orientation1 == 0 && isOnSegment(edge1Start, edge2Start, edge1End)) return true
        if (orientation2 == 0 && isOnSegment(edge1Start, edge2End, edge1End)) return true
        if (orientation3 == 0 && isOnSegment(edge2Start, edge1Start, edge2End)) return true
        if (orientation4 == 0 && isOnSegment(edge2Start, edge1End, edge2End)) return true

        return false
    }

    // Function to calculate orientation of three points
    private fun calculateOrientation(p1: Point, p2: Point, p3: Point): Int {
        val area = (p2.latitude - p1.latitude) * (p3.longitude - p2.longitude) -
                (p2.longitude - p1.longitude) * (p3.latitude - p2.latitude)
        return when {
            area < 0 -> -1 // Counterclockwise
            area > 0 -> 1  // Clockwise
            else -> 0      // Collinear
        }
    }

    // Function to check if a point lies on a segment
    private fun isOnSegment(p1: Point, p2: Point, p3: Point): Boolean {
        return p2.latitude <= max(p1.latitude, p3.latitude) &&
                p2.latitude >= min(p1.latitude, p3.latitude) &&
                p2.longitude <= max(p1.longitude, p3.longitude) &&
                p2.longitude >= min(p1.longitude, p3.longitude)
    }

    // Function to calculate distance between two points using Haversine formula
    private fun calculateDistance(p1: Point, p2: Point): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(p1.latitude)) * kotlin.math.cos(Math.toRadians(p2.latitude)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }

    // Data class to represent a point
    data class Point(val latitude: Double, val longitude: Double)



}