package com.example.seebeepee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

            if (!hasFine && !hasCoarse) {
                RouteManager.logError("SecurityException check: Location permissions (Fine/Coarse) are missing in MainActivity.")
                if (startService) {
                    RouteManager.logError("Cannot start foreground location service: Location permissions are missing.")
                }
            } else {
                RouteManager.logError("Location permissions verified successfully in MainActivity.")
                if (startService) {
                    startHikingForegroundServiceSafely()
                }
            }
        } catch (e: SecurityException) {
            RouteManager.logError("SecurityException while checking location permissions in MainActivity: ${e.message}")
        } catch (e: Exception) {
            RouteManager.logError("Error while checking location permissions in MainActivity: ${e.message}")
        }
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
}
