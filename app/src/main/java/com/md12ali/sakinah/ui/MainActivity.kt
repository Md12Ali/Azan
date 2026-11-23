package com.md12ali.sakinah.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.md12ali.sakinah.ui.components.PatternBackground
import com.md12ali.sakinah.ui.prayer.LocationScreen
import com.md12ali.sakinah.ui.prayer.PrayerDashboardScreen
import com.md12ali.sakinah.ui.prayer.PrayerViewModel
import com.md12ali.sakinah.ui.settings.AdhanSettingsScreen
import com.md12ali.sakinah.ui.theme.SakinahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SakinahTheme { AppRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("prayer", "Prayer", Icons.Outlined.AccessTime),
    Tab("location", "Mosque", Icons.Outlined.LocationOn),
    Tab("settings", "Adhan", Icons.Outlined.Tune)
)

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val vm: PrayerViewModel = viewModel()   // one shared ViewModel for all three screens
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route, onClick = { go(t.route) },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        PatternBackground(Modifier.padding(padding)) {
            NavHost(nav, startDestination = "prayer") {
                composable("prayer") { PrayerDashboardScreen(vm, onOpenLocation = { go("location") }, onOpenAdhanSettings = { go("settings") }) }
                composable("location") { LocationScreen(vm, onDone = { go("prayer") }) }
                composable("settings") { AdhanSettingsScreen(vm) }
            }
        }
    }
}
