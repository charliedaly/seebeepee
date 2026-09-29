package com.example.seebeepee.ui

import android.content.Intent
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
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.service.HikingForegroundService
import com.example.seebeepee.ui.theme.SeeBeePeeTheme
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
                    val fileName = uri.lastPathSegment ?: "Imported Route"
                    viewModel.loadRoute(fileName, parsedWaypoints)
                    viewModel.gpsStatus = "Route Loaded: $fileName"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = RouteManager.currentRouteName, maxLines = 1) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Tracking Service Toggle Button
                    IconButton(onClick = {
                        val serviceIntent = Intent(context, HikingForegroundService::class.java)
                        if (isTrackingActive) {
                            serviceIntent.action = HikingForegroundService.ACTION_STOP
                            context.startService(serviceIntent)
                            isTrackingActive = false
                            viewModel.gpsStatus = "GPS Tracking Paused"
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            isTrackingActive = true
                            viewModel.gpsStatus = "GPS Tracking Active (Foreground)"
                        }
                    }) {
                        Icon(
                            imageVector = if (isTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isTrackingActive) "Stop Tracking" else "Start Tracking",
                            tint = if (isTrackingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
            ) {
                // File Picker FAB
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "Open Route File",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                // Settings Toggle FAB
                FloatingActionButton(
                    onClick = { viewModel.showSettings = !viewModel.showSettings },
                    containerColor = if (viewModel.showSettings) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Toggle Settings",
                        tint = if (viewModel.showSettings) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Map Switch FAB
                FloatingActionButton(
                    onClick = onNavigateToMap,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Switch to Map",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                // 1. Top Section: Current Leg Navigation Metrics
                CurrentLegMetricsCard(viewModel = viewModel)

                // 2. Center Section: Embedded Waypoints Table
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
                            Text("Name", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                            Text("Location", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.8f), textAlign = TextAlign.Center)
                            Text("Alt(m)", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                            Text("Brg(°)", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                            Text("Dist(m)", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                            Text("Time(min)", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
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
                                    val east = wp.x % 100000.0
                                    val north = wp.y % 100000.0
                                    String.format(Locale.US, "V%.0f %.0f", east, north)
                                } else {
                                    String.format(Locale.US, "%.3f, %.3f", wp.x, wp.y)
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
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = wp.name,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1.5f),
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = locStr,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1.8f)
                                    )
                                    Text(
                                        text = altStr,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        text = brgStr,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        text = distStr,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                    Text(
                                        text = timeStr,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }

                            // Final row showing total distance and total time required
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total Route",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1.5f)
                                    )
                                    Text(text = "-", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1.8f))
                                    Text(text = "-", fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                                    Text(text = "-", fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                                    Text(
                                        text = String.format(Locale.US, "%.0f", viewModel.totalDistance),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f", viewModel.totalTimeRequired),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Bottom Section: Overall Hike Statistics
                OverallHikeStatsCard(viewModel = viewModel)
            }

            // Settings Overlay Panel
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

                            // Route Load/Generate Buttons in Settings
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Target: ${targetWp?.name ?: "Finished Route"}",
                    style = MaterialTheme.typography.titleMedium,
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
                MetricItem("Distance", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.0f", currentLeg.distanceMeters)} m" else "-")
                MetricItem("Est. Time", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.1f", currentLeg.timeMinutes)} min" else "-")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("Position", viewModel.currentPositionString)
                MetricItem("Elevation", "${String.format(Locale.US, "%.0f", viewModel.currentElevation)} m")
            }
        }
    }
}

@Composable
fun OverallHikeStatsCard(viewModel: MainViewModel) {
    val elapsedMin = viewModel.elapsedTimeSeconds / 60.0
    val remainingMin = viewModel.remainingTime
    val etaMinTotal = viewModel.elapsedTimeSeconds / 60.0 + remainingMin

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Overall Hike Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("Distance Travelled", "${String.format(Locale.US, "%.0f", viewModel.distanceTravelledSoFar)} m")
                MetricItem("Elevation Climbed", "${String.format(Locale.US, "%.0f", viewModel.elevationClimbedSoFar)} m")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("Time Elapsed", "${elapsedMin.toInt()} min")
                MetricItem("Remaining / ETA", "${remainingMin.toInt()} min (${etaMinTotal.toInt()}m total)")
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SeeBeePeeTheme {
        // MainScreen(...)
    }
}
