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
                title = {
                    val routeTitle = RouteManager.currentRouteName.substringBeforeLast(".")
                    Text(text = if (routeTitle.isBlank()) "Map View" else routeTitle)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Reset View
                    IconButton(onClick = { resetView() }) {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Reset View")
                    }
                    // Return to Navigation
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.Navigation, contentDescription = "Return to Navigation")
                    }
                    // Map Settings
                    IconButton(onClick = { showMapSettings = !showMapSettings }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Map Settings")
                    }
                }
            )
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

                    val rangeX = maxOf(200.0, maxX - minX)
                    val rangeY = maxOf(200.0, maxY - minY)
                    // Added buffer area around map canvas (1.35f scale factor)
                    val baseScale = minOf(canvasWidth / (rangeX * 1.35f), canvasHeight / (rangeY * 1.35f))
                    val scale = baseScale * zoom

                    val centerX = (minX + maxX) / 2.0
                    val centerY = (minY + maxY) / 2.0

                    fun project(x: Double, y: Double): Offset {
                        val px = (canvasWidth / 2f) + pan.x + ((x - centerX) * scale).toFloat()
                        val py = (canvasHeight / 2f) + pan.y - ((y - centerY) * scale).toFloat()
                        return Offset(px, py)
                    }

                    // 1. Draw 1km Coordinate Gridlines & Two-Digit Grid Markings (no N, E, or trailing zeros)
                    if (viewModel.showGridlines) {
                        val gridSpacingMeters = 1000.0
                        val startGridX = (minX / gridSpacingMeters).toInt() * gridSpacingMeters - gridSpacingMeters
                        val endGridX = (maxX / gridSpacingMeters).toInt() * gridSpacingMeters + gridSpacingMeters
                        val startGridY = (minY / gridSpacingMeters).toInt() * gridSpacingMeters - gridSpacingMeters
                        val endGridY = (maxY / gridSpacingMeters).toInt() * gridSpacingMeters + gridSpacingMeters

                        val gridTextPaint = Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 26f
                            isAntiAlias = true
                        }

                        var gx = startGridX
                        while (gx <= endGridX) {
                            val p1 = project(gx, minY - 1000.0)
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.4f),
                                start = Offset(p1.x, 0f),
                                end = Offset(p1.x, canvasHeight),
                                strokeWidth = 1f
                            )
                            val gridNum = ((gx % 100000.0) / 1000.0).toInt() % 100
                            val labelText = String.format(Locale.US, "%02d", gridNum)
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                p1.x + 4f,
                                28f,
                                gridTextPaint
                            )
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
                            val gridNum = ((gy % 100000.0) / 1000.0).toInt() % 100
                            val labelText = String.format(Locale.US, "%02d", gridNum)
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                8f,
                                p1.y - 6f,
                                gridTextPaint
                            )
                            gy += gridSpacingMeters
                        }
                    }

                    // 2. Draw Active Leg / Route Segments & Bearing Option
                    for (i in 0 until waypoints.size - 1) {
                        val wp1 = waypoints[i]
                        val wp2 = waypoints[i + 1]
                        val p1 = project(wp1.x, wp1.y)
                        val p2 = project(wp2.x, wp2.y)
                        drawLine(
                            color = Color(0xFF1976D2),
                            start = p1,
                            end = p2,
                            strokeWidth = 5f
                        )

                        // When Bearing is selected, bearing appears parallel to the waypoint leg close to the originating waypoint
                        if (viewModel.showWaypointBearing) {
                            val bearing = CoordinateUtils.calculateBearing(wp1.x, wp1.y, wp2.x, wp2.y)
                            val bearingText = "${bearing.toInt()}°"
                            val dx = p2.x - p1.x
                            val dy = p2.y - p1.y
                            val len = sqrt(dx * dx + dy * dy)
                            if (len >= 55f) {
                                val ux = dx / len
                                val uy = dy / len
                                val perpX = -uy
                                val perpY = ux

                                val distAlongSegment = maxOf(22f, len * 0.15f)
                                val perpOffset = 16f

                                val textX = p1.x + ux * distAlongSegment + perpX * perpOffset
                                val textY = p1.y + uy * distAlongSegment + perpY * perpOffset

                                var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angleDeg > 90f) {
                                    angleDeg -= 180f
                                } else if (angleDeg < -90f) {
                                    angleDeg += 180f
                                }

                                val nativeCanvas = drawContext.canvas.nativeCanvas
                                nativeCanvas.save()
                                nativeCanvas.translate(textX, textY)
                                nativeCanvas.rotate(angleDeg)

                                val bearingPaint = Paint().apply {
                                    color = android.graphics.Color.BLUE
                                    textSize = 24f
                                    isAntiAlias = true
                                    typeface = Typeface.DEFAULT
                                    textAlign = Paint.Align.CENTER
                                }
                                val fontMetrics = bearingPaint.fontMetrics
                                val baselineOffset = - (fontMetrics.ascent + fontMetrics.descent) / 2f
                                nativeCanvas.drawText(bearingText, 0f, baselineOffset, bearingPaint)
                                nativeCanvas.restore()
                            }
                        }
                    }

                    // 3. Speed-Graded Breadcrumbs Trail
                    if (viewModel.showBreadcrumbs && breadcrumbs.isNotEmpty()) {
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

                    // 4. Waypoint Label Deduplication & Drawing
                    if (viewModel.showWaypoints) {
                        val groupedWps = waypoints.groupBy { Pair(round(it.x * 10) / 10, round(it.y * 10) / 10) }

                        groupedWps.values.forEach { group ->
                            val wp = group.first()
                            val p = project(wp.x, wp.y)
                            val isCurrent = group.any { waypoints.indexOf(it) == viewModel.currentIndex }
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

                        if (viewModel.showWaypointName || viewModel.showWaypointAltitude) {
                            val placedLabels = mutableListOf<PlacedLabel>()
                            val textPaint = Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = 30f
                                isAntiAlias = true
                                typeface = Typeface.DEFAULT_BOLD
                            }

                            groupedWps.values.forEach { group ->
                                val wp = group.first()
                                val p = project(wp.x, wp.y)

                                val parts = mutableListOf<String>()
                                if (viewModel.showWaypointName) {
                                    val names = group.map { it.name }.distinct().joinToString(" / ")
                                    parts.add(names)
                                }
                                val showAlt = viewModel.showWaypointAltitude
                                if (showAlt) {
                                    parts.add("${wp.altitude.toInt()}m")
                                }
                                val labelText = parts.joinToString(" ")

                                if (labelText.isNotBlank()) {
                                    val textWidth = textPaint.measureText(labelText)
                                    val textHeight = 34f

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
                                        bestRect.bottom - 4f,
                                        textPaint
                                    )
                                }
                            }
                        }
                    }

                    // 5. Out-of-Bounds Indicator
                    val currentPos = viewModel.getCurrentPositionMetric()
                    val posScreen = project(currentPos.first, currentPos.second)
                    if (posScreen.x < 0f || posScreen.x > canvasWidth || posScreen.y < 0f || posScreen.y > canvasHeight) {
                        val currWp = waypoints[viewModel.currentIndex]
                        val dist = CoordinateUtils.calculateDistance(currWp.x, currWp.y, currentPos.first, currentPos.second)
                        val distStr = if (dist < 1000.0) "${dist.toInt()}m" else String.format(Locale.US, "%.1fkm", dist / 1000.0)

                        val padding = 40f
                        val clampedX = posScreen.x.coerceIn(padding, canvasWidth - padding)
                        val clampedY = posScreen.y.coerceIn(padding, canvasHeight - padding)

                        val indicatorPaint = Paint().apply {
                            color = android.graphics.Color.RED
                            textSize = 30f
                            isAntiAlias = true
                            typeface = Typeface.DEFAULT_BOLD
                        }

                        drawCircle(
                            color = Color.Red,
                            radius = 20f,
                            center = Offset(clampedX, clampedY)
                        )

                        val labelText = "➔ $distStr"
                        val textWidth = indicatorPaint.measureText(labelText)

                        val textX = if (clampedX >= canvasWidth - padding - 10f) {
                            clampedX - textWidth - 25f
                        } else {
                            clampedX + 25f
                        }
                        val textY = if (clampedY >= canvasHeight - padding - 10f) {
                            clampedY - 10f
                        } else {
                            clampedY + 10f
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            textX,
                            textY,
                            indicatorPaint
                        )
                    }
                }
            }

            // Map Settings Dialog (Instant persistence as soon as changed, no Apply button)
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
                                Text("Waypoint Name", fontSize = 13.sp)
                                Switch(
                                    checked = viewModel.showWaypointName,
                                    onCheckedChange = { viewModel.updateShowWaypointName(it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Waypoint Altitude", fontSize = 13.sp)
                                Switch(
                                    checked = viewModel.showWaypointAltitude,
                                    onCheckedChange = { viewModel.updateShowWaypointAltitude(it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Waypoint Bearing", fontSize = 13.sp)
                                Switch(
                                    checked = viewModel.showWaypointBearing,
                                    onCheckedChange = { viewModel.updateShowWaypointBearing(it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Waypoint Markers", fontSize = 13.sp)
                                Switch(
                                    checked = viewModel.showWaypoints,
                                    onCheckedChange = { viewModel.updateShowWaypoints(it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Speed Breadcrumbs", fontSize = 13.sp)
                                Switch(
                                    checked = viewModel.showBreadcrumbs,
                                    onCheckedChange = { viewModel.updateShowBreadcrumbs(it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show 1km Gridlines", fontSize = 13.sp)
                                Switch(
                                    checked = viewModel.showGridlines,
                                    onCheckedChange = { viewModel.updateShowGridlines(it) }
                                )
                            }

                            Button(
                                onClick = { showMapSettings = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
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
