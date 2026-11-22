package com.example.deliveryroutesystem

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.deliveryroutesystem.ui.DeliveryViewModel
import com.example.deliveryroutesystem.ui.screens.HistorySheet
import com.example.deliveryroutesystem.ui.screens.OrdersSheet
import com.example.deliveryroutesystem.ui.screens.SetupSheet
import com.example.deliveryroutesystem.ui.screens.SummarySheet
import com.example.deliveryroutesystem.ui.theme.DeliveryRouteTheme
import com.example.deliveryroutesystem.ui.theme.MintSecondary
import com.example.deliveryroutesystem.ui.theme.NavyBackground
import com.example.deliveryroutesystem.ui.theme.TealPrimary
import com.example.deliveryroutesystem.ui.theme.UrgentRed
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private val HUB = Hub("Barangay 2 Hall", LatLng(14.07575, 120.63223))

private const val STREET_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val SATELLITE_STYLE_JSON = """
{
  "version": 8,
  "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
  "sources": {
    "imagery": {
      "type": "raster",
      "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "Imagery © Esri, Maxar, Earthstar Geographics"
    }
  },
  "layers": [ { "id": "imagery", "type": "raster", "source": "imagery" } ]
}
"""

enum class ActiveSheet {
    NONE,
    ORDERS,
    SETUP,
    SUMMARY,
    HISTORY
}

class MainActivity : ComponentActivity() {

    private val viewModel: DeliveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        setContent {
            DeliveryRouteTheme {
                DeliveryRouteApp(viewModel)
            }
        }
    }
}

private fun applyMapStyle(context: Context, map: MapLibreMap, satellite: Boolean) {
    val builder = if (satellite) Style.Builder().fromJson(SATELLITE_STYLE_JSON)
    else Style.Builder().fromUri(STREET_STYLE_URL)

    map.setStyle(builder) { style ->
        val text = try {
            context.resources.openRawResource(R.raw.nasugbu_barangays)
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }

        if (text.isNotEmpty()) {
            style.addSource(GeoJsonSource("nasugbu-source", text))
            val onlyBarangays = Expression.eq(
                Expression.get("admin_type:PH"), Expression.literal("barangay")
            )

            style.addLayer(CircleLayer("barangay-dots", "nasugbu-source").apply {
                setFilter(onlyBarangays)
                setProperties(
                    PropertyFactory.circleRadius(4f),
                    PropertyFactory.circleColor(AndroidColor.parseColor("#19B38A")),
                    PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f)
                )
            })
            style.addLayer(SymbolLayer("barangay-labels", "nasugbu-source").apply {
                setFilter(onlyBarangays)
                setProperties(
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textColor(AndroidColor.WHITE),
                    PropertyFactory.textHaloColor(AndroidColor.parseColor("#0B2B3B")),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.1f))
                )
            })
        }
    }
}

