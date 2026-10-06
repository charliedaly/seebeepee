package com.example.seebeepee.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.seebeepee.model.HikeState
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.service.HikingForegroundService
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

    var isTrackingActive by remember { mutableStateOf(viewModel.isHikeActive || RouteManager.hikeState == HikeState.HIKING) }
    LaunchedEffect(viewModel.isHikeActive, RouteManager.hikeState) {
        isTrackingActive = viewModel.isHikeActive || RouteManager.hikeState == HikeState.HIKING
    }
    var showSaveGpxDialog by remember { mutableStateOf(false) }
    var showRoutePickerModal by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val fileName = RouteParser.extractRouteNameFromUri(context, uri)
                val isGpx = uri.toString().endsWith(".gpx", true) || content.contains("<gpx", true)
                val parsedWaypoints = if (isGpx) {
                    RouteParser.parseGpx(content)
                } else {
                    RouteParser.parseCsv(content)
                }

                if (parsedWaypoints.isNotEmpty()) {
                    val filePath = uri.path ?: uri.toString()
                    val fileUri = uri.toString()
                    viewModel.loadRoute(fileName, parsedWaypoints, content = content, filePath = filePath, fileUri = fileUri)
                }
            } catch (e: Exception) {
                RouteManager.logError("Error opening route file: ${e.message}")
            }
        }
    }

    LaunchedEffect(currentIndex) {
        if (waypoints.isNotEmpty()) {
            val scrollTarget = (currentIndex + 1).coerceAtMost(waypoints.size - 1)
            coroutineScope.launch {
                listState.animateScrollToItem(scrollTarget)
            }
        }
    }

    if (showSaveGpxDialog) {
        AlertDialog(
            onDismissRequest = { showSaveGpxDialog = false },
            title = { Text("Save Breadcrumbs as GPX") },
            text = { Text("Do you want to save your hike breadcrumbs as a GPX track before stopping?") },
            confirmButton = {
                TextButton(onClick = {
                    val savedFile = viewModel.finishHike(save = true, context = context)
                    showSaveGpxDialog = false
                    if (savedFile != null) {
                        val folderName = if (savedFile.parentFile?.name.equals("Download", true)) "Downloads" else (savedFile.parentFile?.name ?: "Downloads")
                        viewModel.gpsStatus = "Saved to $folderName: ${savedFile.name}"
                    } else {
                        viewModel.gpsStatus = "Breadcrumbs saved to GPX"
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.finishHike(save = false, context = context)
                    showSaveGpxDialog = false
                    viewModel.gpsStatus = "Breadcrumbs discarded"
                }) {
                    Text("Discard")
                }
            }
        )
    }

    if (showRoutePickerModal) {
        AlertDialog(
            onDismissRequest = { showRoutePickerModal = false },
            title = { Text("Select Route") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val sampleCsv = RouteParser.generateSampleIrishGridCsv()
                            val sampleWps = RouteParser.parseCsv(sampleCsv)
                            viewModel.loadRoute("Sample Hike (Irish Grid)", sampleWps, content = sampleCsv)
                            showRoutePickerModal = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sample Hike (Irish Grid)")
                    }
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                            showRoutePickerModal = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Import GPX / Route File...")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoutePickerModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val hasLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
    val isGpsEnabled = remember {
        mutableStateOf(
            locationManager?.let {
                it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER)
            } ?: false
        )
    }

    LaunchedEffect(Unit) {
        hasLocationPermission.value = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        isGpsEnabled.value = locationManager?.let {
            it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER)
        } ?: false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission.value = fine || coarse
        if (fine || coarse) {
            viewModel.gpsStatus = "Location Permission Granted"
        } else {
            viewModel.gpsStatus = "Location Permission Denied"
            RouteManager.logError("Location permission denied by user.")
        }
    }

    var showGpsDisabledDialog by remember { mutableStateOf(false) }

    if (showGpsDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDisabledDialog = false },
            title = { Text("GPS is Disabled") },
            text = { Text("GPS / Location services are disabled on your device. Please enable GPS in settings for accurate hike tracking.") },
            confirmButton = {
                TextButton(onClick = {
                    showGpsDisabledDialog = false
                    try {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (e: Exception) {
                        RouteManager.logError("Error opening location settings: ${e.message}")
                    }
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsDisabledDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    var showOutsideMapConfirmation by remember { mutableStateOf(false) }

    val startTrackingAction = {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission.value = fine || coarse

        val gpsOn = locationManager?.let {
            it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER)
        } ?: false
        isGpsEnabled.value = gpsOn

        if (!hasLocationPermission.value) {
            viewModel.gpsStatus = "Permission Required"
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else if (!gpsOn) {
            viewModel.gpsStatus = "GPS Disabled"
            showGpsDisabledDialog = true
        } else {
            viewModel.startHike()
            val serviceIntent = Intent(context, HikingForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                isTrackingActive = true
                viewModel.gpsStatus = "GPS Tracking Active"
            } catch (e: Exception) {
                isTrackingActive = false
                viewModel.gpsStatus = "Tracking Failed"
            }
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
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ACTIVE ROUTE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                onClick = { showRoutePickerModal = true },
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Change", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Change Route", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }

                    val routeTitle = RouteManager.currentRouteName.substringBeforeLast(".")
                    Text(
                        text = if (routeTitle.equals("No Route Loaded", true)) "No Route Loaded" else routeTitle,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (waypoints.isNotEmpty()) {
                        val totalDistKm = viewModel.totalDistance / 1000.0
                        val totalClimbM = viewModel.totalClimb
                        val totalTimeStr = formatHoursMinutes(viewModel.totalTimeRequired)
                        Text(
                            text = String.format(Locale.US, "Distance %.1fkm • Altitude %.0fm • Time %s", totalDistKm, totalClimbM, totalTimeStr),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
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
                    Button(
                        onClick = {
                            if (isTrackingActive) {
                                viewModel.stopHike()
                                val serviceIntent = Intent(context, HikingForegroundService::class.java).apply {
                                    action = HikingForegroundService.ACTION_STOP
                                }
                                context.startService(serviceIntent)
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
                // 1. Redesigned Streamlined Current Leg / GPS Status Card
                CurrentLegMetricsCard(viewModel = viewModel)

                // 2. Waypoint Table Card
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
                                    CoordinateUtils.formatGridReferenceWithLetter(wp.x, wp.y, viewModel.gridReferencePrecision)
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

                // 3. Redesigned Streamlined Overall Hike Statistics Card
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
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.85f)
                            .clickable(enabled = false) {},
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Hike & Navigation Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Column {
                                Text(text = "Flat Pace: ${viewModel.flatPace.toInt()} min/km", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.flatPace.toFloat(),
                                    onValueChange = { viewModel.updateFlatPace(it.toDouble()) },
                                    valueRange = 10f..40f,
                                    steps = 30
                                )
                            }

                            Column {
                                Text(text = "Climb Penalty: ${String.format(Locale.US, "%.1f", viewModel.climbPenalty)} min / 10m", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.climbPenalty.toFloat(),
                                    onValueChange = { viewModel.updateClimbPenalty(it.toDouble()) },
                                    valueRange = 0.5f..3.0f,
                                    steps = 25
                                )
                            }

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

                            if (viewModel.coordinateSystem == "grid") {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = "Grid Reference Precision:", fontSize = 13.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(6, 8, 10).forEach { prec ->
                                            Button(
                                                onClick = { viewModel.updateGridReferencePrecision(prec) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (viewModel.gridReferencePrecision == prec) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("$prec Digits", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

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

                            Column {
                                Text(text = "Off-Course Cone Angle: ${viewModel.coneAngle.toInt()}°", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.coneAngle,
                                    onValueChange = { viewModel.updateConeAngle(it) },
                                    valueRange = 5f..30f,
                                    steps = 25
                                )
                            }

                            Column {
                                Text(text = "Loopback Distance (D_loopback): ${viewModel.loopbackDistance.toInt()}m", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.loopbackDistance.toFloat(),
                                    onValueChange = { viewModel.updateLoopbackDistance(it.toDouble()) },
                                    valueRange = 2f..40f,
                                    steps = 39 // 40 -2 + 1
                                )
                            }

                            Column {
                                Text(text = "Low-Speed Cutoff: ${String.format(Locale.US, "%.1f", viewModel.lowSpeedCutoff)} km/h", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.lowSpeedCutoff.toFloat(),
                                    onValueChange = { viewModel.updateLowSpeedCutoff(it.toDouble()) },
                                    valueRange = 0.5f..3.0f,
                                    steps = 25
                                )
                            }

                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Heading Smoothing Alpha: ${String.format("%.2f", viewModel.headingSmoothingAlpha)}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = viewModel.headingSmoothingAlpha.toFloat(),
                                    onValueChange = { viewModel.updateHeadingSmoothingAlpha(it) },
                                    valueRange = 0.05f..1.0f
                                )
                                Text(
                                    text = "Lower values smooth compass jitter more; higher values make turns react faster.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column {
                                Text(text = "Off-Course Persistence: ${viewModel.persistenceFixes} fixes", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.persistenceFixes.toFloat(),
                                    onValueChange = { viewModel.updatePersistenceFixes(it.toInt()) },
                                    valueRange = 1f..5f,
                                    steps = 3
                                )
                            }

                            Column {
                                Text(text = "Waypoint Arrival Threshold: ${viewModel.arrivalThreshold.toInt()}m", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.arrivalThreshold.toFloat(),
                                    onValueChange = { viewModel.updateArrivalThreshold(it.toDouble()) },
                                    valueRange = 5f..50f,
                                    steps = 45
                                )
                            }

                            Column {
                                Text(text = "Near-Miss Threshold: ${viewModel.nearMissThreshold.toInt()}m", fontSize = 13.sp)
                                Slider(
                                    value = viewModel.nearMissThreshold.toFloat(),
                                    onValueChange = { viewModel.updateNearMissThreshold(it.toDouble()) },
                                    valueRange = 20f..100f,
                                    steps = 80
                                )
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
        CoordinateUtils.formatGridReferenceWithLetter(x, y, viewModel.gridReferencePrecision)
    } else {
        viewModel.currentPositionString
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPS: $posStr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = viewModel.gpsStatus,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("Bearing", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.0f", currentLeg.bearing)}°" else "-")
                StatColumn("Distance", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.0f", currentLeg.distanceMeters)}m" else "-")
                StatColumn("Time", if (currentLeg != null && targetWp != null) "${String.format(Locale.US, "%.1f", currentLeg.timeMinutes)}m" else "-")
                StatColumn("Target", targetWp?.name ?: "Finished")
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Overall Hike Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("Distance", String.format(Locale.US, "%.1fkm", distKm), isSecondary = true)
                StatColumn("Altitude", String.format(Locale.US, "%.0fm", altitudeM), isSecondary = true)
                StatColumn("Time", timeStr, isSecondary = true)
                StatColumn("Complete", "$completionPct%", isSecondary = true)
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String, isSecondary: Boolean = false) {
    val labelColor = if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    val valueColor = if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, fontSize = 11.sp, color = labelColor)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

fun formatHoursMinutes(totalMinutes: Double): String {
    val totalMinsInt = totalMinutes.toInt()
    val hours = totalMinsInt / 60
    val mins = totalMinsInt % 60
    return String.format(Locale.US, "%02d:%02d", hours, mins)
}