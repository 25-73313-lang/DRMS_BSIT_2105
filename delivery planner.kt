package com.example.deliveryroutesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object DeliveryPlanner {

    val RIDER_COLORS = listOf("#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA")

    suspend fun plan(
        hub: Hub,
        riderCount: Int,
        serviceMinutesPerStop: Int,
        orders: List<DeliveryOrder>,
        departureMillis: Long = System.currentTimeMillis(),
        routingProvider: RoutingProvider = OsrmRoutingProvider(),
        stopSequencer: StopSequencer = NearestFirstSequencer()
    ): List<RiderPlan> {
        val active = orders.filter { it.status != OrderStatus.DELIVERED }
        val groups = withContext(Dispatchers.Default) {
            RiderAssigner.assign(hub.latLng, active, riderCount)
        }
        val plans = mutableListOf<RiderPlan>()

        for ((index, group) in groups.withIndex()) {
            if (group.isNotEmpty() && plans.any { it.stops.isNotEmpty() }) {
                delay(300)
            }
            plans.add(
                planGroup(
                    riderId = index + 1,
                    start = hub.latLng,
                    startsAtHub = true,
                    hub = hub.latLng,
                    group = group,
                    serviceMinutesPerStop = serviceMinutesPerStop,
                    departureMillis = departureMillis,
                    routingProvider = routingProvider,
                    stopSequencer = stopSequencer
                )
            )
        }
        return plans
    }

    suspend fun replanRider(
        hub: Hub,
        old: RiderPlan,
        start: LatLng,
        remaining: List<DeliveryOrder>,
        serviceMinutesPerStop: Int,
        routingProvider: RoutingProvider = OsrmRoutingProvider(),
        stopSequencer: StopSequencer = NearestFirstSequencer()
    ): RiderPlan = planGroup(
        riderId = old.riderId,
        start = start,
        startsAtHub = false,
        hub = hub.latLng,
        group = remaining,
        serviceMinutesPerStop = serviceMinutesPerStop,
        departureMillis = System.currentTimeMillis(),
        routingProvider = routingProvider,
        stopSequencer = stopSequencer
    )

    private suspend fun planGroup(
        riderId: Int,
        start: LatLng,
        startsAtHub: Boolean,
        hub: LatLng,
        group: List<DeliveryOrder>,
        serviceMinutesPerStop: Int,
        departureMillis: Long,
        routingProvider: RoutingProvider,
        stopSequencer: StopSequencer
    ): RiderPlan {
        val color = RIDER_COLORS[(riderId - 1) % RIDER_COLORS.size]

        if (group.isEmpty()) {
            return RiderPlan(
                riderId, "Rider $riderId", color, emptyList(), emptyList(),
                0.0, 0.0, 0.0, departureMillis, false
            )
        }

        val stopPoints = group.map { LatLng(it.latitude, it.longitude) }
        val points = if (startsAtHub) listOf(hub) + stopPoints
        else listOf(start) + stopPoints + listOf(hub)
        val endIndex = if (startsAtHub) 0 else points.size - 1

        val matrix = routingProvider.fetchMatrix(points)
        var estimated = matrix == null
        val cost = matrix ?: StraightLineRoutingProvider().fetchMatrix(points)

        val isUrgentFlags = listOf(false) + group.map { it.isUrgent } + listOf(false)
        val visitOrder = stopSequencer.sequence(cost.distances, isUrgentFlags, 0, endIndex)
        val orderedOrders = visitOrder.map { group[it - 1] }
        val tour = listOf(0) + visitOrder + listOf(endIndex)
        val waypoints = tour.map { points[it] }

        val route = if (!estimated) routingProvider.fetchRoute(waypoints) else null
        val legSeconds: List<Double>
        val legMeters: List<Double>
        val line: List<LatLng>

        if (route != null && route.legDurationsSeconds.size == waypoints.size - 1) {
            legSeconds = route.legDurationsSeconds
            legMeters = route.legDistancesMeters
            line = route.points
        } else {
            estimated = true
            legSeconds = (0 until tour.size - 1).map { cost.durations[tour[it]][tour[it + 1]] }
            legMeters = (0 until tour.size - 1).map { cost.distances[tour[it]][tour[it + 1]] }
            line = waypoints
        }

        var clock = 0.0
        val plannedStops = orderedOrders.mapIndexed { i, order ->
            clock += legSeconds[i] / 60.0
            val arrival = clock
            clock += serviceMinutesPerStop
            val isLate = (order.isUrgent && arrival > 20.0) || (arrival > 40.0)
            order.copy(
                riderId = riderId,
                sequenceNumber = i + 1,
                etaMinutes = arrival,
                status = OrderStatus.IN_TRANSIT,
                isLate = isLate
            )
        }

        return RiderPlan(
            riderId = riderId,
            name = "Rider $riderId",
            colorHex = color,
            stops = plannedStops,
            routePoints = line,
            distanceKm = legMeters.sum() / 1000.0,
            driveMinutes = legSeconds.sum() / 60.0,
            serviceMinutes = serviceMinutesPerStop * plannedStops.size.toDouble(),
            departureMillis = departureMillis,
            isEstimated = estimated,
            legMeters = legMeters
        )
    }

    fun clockTime(departureMillis: Long, minutesAfterDeparture: Double): String =
        SimpleDateFormat("h:mm a", Locale.getDefault())
            .format(Date(departureMillis + (minutesAfterDeparture * 60_000).toLong()))

    fun formatDate(timeMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMillis))

    fun formatDisplayDate(timeMillis: Long): String {
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = fmt.format(Date(now))
        val dateStr = fmt.format(Date(timeMillis))
        return if (todayStr == dateStr) "Today (${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timeMillis))})"
        else SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(timeMillis))
    }

    fun formatDuration(minutes: Double): String {
        val m = minutes.roundToInt()
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