private fun makePin(
    context: Context,
    colorInt: Int,
    label: String,
    sizeDp: Int = 34,
    isUrgent: Boolean = false,
    isLate: Boolean = false
): Icon {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    if (isLate) {
        paint.color = AndroidColor.parseColor("#E65100")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    }

    val lateOffset = if (isLate) 3 * density else 0f
    val outerRadius = size / 2f - lateOffset

    if (isUrgent) {
        paint.color = AndroidColor.parseColor("#D32F2F")
        canvas.drawCircle(size / 2f, size / 2f, outerRadius, paint)
    } else {
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(size / 2f, size / 2f, outerRadius, paint)
    }

    paint.color = colorInt
    canvas.drawCircle(size / 2f, size / 2f, outerRadius - 3 * density, paint)

    paint.color = AndroidColor.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = size * 0.40f
    val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
    val text = if (isUrgent) "⚡$label" else label
    canvas.drawText(text, size / 2f, y, paint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private class RouteGeometry(val cum: DoubleArray, val stopAt: DoubleArray)

private class RiderSim(val plan: RiderPlan, val geo: RouteGeometry) {
    var progress = 0.0
    var waitLeft = 0.0
    var arrivedStopId: String? = null
}

private fun metersBetween(a: LatLng, b: LatLng): Double =
    RouteSequencer.calculateDistance(a, b) * 1000.0

private fun buildGeometry(plan: RiderPlan): RouteGeometry {
    val pts = plan.routePoints
    val cum = DoubleArray(pts.size)
    for (i in 1 until pts.size) cum[i] = cum[i - 1] + metersBetween(pts[i - 1], pts[i])
    val total = cum.last()

    val legTotal = plan.legMeters.sum()
    val stopAt = DoubleArray(plan.stops.size)
    var acc = 0.0
    for (i in plan.stops.indices) {
        acc += plan.legMeters.getOrElse(i) { 0.0 }
        stopAt[i] = if (legTotal > 0) acc / legTotal * total
        else total * (i + 1) / (plan.stops.size + 1)
    }
    return RouteGeometry(cum, stopAt)
}

private fun pointAt(pts: List<LatLng>, cum: DoubleArray, d: Double): LatLng {
    if (d <= 0.0) return pts.first()
    if (d >= cum.last()) return pts.last()
    var lo = 0
    var hi = cum.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (cum[mid] <= d) lo = mid else hi = mid
    }
    val seg = cum[hi] - cum[lo]
    val t = if (seg > 0) (d - cum[lo]) / seg else 0.0
    return LatLng(
        pts[lo].latitude + (pts[hi].latitude - pts[lo].latitude) * t,
        pts[lo].longitude + (pts[hi].longitude - pts[lo].longitude) * t
    )
}

private fun ridersJson(items: List<Triple<Int, String, LatLng>>): String {
    val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
    items.forEachIndexed { i, (id, color, pos) ->
        if (i > 0) sb.append(",")
        sb.append("{\"type\":\"Feature\",\"properties\":{\"color\":\"$color\",\"label\":\"R$id\"},")
        sb.append("\"geometry\":{\"type\":\"Point\",\"coordinates\":[${pos.longitude},${pos.latitude}]}}")
    }
    sb.append("]}")
    return sb.toString()
}

private fun ensureRiderLayers(style: Style) {
    if (style.getSource("riders-source") != null) return
    style.addSource(GeoJsonSource("riders-source", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
    style.addLayer(CircleLayer("riders-circle", "riders-source").apply {
        setProperties(
            PropertyFactory.circleRadius(14f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(3f)
        )
    })
    style.addLayer(SymbolLayer("riders-label", "riders-source").apply {
        setProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor(AndroidColor.WHITE),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true)
        )
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryRouteApp(viewModel: DeliveryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val barangays = remember { BarangayLoader.load(context) }
    val orders by viewModel.pendingOrders.collectAsState()
    val history by viewModel.history.collectAsState()
    val plans by viewModel.plans.collectAsState()

    val riderCount by viewModel.riderCount.collectAsState()
    val serviceMinutes by viewModel.serviceMinutes.collectAsState()
    val useSatellite by viewModel.useSatellite.collectAsState()

    val isPlanning by viewModel.isPlanning.collectAsState()
    val simRunning by viewModel.simRunning.collectAsState()
    val simSpeed by viewModel.simSpeed.collectAsState()
    val focusRider by viewModel.focusRider.collectAsState()
    val editingOrder by viewModel.editingOrder.collectAsState()

    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    val todayDateStr = DeliveryPlanner.formatDate(System.currentTimeMillis())
    val todayHistory = history.filter { it.deliveredDate == todayDateStr }
    val deliveredCount = todayHistory.size
    val collectedCod = todayHistory.sumOf { it.cashOnDelivery }

    DisposableEffect(simRunning) {
        val activity = context as? Activity
        if (simRunning) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                val nasugbuBounds = LatLngBounds.Builder()
                    .include(LatLng(14.0000, 120.5300))
                    .include(LatLng(14.1900, 120.7300))
                    .build()
                map.setLatLngBoundsForCameraTarget(nasugbuBounds)
                map.setMinZoomPreference(10.5)
                map.setMaxZoomPreference(18.0)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(HUB.latLng, 12.5))
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.setLogoMargins(30, 0, 0, 180)
                map.uiSettings.setAttributionMargins(30, 0, 0, 180)
                mapInstance = map
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapInstance, useSatellite) {
        val map = mapInstance ?: return@LaunchedEffect
        applyMapStyle(context, map, useSatellite)
    }

    LaunchedEffect(mapInstance, orders, plans, focusRider, useSatellite) {
        val map = mapInstance ?: return@LaunchedEffect
        map.clear()

        map.addMarker(
            MarkerOptions()
                .position(HUB.latLng)
                .icon(makePin(context, AndroidColor.parseColor("#0B2B3B"), "H", 42))
                .title("Hub - ${HUB.name}")
                .snippet("All riders start & end here")
        )

        if (plans.isEmpty()) {
            orders.forEachIndexed { i, o ->
                val pinColor = if (o.isUrgent) AndroidColor.parseColor("#D32F2F") else AndroidColor.parseColor("#0E7C7B")
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(o.latitude, o.longitude))
                        .icon(makePin(context, pinColor, "${i + 1}", sizeDp = 34, isUrgent = o.isUrgent, isLate = o.isLate))
                        .title("${if (o.isUrgent) "[URGENT] " else ""}${o.customerName}")
                        .snippet("${o.barangay} - ${o.addressDetails}")
                )
            }
        } else {
            plans.filter { focusRider == 0 || it.riderId == focusRider }.forEach { plan ->
                val color = AndroidColor.parseColor(plan.colorHex)
                if (plan.routePoints.isNotEmpty()) {
                    map.addPolyline(
                        PolylineOptions().addAll(plan.routePoints).color(AndroidColor.WHITE).width(9f)
                    )
                    map.addPolyline(
                        PolylineOptions().addAll(plan.routePoints).color(color).width(5f)
                    )
                }
                plan.stops.forEach { s ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(s.latitude, s.longitude))
                            .icon(makePin(context, color, "${s.sequenceNumber}", sizeDp = 34, isUrgent = s.isUrgent, isLate = s.isLate))
                            .title("${plan.name} - Stop ${s.sequenceNumber}: ${s.customerName}${if (s.isUrgent) " [URGENT]" else ""}")
                            .snippet("ETA ${DeliveryPlanner.clockTime(plan.departureMillis, s.etaMinutes)}${if (s.isLate) " (⚠️ LATE)" else ""}")
                    )
                }
            }
        }
    }

    LaunchedEffect(mapInstance, plans, focusRider) {
        val map = mapInstance ?: return@LaunchedEffect
        if (simRunning) return@LaunchedEffect
        val pts = plans.filter { focusRider == 0 || it.riderId == focusRider }
            .flatMap { it.routePoints }
        if (pts.size < 2) return@LaunchedEffect
        try {
            val b = LatLngBounds.Builder()
            pts.forEach { b.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 110))
        } catch (e: Exception) { }
    }

    LaunchedEffect(simRunning, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect

        fun showRiders(items: List<Triple<Int, String, LatLng>>) {
            val style = map.style
            if (style == null || !style.isFullyLoaded) return
            ensureRiderLayers(style)
            style.getSourceAs<GeoJsonSource>("riders-source")?.setGeoJson(ridersJson(items))
        }

        if (!simRunning) {
            showRiders(emptyList())
            return@LaunchedEffect
        }

        val sims = mutableMapOf<Int, RiderSim>()
        val shown = mutableMapOf<Int, Triple<Int, String, LatLng>>()
        var last = System.nanoTime()

        while (true) {
            delay(50)
            val now = System.nanoTime()
            val dtSim = ((now - last) / 1e9).coerceAtMost(0.5) * simSpeed
            last = now

            if (isPlanning) continue

            val current = plans
            if (current.isEmpty()) {
                viewModel.setSimRunning(false)
                break
            }

            for (plan in current) {
                if (plan.stops.isEmpty() || plan.routePoints.size < 2) continue

                var sim = sims[plan.riderId]
                if (sim == null || sim.plan !== plan) {
                    sim = RiderSim(plan, buildGeometry(plan))
                    sims[plan.riderId] = sim
                }

                if (sim.waitLeft > 0) {
                    sim.waitLeft -= dtSim
                    if (sim.waitLeft <= 0) {
                        sim.waitLeft = 0.0
                        sim.arrivedStopId?.let { viewModel.markDelivered(HUB, it) }
                    }
                } else if (sim.arrivedStopId == null) {
                    val speed = (plan.distanceKm * 1000.0 / (plan.driveMinutes * 60.0).coerceAtLeast(1.0))
                        .coerceIn(3.0, 25.0)
                    sim.progress += speed * dtSim
                    val target = sim.geo.stopAt[0]
                    if (sim.progress >= target) {
                        sim.progress = target
                        sim.waitLeft = serviceMinutes * 60.0
                        sim.arrivedStopId = plan.stops.first().id
                    }
                }

                shown[plan.riderId] = Triple(
                    plan.riderId,
                    plan.colorHex,
                    pointAt(plan.routePoints, sim.geo.cum, sim.progress)
                )
            }
            showRiders(shown.values.toList())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HeaderBand(
            orderCount = orders.size,
            riderCount = riderCount
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            val activePlans = plans.filter { it.stops.isNotEmpty() }
            if (activePlans.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = focusRider == 0,
                        onClick = { viewModel.setFocusRider(0) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                    activePlans.forEach { p ->
                        FilterChip(
                            selected = focusRider == p.riderId,
                            onClick = { viewModel.setFocusRider(p.riderId) },
                            label = { Text("R${p.riderId}") },
                            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }

            SmallFloatingActionButton(
                onClick = {
                    val map = mapInstance ?: return@SmallFloatingActionButton
                    val pts = mutableListOf(HUB.latLng)
                    if (plans.isNotEmpty()) {
                        pts.addAll(plans.flatMap { it.routePoints })
                    } else if (orders.isNotEmpty()) {
                        pts.addAll(orders.map { LatLng(it.latitude, it.longitude) })
                    }
                    if (pts.size > 1) {
                        try {
                            val b = LatLngBounds.Builder()
                            pts.forEach { b.include(it) }
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120))
                        } catch (_: Exception) {}
                    } else {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(HUB.latLng, 13.0))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp)
            ) {
                Text("🎯", fontSize = 18.sp)
            }

            if (isPlanning) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(16.dp),
                    color = NavyBackground.copy(alpha = 0.88f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = MintSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Optimizing road routes...", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingSquareButton(
                    icon = "📦",
                    label = "Orders",
                    badgeCount = orders.size,
                    isActive = activeSheet == ActiveSheet.ORDERS,
                    onClick = {
                        activeSheet = if (activeSheet == ActiveSheet.ORDERS) ActiveSheet.NONE else ActiveSheet.ORDERS
                    }
                )
                FloatingSquareButton(
                    icon = "⚙️",
                    label = "Setup",
                    isActive = activeSheet == ActiveSheet.SETUP,
                    onClick = {
                        activeSheet = if (activeSheet == ActiveSheet.SETUP) ActiveSheet.NONE else ActiveSheet.SETUP
                    }
                )
                FloatingSquareButton(
                    icon = "📋",
                    label = "Summary",
                    badgeCount = plans.sumOf { it.stops.size },
                    isActive = activeSheet == ActiveSheet.SUMMARY,
                    onClick = {
                        activeSheet = if (activeSheet == ActiveSheet.SUMMARY) ActiveSheet.NONE else ActiveSheet.SUMMARY
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingSquareButton(
                        icon = if (useSatellite) "🗺️" else "🛰️",
                        label = if (useSatellite) "Street" else "Satellite",
                        isActive = false,
                        onClick = { viewModel.setUseSatellite(!useSatellite) }
                    )

                    FloatingSquareButton(
                        icon = "📜",
                        label = "History",
                        badgeCount = history.size,
                        isActive = activeSheet == ActiveSheet.HISTORY,
                        onClick = {
                            activeSheet = if (activeSheet == ActiveSheet.HISTORY) ActiveSheet.NONE else ActiveSheet.HISTORY
                        }
                    )
                }
            }
        }
    }

    if (activeSheet != ActiveSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            when (activeSheet) {
                ActiveSheet.ORDERS -> {
                    OrdersSheet(
                        orders = orders,
                        barangays = barangays,
                        editingOrder = editingOrder,
                        onAddOrder = { name, details, b, cod, urgent ->
                            viewModel.addOrder(name, details, b, cod, urgent)
                        },
                        onUpdateOrder = { id, name, details, b, cod, urgent ->
                            viewModel.updateOrder(id, name, details, b, cod, urgent)
                        },
                        onStartEditOrder = { order -> viewModel.startEditOrder(order) },
                        onCancelEdit = { viewModel.clearEditOrder() },
                        onDeleteOrder = { id -> viewModel.removeOrder(id) },
                        onAdd4Samples = {
                            viewModel.add4Samples(barangays)
                            Toast.makeText(context, "Added 4 sample orders for Nasugbu!", Toast.LENGTH_SHORT).show()
                        },
                        onClearOrders = { viewModel.clearPendingOrders() },
                        onOrderClick = { order ->
                            activeSheet = ActiveSheet.NONE
                            mapInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(order.latitude, order.longitude),
                                    15.5
                                )
                            )
                        }
                    )
                }
                ActiveSheet.SETUP -> {
                    SetupSheet(
                        riderCount = riderCount,
                        onRiderCountChange = { viewModel.setRiderCount(it) },
                        serviceMinutes = serviceMinutes,
                        onServiceMinutesChange = { viewModel.setServiceMinutes(it) },
                        useSatellite = useSatellite,
                        onUseSatelliteChange = { viewModel.setUseSatellite(it) }
                    )
                }
                ActiveSheet.SUMMARY -> {
                    SummarySheet(
                        plans = plans,
                        pendingCount = orders.size,
                        deliveredCount = deliveredCount,
                        collectedCod = collectedCod,
                        isPlanning = isPlanning,
                        onPlan = {
                            viewModel.planRoutes(HUB) {
                                if (viewModel.plans.value.any { it.isEstimated }) {
                                    Toast.makeText(
                                        context,
                                        "Road service unreachable for some routes - times are estimates.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        simRunning = simRunning,
                        simSpeed = simSpeed,
                        onToggleSim = { viewModel.setSimRunning(!simRunning) },
                        onSpeedChange = { speed -> viewModel.setSimSpeed(speed) },
                        onDelivered = { id -> viewModel.markDelivered(HUB, id) }
                    )
                }
                ActiveSheet.HISTORY -> {
                    HistorySheet(
                        history = history,
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
                ActiveSheet.NONE -> {}
            }
        }
    }
}

@Composable
fun HeaderBand(orderCount: Int, riderCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brush.linearGradient(listOf(NavyBackground, TealPrimary)))
            .drawBehind {
                drawCircle(
                    color = ComposeColor.White.copy(alpha = 0.07f),
                    radius = size.width * 0.35f,
                    center = Offset(size.width * 0.95f, 0f)
                )
            }
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                "CHRISVEN ROUTING",
                color = MintSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Delivery Route Management System",
                color = ComposeColor.White,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderPill("📍 Hub: Barangay 2")
                HeaderPill("$orderCount pending")
                HeaderPill("$riderCount riders")
            }
        }
    }
}

@Composable
fun HeaderPill(text: String) {
    Surface(color = ComposeColor.White.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = ComposeColor.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FloatingSquareButton(
    icon: String,
    label: String,
    badgeCount: Int = 0,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) TealPrimary else MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.size(62.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) ComposeColor.White else MaterialTheme.colorScheme.onSurface
                )
            }
            if (badgeCount > 0) {
                Surface(
                    color = if (isActive) MintSecondary else UrgentRed,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        "$badgeCount",
                        color = ComposeColor.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
