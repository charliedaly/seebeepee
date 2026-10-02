package com.example.seebeepee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.seebeepee.model.RouteManager
import com.example.seebeepee.service.HikingForegroundService
import com.example.seebeepee.ui.MainScreen
import com.example.seebeepee.ui.MapScreen
import com.example.seebeepee.ui.theme.SeeBeePeeTheme
import com.example.seebeepee.util.TtsManager
import com.example.seebeepee.viewmodel.MainViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppScreen {
    @Serializable
    data object Main : AppScreen
    @Serializable
    data object Map : AppScreen
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize TtsManager for independent startup test
        ttsManager = TtsManager(applicationContext)

        // Give the TTS engine a tiny window to bind, then speak a verification phrase
        window.decorView.postDelayed({
            ttsManager.speak("TTS engine initialized and ready.")
        }, 1500)

        checkLocationPermissionsAndStartTracking()

        setContent {
            SeeBeePeeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val backStack = remember { mutableStateListOf<Any>(AppScreen.Main) }
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else finish() },
                        entryProvider = { key ->
                            when (key) {
                                is AppScreen.Main -> NavEntry(key) {
                                    MainScreen(
                                        viewModel = viewModel,
                                        onNavigateToMap = { backStack.add(AppScreen.Map) }
                                    )
                                }
                                is AppScreen.Map -> NavEntry(key) {
                                    MapScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
                                    )
                                }
                                else -> error("Unknown route: $key")
                            }
                        }
                    )
                }
            }
        }
    }

    fun checkLocationPermissionsAndStartTracking(startService: Boolean = false) {
        try {
            val hasFine = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
            val isGpsOn = locationManager?.let {
                it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
                )
            } ?: false

            if (!hasFine && !hasCoarse || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotification)) {
                RouteManager.logError("Permissions check: Location or Notification permissions are missing in MainActivity. Requesting permissions.")
                requestPermissionsIfNeeded()
                if (startService) {
                    RouteManager.logError("Cannot start foreground location service: Required permissions are missing.")
                }
            } else if (!isGpsOn) {
                RouteManager.logError("Warning: Permissions verified, but GPS/Location provider is disabled in MainActivity.")
                if (startService) {
                    RouteManager.logError("Cannot start foreground location service: GPS is disabled.")
                }
            } else {
                RouteManager.logError("Permissions and GPS verified successfully in MainActivity.")
                if (startService) {
                    startHikingForegroundServiceSafely()
                }
            }
        } catch (e: SecurityException) {
            RouteManager.logError("SecurityException while checking permissions in MainActivity: ${e.message}")
        } catch (e: Exception) {
            RouteManager.logError("Error while checking permissions in MainActivity: ${e.message}")
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1001)
    }

    fun startHikingForegroundServiceSafely() {
        try {
            val hasFine = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                RouteManager.logError("Permission check failed: Missing location permission before starting foreground service from MainActivity.")
                return
            }

            val serviceIntent = Intent(this, HikingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            RouteManager.logError("HikingForegroundService started safely from MainActivity.")
        } catch (e: SecurityException) {
            RouteManager.logError("SecurityException caught when starting foreground service from MainActivity: ${e.message}")
        } catch (e: Exception) {
            RouteManager.logError("Exception caught when starting foreground service from MainActivity: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}