package com.example.deliveryroutesystem

import org.maplibre.android.geometry.LatLng
import kotlin.math.*

object RouteSequencer {

    fun calculateDistance(start: LatLng, end: LatLng): Double {
        val r = 6371.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun nearestFirst(cost: Array<DoubleArray>, startIndex: Int = 0, endIndex: Int = 0): List<Int> {
        val remaining = (0 until cost.size).filter { it != startIndex && it != endIndex }.toMutableList()
        val order = mutableListOf<Int>()
        var current = startIndex
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { cost[current][it] }!!
            order.add(next)
            remaining.remove(next)
            current = next
        }
        return order
    }

    fun nearestFirstUrgentPriority(
        cost: Array<DoubleArray>,
        isUrgent: List<Boolean>,
        startIndex: Int = 0,
        endIndex: Int = 0
    ): List<Int> {
        return NearestFirstSequencer().sequence(cost, isUrgent, startIndex, endIndex)
    }

    fun optimizeTour(
        cost: Array<DoubleArray>,
        startIndex: Int = 0,
        endIndex: Int = 0
    ): List<Int> {
        return OptimizedTourSequencer().sequence(cost, emptyList(), startIndex, endIndex)
    }

    fun sequenceRoute(startLocation: LatLng, orders: List<DeliveryOrder>): List<DeliveryOrder> {
        val pendingOrders = orders.filter { it.status != OrderStatus.DELIVERED }.toMutableList()
        val urgentOrders = pendingOrders.filter { it.isUrgent }.toMutableList()
        val normalOrders = pendingOrders.filter { !it.isUrgent }.toMutableList()

        val sequencedOrders = mutableListOf<DeliveryOrder>()
        var currentLocation = startLocation
        var stepCounter = 1

        while (urgentOrders.isNotEmpty()) {
            val nextOrder = urgentOrders.minByOrNull { order ->
                calculateDistance(currentLocation, LatLng(order.latitude, order.longitude))
            }
            nextOrder?.let { order ->
                val sequenced = order.copy(sequenceNumber = stepCounter++)
                sequencedOrders.add(sequenced)
                urgentOrders.remove(order)
                currentLocation = LatLng(order.latitude, order.longitude)
            }
        }

        while (normalOrders.isNotEmpty()) {
            val nextOrder = normalOrders.minByOrNull { order ->
                calculateDistance(currentLocation, LatLng(order.latitude, order.longitude))
            }
            nextOrder?.let { order ->
                val sequenced = order.copy(sequenceNumber = stepCounter++)
                sequencedOrders.add(sequenced)
                normalOrders.remove(order)
                currentLocation = LatLng(order.latitude, order.longitude)
            }
        }

        val deliveredOrders = orders.filter { it.status == OrderStatus.DELIVERED }
        return sequencedOrders + deliveredOrders
    }
}
