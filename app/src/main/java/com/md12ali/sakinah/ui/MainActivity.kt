package com.md12ali.sakinah.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.md12ali.sakinah.ui.components.FloatingNavBar
import com.md12ali.sakinah.ui.components.NavTab
import com.md12ali.sakinah.ui.prayer.LocationScreen
import com.md12ali.sakinah.ui.prayer.PrayerDashboardScreen
import com.md12ali.sakinah.ui.prayer.PrayerViewModel
import com.md12ali.sakinah.ui.qibla.QiblaScreen
import com.md12ali.sakinah.ui.settings.AdhanSettingsScreen
import com.md12ali.sakinah.ui.theme.SakinahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SakinahTheme { AppRoot() } }
    }
}

private val tabs = listOf(
    NavTab("prayer", "Prayer", Icons.Outlined.AccessTime),
    NavTab("qibla", "Qibla", Icons.Outlined.Explore),
    NavTab("mosques", "Mosques", Icons.Outlined.Mosque),
    NavTab("location", "Location", Icons.Outlined.Place),
    NavTab("settings", "Azan", Icons.Outlined.Tune)
)

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val vm: PrayerViewModel = viewModel()   // one shared ViewModel for all screens
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Light status-bar icons over the dark sky (Prayer) and Qibla screens, dark icons on light screens.
    val view = androidx.compose.ui.platform.LocalView.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    androidx.compose.runtime.SideEffect {
        val window = (view.context as android.app.Activity).window
        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            !darkTheme && route != "prayer" && route != "qibla" && route != null
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            nav, startDestination = "prayer",
            enterTransition = { fadeIn() }, exitTransition = { fadeOut() }
        ) {
            composable("prayer") { PrayerDashboardScreen(vm, onOpenLocation = { go("location") }, onOpenAdhanSettings = { go("settings") }) }
            composable("qibla") { QiblaScreen(vm) }
            composable("mosques") { com.md12ali.sakinah.ui.prayer.MosquesScreen(vm) }
            composable("location") { LocationScreen(vm, onDone = { go("prayer") }) }
            composable("settings") { AdhanSettingsScreen(vm) }
        }
        FloatingNavBar(tabs, route, onSelect = { go(it) }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
