package com.example.deliveryroutesystem

import org.maplibre.android.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos

object RiderAssigner {

    private fun bearing(from: LatLng, to: LatLng): Double {
        val dLat = to.latitude - from.latitude
        val dLon = (to.longitude - from.longitude) * cos(Math.toRadians(from.latitude))
        val deg = Math.toDegrees(atan2(dLon, dLat))
        return (deg + 360.0) % 360.0
    }

    private fun point(o: DeliveryOrder) = LatLng(o.latitude, o.longitude)

    fun assign(hub: LatLng, orders: List<DeliveryOrder>, riderCount: Int): List<List<DeliveryOrder>> {
        val k = riderCount.coerceAtLeast(1)
        if (orders.isEmpty()) return List(k) { emptyList() }
        if (orders.size <= k) {
            val result = MutableList(k) { emptyList<DeliveryOrder>() }
            orders.forEachIndexed { idx, order ->
                result[idx] = listOf(order)
            }
            return result
        }

        val groups = sweep(hub, orders, k).map { it.toMutableList() }.toMutableList()
        improve(hub, groups)
        return groups.map { it.toList() }
    }

    private fun sweep(hub: LatLng, orders: List<DeliveryOrder>, k: Int): List<List<DeliveryOrder>> {
        if (orders.isEmpty()) return List(k) { emptyList() }
        val sorted = orders.sortedBy { bearing(hub, point(it)) }
        val angles = sorted.map { bearing(hub, point(it)) }

        var startIndex = 0
        var biggestGap = -1.0
        for (i in sorted.indices) {
            val next = angles[(i + 1) % sorted.size]
            var gap = next - angles[i]
            if (gap <= 0) gap += 360.0
            if (gap > biggestGap) {
                biggestGap = gap
                startIndex = (i + 1) % sorted.size
            }
        }
        val rotated = sorted.drop(startIndex) + sorted.take(startIndex)

        val base = rotated.size / k
        val extra = rotated.size % k
        var cursor = 0
        return List(k) { rider ->
            val count = base + if (rider < extra) 1 else 0
            val endIdx = (cursor + count).coerceAtMost(rotated.size)
            val slice = if (cursor < rotated.size) rotated.subList(cursor, endIdx).toList() else emptyList()
            cursor = endIdx
            slice
        }
    }

    private fun tourLengthKm(hub: LatLng, stops: List<DeliveryOrder>): Double {
        if (stops.isEmpty()) return 0.0
        val pts = listOf(hub) + stops.map { point(it) }
        val n = pts.size
        val d = Array(n) { i ->
            DoubleArray(n) { j -> if (i == j) 0.0 else RouteSequencer.calculateDistance(pts[i], pts[j]) }
        }
        val order = RouteSequencer.optimizeTour(d, 0, 0)
        var total = 0.0
        var previous = 0
        for (idx in order) {
            if (idx in 0 until n) {
                total += d[previous][idx]
                previous = idx
            }
        }
        return total + d[previous][0]
    }

    private fun improve(hub: LatLng, groups: MutableList<MutableList<DeliveryOrder>>) {
        val k = groups.size
        if (k < 2) return

        val lengths = groups.map { tourLengthKm(hub, it) }.toMutableList()
        var improved = true
        var passes = 0

        while (improved && passes < 8) {
            improved = false
            passes++

            for (a in 0 until k) {
                for (b in 0 until k) {
                    if (a == b || groups[a].size <= groups[b].size) continue
                    for (o in groups[a].toList()) {
                        if (o !in groups[a] || groups[a].size <= groups[b].size) continue
                        val newA = groups[a] - o
                        val newB = groups[b] + o
                        val la = tourLengthKm(hub, newA)
                        val lb = tourLengthKm(hub, newB)
                        if (la + lb < lengths[a] + lengths[b] - 1e-6) {
                            groups[a] = newA.toMutableList()
                            groups[b] = newB.toMutableList()
                            lengths[a] = la
                            lengths[b] = lb
                            improved = true
                        }
                    }
                }
            }

            for (a in 0 until k) {
                for (b in a + 1 until k) {
                    for (x in groups[a].toList()) {
                        for (y in groups[b].toList()) {
                            if (x !in groups[a] || y !in groups[b]) continue
                            val newA = (groups[a] - x) + y
                            val newB = (groups[b] - y) + x
                            val la = tourLengthKm(hub, newA)
                            val lb = tourLengthKm(hub, newB)
                            if (la + lb < lengths[a] + lengths[b] - 1e-6) {
                                groups[a] = newA.toMutableList()
                                groups[b] = newB.toMutableList()
                                lengths[a] = la
                                lengths[b] = lb
                                improved = true
                            }
                        }
                    }
                }
            }
        }
    }
}
