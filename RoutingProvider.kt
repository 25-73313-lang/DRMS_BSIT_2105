package com.example.deliveryroutesystem

import org.maplibre.android.geometry.LatLng

interface RoutingProvider {
    suspend fun fetchRoute(waypoints: List<LatLng>): RouteResult?
    suspend fun fetchMatrix(points: List<LatLng>): CostMatrix?
}
