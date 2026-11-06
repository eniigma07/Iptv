package com.artykulacja.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.artykulacja.app.app
import com.artykulacja.app.ui.theme.AppTheme
import com.artykulacja.app.ui.theme.C

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().background(C.Bg)) { Root() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        app.player.stop()
    }
}

fun enc(s: String): String = Uri.encode(s)
fun dec(s: String?): String = Uri.decode(s ?: "")

@Composable
fun Root() {
    val nav = rememberNavController()
    val start = if (app.settings.onboarded && app.models.isReady()) "main" else "welcome"
    NavHost(nav, startDestination = start) {
        composable("welcome") { WelcomeScreen(nav) }
        composable("download") { DownloadScreen(nav) }
        composable("pickvoice") { VoicesScreen(nav, onboarding = true) }
        composable("placement_intro") { PlacementIntro(nav) }
        composable("results") { PlacementResults(nav) }
        composable("main") { MainTabs(nav) }
        composable("unit/{id}") { UnitScreen(nav, it.arguments?.getString("id") ?: "") }
        composable("drill/{kind}/{arg}") {
            DrillScreen(nav, it.arguments?.getString("kind") ?: "", dec(it.arguments?.getString("arg")))
        }
        composable("pairs/{id}") { PairsScreen(nav, it.arguments?.getString("id") ?: "") }
        composable("pairs_pick") { PairsPickScreen(nav) }
        composable("stress") { StressScreen(nav) }
        composable("phrases") { PhrasesScreen(nav) }
        composable("mywords") { MyWordsScreen(nav) }
        composable("free") { FreeTextScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("voices") { VoicesScreen(nav, onboarding = false) }
    }
}

fun NavHostController.goMain() {
    navigate("main") { popUpTo(0) { inclusive = true } }
}

@Composable
fun MainTabs(nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeScreen(nav) { tab = it }
                1 -> CourseScreen(nav)
                2 -> PracticeHub(nav)
                else -> ProgressScreen(nav)
            }
        }
        HorizontalDivider(color = C.Line)
        NavigationBar(containerColor = C.Bg, tonalElevation = 0.dp) {
            val items = listOf(
                Triple("Dziś", Icons.Rounded.Home, 0),
                Triple("Kurs", Icons.Rounded.School, 1),
                Triple("Trening", Icons.Rounded.GraphicEq, 2),
                Triple("Postępy", Icons.Rounded.AutoGraph, 3),
            )
            for ((label, icon, i) in items) {
                NavigationBarItem(
                    selected = tab == i, onClick = { tab = i },
                    icon = { Icon(icon, label) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = C.Gold, selectedTextColor = C.Gold, indicatorColor = C.GoldSoft,
                        unselectedIconColor = C.Text3, unselectedTextColor = C.Text3,
                    )
                )
            }
        }
    }
}
