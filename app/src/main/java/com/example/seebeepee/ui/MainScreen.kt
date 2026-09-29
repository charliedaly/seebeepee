package com.example.seebeepee.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.service.HikingForegroundService
import com.example.seebeepee.ui.theme.SeeBeePeeTheme
import com.example.seebeepee.util.CoordinateUtils
import com.example.seebeepee.util.RouteParser
import com.example.seebeepee.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val currentIndex = viewModel.currentIndex
    val legMetrics = viewModel.legMetrics
    val waypoints = RouteManager.waypoints

    var isTrackingActive by remember { mutableStateOf(false) }
    var showSaveGpxDialog by remember { mutableStateOf(false) }

    // File picker launcher for CSV/GPX route files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val parsedWaypoints = if (uri.toString().endsWith(".gpx", true) || content.contains("<gpx", true)) {
                    RouteParser.parseGpx(content)
                } else {
                    RouteParser.parseCsv(content)
                }

                if (parsedWaypoints.isNotEmpty()) {
                    val fileName = (uri.lastPathSegment ?: "Imported Route").substringBeforeLast(".")
                    viewModel.loadRoute(fileName, parsedWaypoints)
                }
            } catch (e: Exception) {
                RouteManager.logError("Error opening route file: ${e.message}")
            }
        }
    }

    // Auto-scroll behavior favoring upcoming waypoints
    LaunchedEffect(currentIndex) {
        if (waypoints.isNotEmpty()) {
            val scrollTarget = (currentIndex + 1).coerceAtMost(waypoints.size - 1)
            coroutineScope.launch {
                listState.animateScrollToItem(scrollTarget)
            }
        }
    }

    // Save GPX Dialog when stopping tracking
    if (showSaveGpxDialog) {
        AlertDialog(
            onDismissRequest = { showSaveGpxDialog = false },
            title = { Text("Save Breadcrumbs as GPX") },
            text = { Text("Do you want to save your hike breadcrumbs as a GPX track before stopping?") },
            confirmButton = {
                TextButton(onClick = {
                    RouteManager.persistUnsavedBreadcrumbs()
                    showSaveGpxDialog = false
                    viewModel.gpsStatus = "Breadcrumbs saved to GPX"
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    RouteManager.clearBreadcrumbs()
                    showSaveGpxDialog = false
                    viewModel.gpsStatus = "Breadcrumbs discarded"
                }) {
                    Text("Discard")
                }
            }
        )
    }

    var showOutsideMapConfirmation by remember { mutableStateOf(false) }

    val startTrackingAction = {
        val serviceIntent = Intent(context, HikingForegroundService::class.java)
        try {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                RouteManager.logError("Location permissions missing when starting GPS tracking.")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            isTrackingActive = true
            viewModel.gpsStatus = "GPS Tracking Active"
        } catch (e: SecurityException) {
            RouteManager.logError("SecurityException starting service: ${e.message}")
            isTrackingActive = false
            viewModel.gpsStatus = "Tracking Failed (Permission Denied)"
        } catch (e: Exception) {
            RouteManager.logError("Error starting service: ${e.message}")
            isTrackingActive = false
            viewModel.gpsStatus = "Tracking Failed"
        }
    }

    if (showOutsideMapConfirmation) {
        AlertDialog(
            onDismissRequest = { showOutsideMapConfirmation = false },
            title = { Text("Are you sure?") },
            text = { Text("Current position is outside map area") },
            confirmButton = {
                TextButton(onClick = {
                    showOutsideMapConfirmation = false
                    startTrackingAction()
                }) {
                    Text("Start Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOutsideMapConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val routeTitle = RouteManager.currentRouteName.substringBeforeLast(".")
                        Text(
                            text = if (routeTitle.equals("No Route Loaded", true)) "No Route Loaded" else routeTitle,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (waypoints.isNotEmpty()) {
                            val totalDistKm = viewModel.totalDistance / 1000.0
                            val totalClimbM = viewModel.totalClimb
                            val totalTimeStr = formatHoursMinutes(viewModel.totalTimeRequired)
                            Text(
                                text = String.format(Locale.US, "Distance %.1fkm Altitude %.0fm Time %s", totalDistKm, totalClimbM, totalTimeStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // File load icon in the title section to open route file picker
                    IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Load Route File",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start / Finish Button
                    Button(
                        onClick = {
                            if (isTrackingActive) {
                                val serviceIntent = Intent(context, HikingForegroundService::class.java).apply {
                                    action = HikingForegroundService.ACTION_STOP
                                }
                                try {
                                    context.startService(serviceIntent)
                                } catch (e: Exception) {
                                    RouteManager.logError("Error stopping tracking service: ${e.message}")
                                }
                                isTrackingActive = false
                                viewModel.gpsStatus = "GPS Tracking Paused"
                                showSaveGpxDialog = true
                            } else {
                                if (!viewModel.isCurrentPositionOnMap()) {
                                    showOutsideMapConfirmation = true
                                } else {
                                    startTrackingAction()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTrackingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isTrackingActive) "Finish Hiking" else "Start Hiking",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTrackingActive) "Finish" else "Start", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Map Screen Button (Globe / Map icon)
                    OutlinedButton(
                        onClick = onNavigateToMap,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Map Screen",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Map", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Settings Button (Gear icon)
                    IconButton(
                        onClick = { viewModel.showSettings = !viewModel.showSettings },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (viewModel.showSettings) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First-Run Onboarding Banner if no route is loaded
                if (waypoints.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Welcome to SeeBeePee Hike Navigator!",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Get started by opening a route file (CSV / GPX) or generating a sample Irish Grid route.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Route File")
                                }
                                Button(
                                    onClick = {
                                        val sampleCsv = RouteParser.generateSampleIrishGridCsv()
                                        val sampleWps = RouteParser.parseCsv(sampleCsv)
                                        viewModel.loadRoute("Sample Irish Grid Hike", sampleWps)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Load Sample CSV")
                                }
                            }
                        }
                    }
                }

                // 1. Top Section (Current Leg): Position, Bearing, Distance, Time, Target using 6-digit grid references
                CurrentLegMetricsCard(viewModel = viewModel)

                // 2. Waypoint Table: Compact rows using 6-digit grid reference values, no final totals row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Name", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                            Text("Location", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                            Text("Alt(m)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                            Text("Brg(°)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                            Text("Dist(m)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                            Text("Time(m)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                        }

                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            itemsIndexed(waypoints) { index, wp ->
                                val isCurrent = (index == currentIndex)
                                val metric = if (index < legMetrics.size) legMetrics[index] else null
                                val locStr = if (viewModel.coordinateSystem == "grid") {
                                    formatSuccinctGrid(wp.x, wp.y)
                                } else {
                                    val (lat, lon) = CoordinateUtils.metricToLatLon(wp.x, wp.y)
                                    String.format(Locale.US, "%.2f, %.2f", lat, lon)
                                }

                                val altStr = String.format(Locale.US, "%.0f", wp.altitude)
                                val brgStr = if (metric != null && metric.toWaypoint != null) String.format(Locale.US, "%.0f", metric.bearing) else "-"
                                val distStr = if (metric != null && metric.toWaypoint != null) String.format(Locale.US, "%.0f", metric.distanceMeters) else "-"
                                val timeStr = if (metric != null && metric.toWaypoint != null) String.format(Locale.US, "%.1f", metric.timeMinutes) else "-"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            when {
                                                isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                index % 2 == 0 -> MaterialTheme.colorScheme.surface
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            }
                                        )
                                        .clickable { viewModel.setCurrentWaypointIndex(index) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = wp.name,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1.4f),
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = locStr,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1.5f)
                                    )
                                    Text(
                                        text = altStr,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    Text(
                                        text = brgStr,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    Text(
                                        text = distStr,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.0f)
                                    )
                                    Text(
                                        text = timeStr,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.0f)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                // 3. Overall Hike Statistics: Condensed two-line format
                OverallHikeStatsCard(viewModel = viewModel)
            }

            // Settings Overlay Panel (Instant persistence as soon as changed, no Apply button)
            AnimatedVisibility(
                visible = viewModel.showSettings,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { viewModel.showSettings = false },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .wrapContentHeight()
                            .clickable(enabled = false) {},
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Hike & Navigation Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Flat Pace Slider
                            Column {
                                Text(text = "Flat Pace: ${viewModel.flatPace.toInt()} min/km", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.flatPace.toFloat(),
                                    onValueChange = { viewModel.updateFlatPace(it.toDouble()) },
                                    valueRange = 10f..40f,
                                    steps = 30
                                )
                            }

                            // Climb Penalty Slider
                            Column {
                                Text(text = "Climb Penalty: ${String.format(Locale.US, "%.1f", viewModel.climbPenalty)} min / 10m", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.climbPenalty.toFloat(),
                                    onValueChange = { viewModel.updateClimbPenalty(it.toDouble()) },
                                    valueRange = 0.5f..3.0f,
                                    steps = 25
                                )
                            }

                            // Coordinate System selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Coordinate Format:", fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.updateCoordinateSystem("grid") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (viewModel.coordinateSystem == "grid") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Text("Grid", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.updateCoordinateSystem("latlon") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (viewModel.coordinateSystem == "latlon") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Text("Lat/Lon", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Voice alerts toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Spoken Voice Alerts:", fontSize = 13.sp)
                                Switch(
                                    checked = viewModel.voiceAlertsEnabled,
                                    onCheckedChange = { viewModel.updateVoiceAlerts(it) }
                                )
                            }

                            // Cone angle slider
                            Column {
                                Text(text = "Off-Course Cone Angle: ${viewModel.coneAngle.toInt()}°", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.coneAngle,
                                    onValueChange = { viewModel.updateConeAngle(it) },
                                    valueRange = 5f..30f,
                                    steps = 25
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Route Load / Generate Sample Buttons in Settings
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Route File", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        val sampleCsv = RouteParser.generateSampleIrishGridCsv()
                                        val sampleWps = RouteParser.parseCsv(sampleCsv)
                                        viewModel.loadRoute("Sample Irish Grid Hike", sampleWps)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Generate Sample", fontSize = 11.sp)
                                }
                            }

                            Button(
                                onClick = { viewModel.showSettings = false },
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

@Composable
fun CurrentLegMetricsCard(viewModel: MainViewModel) {
    val currentLeg = viewModel.currentLegMetric
    val targetWp = currentLeg?.toWaypoint

    val posStr = if (viewModel.coordinateSystem == "grid") {
        val lastBc = RouteManager.breadcrumbs.lastOrNull()
        val x = lastBc?.x ?: RouteManager.waypoints.getOrNull(viewModel.currentIndex)?.x ?: 0.0
        val y = lastBc?.y ?: RouteManager.waypoints.getOrNull(viewModel.currentIndex)?.y ?: 0.0
        formatSuccinctGrid(x, y)
    } else {
        viewModel.currentPositionString
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Position: $posStr",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = viewModel.gpsStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("Bearing", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.0f", currentLeg.bearing)}°" else "-")
                MetricItem("Distance", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.0f", currentLeg.distanceMeters)}m" else "-")
                MetricItem("Time", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.1f", currentLeg.timeMinutes)}m" else "-")
                MetricItem("Target", targetWp?.name ?: "Finished")
            }
        }
    }
}

@Composable
fun OverallHikeStatsCard(viewModel: MainViewModel) {
    val elapsedMins = viewModel.elapsedTimeSeconds / 60.0
    val timeStr = formatHoursMinutes(elapsedMins)
    val distKm = viewModel.distanceTravelledSoFar / 1000.0
    val altitudeM = viewModel.elevationClimbedSoFar
    val totalDist = viewModel.totalDistance
    val completionPct = if (totalDist > 0.0) (viewModel.distanceTravelledSoFar / totalDist * 100.0).coerceIn(0.0, 100.0).toInt() else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Overall Hike Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            // Condensed two-line format:
            // Distance: X.Xkm Altitude: Xm Time: hh:mm % XX%
            Text(
                text = String.format(Locale.US, "Distance: %.1fkm  Altitude: %.0fm  Time: %s  %% %d%%", distKm, altitudeM, timeStr, completionPct),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatSuccinctGrid(x: Double, y: Double): String {
    return CoordinateUtils.formatGridReference6Digit(x, y)
}

fun formatHoursMinutes(totalMinutes: Double): String {
    val totalMinsInt = totalMinutes.toInt()
    val hours = totalMinsInt / 60
    val mins = totalMinsInt % 60
    return String.format(Locale.US, "%02d:%02d", hours, mins)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SeeBeePeeTheme {
        // MainScreen(...)
    }
}
