package com.example.deliveryroutesystem

import org.maplibre.android.geometry.LatLng

class OsrmRoutingProvider : RoutingProvider {

    override suspend fun fetchRoute(waypoints: List<LatLng>): RouteResult? {
        return RouteFetcher.fetchShortestPath(waypoints) ?: RouteFetcher.fetchRoute(waypoints)
    }

    override suspend fun fetchMatrix(points: List<LatLng>): CostMatrix? {
        return RouteFetcher.fetchMatrix(points)
    }
}
