package com.example.seebeepee.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.model.Waypoint
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.viewmodel.MainViewModel
import java.util.Locale
import kotlin.math.*

data class PlacedLabel(
    val rect: Rect,
    val text: String,
    val point: Offset
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waypoints = RouteManager.waypoints
    val breadcrumbs = RouteManager.breadcrumbs

    // Map toggles
    var showLabels by remember { mutableStateOf(true) }
    var showWaypoints by remember { mutableStateOf(true) }
    var showBreadcrumbs by remember { mutableStateOf(true) }
    var showGridlines by remember { mutableStateOf(true) }
    var showMapSettings by remember { mutableStateOf(false) }

    // Pan and Zoom gesture states
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    fun resetView() {
        zoom = 1f
        pan = Offset.Zero
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Map Canvas: ${RouteManager.currentRouteName}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showMapSettings = !showMapSettings }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Map Settings")
                    }
                    IconButton(onClick = { resetView() }) {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Reset View")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateBack,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Back to Navigation",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(0.2f, 10f)
                            pan += panChange
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                if (waypoints.isNotEmpty()) {
                    val minX = waypoints.minOf { it.x }
                    val maxX = waypoints.maxOf { it.x }
                    val minY = waypoints.minOf { it.y }
                    val maxY = waypoints.maxOf { it.y }

                    val rangeX = maxOf(2000.0, maxX - minX) // Dynamic scaling: minimum 2km range
                    val rangeY = maxOf(2000.0, maxY - minY)
                    val baseScale = minOf(canvasWidth / (rangeX * 1.1f), canvasHeight / (rangeY * 1.1f))
                    val scale = baseScale * zoom

                    val centerX = (minX + maxX) / 2.0
                    val centerY = (minY + maxY) / 2.0

                    fun project(x: Double, y: Double): Offset {
                        val px = (canvasWidth / 2f) + pan.x + ((x - centerX) * scale).toFloat()
                        val py = (canvasHeight / 2f) + pan.y - ((y - centerY) * scale).toFloat() // inverted Y for canvas
                        return Offset(px, py)
                    }

                    // 1. Draw 1km Coordinate Gridlines
                    if (showGridlines) {
                        val gridSpacingMeters = 1000.0
                        val startGridX = (minX / gridSpacingMeters).toInt() * gridSpacingMeters - gridSpacingMeters
                        val endGridX = (maxX / gridSpacingMeters).toInt() * gridSpacingMeters + gridSpacingMeters
                        val startGridY = (minY / gridSpacingMeters).toInt() * gridSpacingMeters - gridSpacingMeters
                        val endGridY = (maxY / gridSpacingMeters).toInt() * gridSpacingMeters + gridSpacingMeters

                        var gx = startGridX
                        while (gx <= endGridX) {
                            val p1 = project(gx, minY - 1000.0)
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.4f),
                                start = Offset(p1.x, 0f),
                                end = Offset(p1.x, canvasHeight),
                                strokeWidth = 1f
                            )
                            if (showLabels) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    "E: ${gx.toInt()}",
                                    p1.x + 4f,
                                    24f,
                                    Paint().apply {
                                        color = android.graphics.Color.GRAY
                                        textSize = 28f
                                    }
                                )
                            }
                            gx += gridSpacingMeters
                        }

                        var gy = startGridY
                        while (gy <= endGridY) {
                            val p1 = project(minX - 1000.0, gy)
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.4f),
                                start = Offset(0f, p1.y),
                                end = Offset(canvasWidth, p1.y),
                                strokeWidth = 1f
                            )
                            if (showLabels) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    "N: ${gy.toInt()}",
                                    8f,
                                    p1.y - 6f,
                                    Paint().apply {
                                        color = android.graphics.Color.GRAY
                                        textSize = 28f
                                    }
                                )
                            }
                            gy += gridSpacingMeters
                        }
                    }

                    // 2. Draw Active Leg / Route Segments
                    for (i in 0 until waypoints.size - 1) {
                        val p1 = project(waypoints[i].x, waypoints[i].y)
                        val p2 = project(waypoints[i + 1].x, waypoints[i + 1].y)
                        drawLine(
                            color = Color(0xFF1976D2), // Material Blue
                            start = p1,
                            end = p2,
                            strokeWidth = 5f
                        )
                    }

                    // 3. Speed-Graded Breadcrumbs Trail
                    if (showBreadcrumbs && breadcrumbs.isNotEmpty()) {
                        for (i in breadcrumbs.indices) {
                            val bc = breadcrumbs[i]
                            val p = project(bc.x, bc.y)
                            val speed = if (i > 0) CoordinateUtils.calculateSpeed(breadcrumbs[i - 1], bc) else 0.0
                            val bcColor = speedToColor(speed)
                            drawCircle(
                                color = bcColor,
                                radius = 5f,
                                center = p
                            )
                        }
                    }

                    // 4. Draw Waypoints & Intelligent Constraint-Based Label Placement
                    if (showWaypoints) {
                        dataIndexAndCrowding(waypoints).forEach { (idx, wp, _) ->
                            val p = project(wp.x, wp.y)
                            val isCurrent = (idx == viewModel.currentIndex)
                            val radius = if (isCurrent) 14f else 8f

                            drawCircle(
                                color = if (isCurrent) Color(0xFFD32F2F) else Color(0xFF424242),
                                radius = radius,
                                center = p
                            )
                            drawCircle(
                                color = Color.White,
                                radius = radius * 0.4f,
                                center = p
                            )
                        }

                        if (showLabels) {
                            val placedLabels = mutableListOf<PlacedLabel>()
                            val sortedWps = dataIndexAndCrowding(waypoints)

                            val textPaint = Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = 32f
                                isAntiAlias = true
                                typeface = Typeface.DEFAULT_BOLD
                            }

                            for ((_, wp, _) in sortedWps) {
                                val p = project(wp.x, wp.y)
                                val labelText = "${wp.name} (${wp.altitude.toInt()}m)"
                                val textWidth = textPaint.measureText(labelText)
                                val textHeight = 36f

                                val candidates = listOf(
                                    Rect(p.x + 16f, p.y - textHeight / 2f, p.x + 16f + textWidth, p.y + textHeight / 2f),
                                    Rect(p.x - 16f - textWidth, p.y - textHeight / 2f, p.x - 16f, p.y + textHeight / 2f),
                                    Rect(p.x - textWidth / 2f, p.y - 24f - textHeight, p.x + textWidth / 2f, p.y - 24f),
                                    Rect(p.x - textWidth / 2f, p.y + 24f, p.x + textWidth / 2f, p.y + 24f + textHeight)
                                )

                                var bestRect = candidates[0]
                                var minOverlap = Int.MAX_VALUE

                                for (cand in candidates) {
                                    var overlapCount = 0
                                    for (placed in placedLabels) {
                                        if (cand.overlaps(placed.rect)) {
                                            overlapCount++
                                        }
                                    }
                                    if (overlapCount < minOverlap) {
                                        minOverlap = overlapCount
                                        bestRect = cand
                                        if (overlapCount == 0) break
                                    }
                                }

                                placedLabels.add(PlacedLabel(bestRect, labelText, p))

                                drawRect(
                                    color = Color.White.copy(alpha = 0.85f),
                                    topLeft = Offset(bestRect.left - 4f, bestRect.top - 2f),
                                    size = Size(bestRect.width + 8f, bestRect.height + 4f)
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    labelText,
                                    bestRect.left,
                                    bestRect.bottom - 6f,
                                    textPaint
                                )
                            }
                        }
                    }
                }
            }

            // Info & Toggles Overlay Card
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Waypoints: ${waypoints.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Breadcrumbs: ${breadcrumbs.size}", fontSize = 12.sp)
                    Text(text = "Zoom: ${String.format(Locale.US, "%.1f", zoom)}x", fontSize = 12.sp)
                }
            }

            // Map Settings Dialog / Panel
            AnimatedVisibility(
                visible = showMapSettings,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showMapSettings = false },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clickable(enabled = false) {},
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Map Display Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Waypoint Labels", fontSize = 13.sp)
                                Switch(checked = showLabels, onCheckedChange = { showLabels = it })
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Waypoint Markers", fontSize = 13.sp)
                                Switch(checked = showWaypoints, onCheckedChange = { showWaypoints = it })
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Speed Breadcrumbs", fontSize = 13.sp)
                                Switch(checked = showBreadcrumbs, onCheckedChange = { showBreadcrumbs = it })
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show 1km Gridlines", fontSize = 13.sp)
                                Switch(checked = showGridlines, onCheckedChange = { showGridlines = it })
                            }

                            Button(
                                onClick = { showMapSettings = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class WaypointCrowding(val index: Int, val waypoint: Waypoint, val crowdingScore: Double)

private fun dataIndexAndCrowding(waypoints: List<Waypoint>): List<WaypointCrowding> {
    val list = mutableListOf<WaypointCrowding>()
    for (i in waypoints.indices) {
        val wp = waypoints[i]
        var totalDist = 0.0
        for (j in waypoints.indices) {
            if (i != j) {
                totalDist += CoordinateUtils.calculateDistance(wp.x, wp.y, waypoints[j].x, waypoints[j].y)
            }
        }
        val crowding = if (waypoints.size > 1) totalDist / (waypoints.size - 1) else 0.0
        list.add(WaypointCrowding(i, wp, crowding))
    }
    return list.sortedBy { it.crowdingScore }
}

private fun speedToColor(speedKmh: Double): Color {
    val clamped = speedKmh.coerceIn(0.0, 6.0)
    val ratio = (clamped / 6.0).toFloat()

    return when {
        ratio < 0.2f -> {
            val t = ratio / 0.2f
            Color(red = 1f, green = 0.5f * t, blue = 0f)
        }
        ratio < 0.4f -> {
            val t = (ratio - 0.2f) / 0.2f
            Color(red = 1f, green = 0.5f + 0.5f * t, blue = 0f)
        }
        ratio < 0.6f -> {
            val t = (ratio - 0.4f) / 0.2f
            Color(red = 1f - t, green = 1f, blue = 0f)
        }
        ratio < 0.8f -> {
            val t = (ratio - 0.6f) / 0.2f
            Color(red = 0f, green = 1f, blue = t)
        }
        else -> {
            val t = (ratio - 0.8f) / 0.2f
            Color(red = 0f, green = 1f - t, blue = 1f)
        }
    }
}
