package com.example.deliveryroutesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL

object RouteFetcher {

    private const val BASE_URL = "https://router.project-osrm.org"

    private fun coordinates(points: List<LatLng>): String =
        points.joinToString(";") { "${it.longitude},${it.latitude}" }

    private fun httpGet(urlString: String): String? {
        return try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "NasugbuDeliveryApp/1.0")
                connectTimeout = 8000
                readTimeout = 12000
            }
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchShortestPath(waypoints: List<LatLng>): RouteResult? {
        if (waypoints.size < 2) return null
        val points = ArrayList<LatLng>()
        val legSeconds = ArrayList<Double>()
        val legMeters = ArrayList<Double>()

        for (i in 0 until waypoints.size - 1) {
            if (i > 0) delay(120)
            val leg = fetchShortestLeg(waypoints[i], waypoints[i + 1]) ?: return null
            points.addAll(leg.points)
            legSeconds.add(leg.durationSeconds)
            legMeters.add(leg.distanceMeters)
        }
        return RouteResult(points, legMeters.sum(), legSeconds.sum(), legSeconds, legMeters)
    }

    private suspend fun fetchShortestLeg(a: LatLng, b: LatLng): RouteResult? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/route/v1/driving/${coordinates(listOf(a, b))}" +
                "?overview=full&geometries=geojson&alternatives=true"
        var body = httpGet(url)
        if (body == null) {
            delay(300)
            body = httpGet(url)
        }
        if (body == null) return@withContext null

        try {
            val routes = JSONObject(body).getJSONArray("routes")
            if (routes.length() == 0) return@withContext null

            var best: JSONObject? = null
            for (i in 0 until routes.length()) {
                val r = routes.getJSONObject(i)
                if (best == null || r.getDouble("distance") < best.getDouble("distance")) best = r
            }
            val route = best ?: return@withContext null

            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val path = ArrayList<LatLng>(coords.length())
            for (i in 0 until coords.length()) {
                val pair = coords.getJSONArray(i)
                path.add(LatLng(pair.getDouble(1), pair.getDouble(0)))
            }

            val meters = route.getDouble("distance")
            val seconds = route.getDouble("duration")
            RouteResult(path, meters, seconds, listOf(seconds), listOf(meters))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchRoadRoute(start: LatLng, end: LatLng): List<LatLng> =
        fetchRoute(listOf(start, end))?.points ?: emptyList()

    suspend fun fetchRoute(waypoints: List<LatLng>): RouteResult? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null
        val url = "$BASE_URL/route/v1/driving/${coordinates(waypoints)}" +
                "?overview=full&geometries=geojson"
        var body = httpGet(url)
        if (body == null) {
            delay(300)
            body = httpGet(url)
        }
        if (body == null) return@withContext null

        try {
            val routes = JSONObject(body).getJSONArray("routes")
            if (routes.length() == 0) return@withContext null
            val route = routes.getJSONObject(0)

            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val path = ArrayList<LatLng>(coords.length())
            for (i in 0 until coords.length()) {
                val pair = coords.getJSONArray(i)
                path.add(LatLng(pair.getDouble(1), pair.getDouble(0)))
            }

            val legs = route.getJSONArray("legs")
            val legSeconds = ArrayList<Double>()
            val legMeters = ArrayList<Double>()
            for (i in 0 until legs.length()) {
                legSeconds.add(legs.getJSONObject(i).getDouble("duration"))
                legMeters.add(legs.getJSONObject(i).getDouble("distance"))
            }

            RouteResult(
                points = path,
                distanceMeters = route.getDouble("distance"),
                durationSeconds = route.getDouble("duration"),
                legDurationsSeconds = legSeconds,
                legDistancesMeters = legMeters
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchMatrix(points: List<LatLng>): CostMatrix? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        val url = "$BASE_URL/table/v1/driving/${coordinates(points)}?annotations=duration,distance"
        var body = httpGet(url)
        if (body == null) {
            delay(300)
            body = httpGet(url)
        }
        if (body == null) return@withContext null

        try {
            val json = JSONObject(body)
            val n = points.size
            val durations = Array(n) { DoubleArray(n) }
            val distances = Array(n) { DoubleArray(n) }
            val dArr = json.getJSONArray("durations")
            val mArr = json.getJSONArray("distances")
            for (i in 0 until n) {
                val dRow = dArr.getJSONArray(i)
                val mRow = mArr.getJSONArray(i)
                for (j in 0 until n) {
                    durations[i][j] = if (dRow.isNull(j)) 1e9 else dRow.getDouble(j)
                    distances[i][j] = if (mRow.isNull(j)) 1e9 else mRow.getDouble(j)
                }
            }
            CostMatrix(durations, distances)
        } catch (e: Exception) {
            null
        }
    }
}
