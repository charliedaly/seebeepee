package com.example.seebeepee

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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
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
}
