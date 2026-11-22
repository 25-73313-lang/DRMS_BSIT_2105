package com.example.deliveryroutesystem

import android.content.Context
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

/**
 * Reads res/raw/nasugbu_barangays.json and keeps only the barangay center points
 * (features whose geometry is a Point and admin_type:PH == "barangay").
 */
object BarangayLoader {

    fun load(context: Context): List<BarangayPoint> {
        return try {
            val text = context.resources.openRawResource(R.raw.nasugbu_barangays)
                .bufferedReader().use { it.readText() }
            val features = JSONObject(text).getJSONArray("features")
            val result = mutableListOf<BarangayPoint>()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.optJSONObject("properties") ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue
                if (geometry.optString("type") != "Point") continue
                if (props.optString("admin_type:PH") != "barangay") continue

                val name = props.optString("name")
                if (name.isBlank()) continue

                val c = geometry.getJSONArray("coordinates") // [lon, lat]
                result.add(BarangayPoint(name, LatLng(c.getDouble(1), c.getDouble(0))))
            }
            result.distinctBy { it.name }.sortedBy { it.name }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Name of the barangay whose center is closest to the tapped point. */
    fun nearest(barangays: List<BarangayPoint>, point: LatLng): BarangayPoint? =
        barangays.minByOrNull { RouteSequencer.calculateDistance(it.latLng, point) }
}
