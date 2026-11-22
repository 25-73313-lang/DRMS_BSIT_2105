package com.example.deliveryroutesystem

import org.maplibre.android.geometry.LatLng

class StraightLineRoutingProvider(
    private val speedKmh: Double = 25.0,
    private val detourFactor: Double = 1.3
) : RoutingProvider {

    override suspend fun fetchRoute(waypoints: List<LatLng>): RouteResult? = null

    override suspend fun fetchMatrix(points: List<LatLng>): CostMatrix {
        val n = points.size
        val dist = Array(n) { DoubleArray(n) }
        val time = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) continue
                val meters = RouteSequencer.calculateDistance(points[i], points[j]) * 1000.0 * detourFactor
                dist[i][j] = meters
                time[i][j] = meters / 1000.0 / speedKmh * 3600.0
            }
        }
        return CostMatrix(time, dist)
    }
}
