package com.mojatv.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.ui.theme.MojaTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // porządek w nagraniach (np. po wyłączeniu telefonu w trakcie nagrywania)
        runCatching { com.mojatv.app.rec.RecordingScheduler.rescheduleAll(this) }
        setContent {
            MojaTvTheme {
                // Surface ustawia jasny kolor tekstu na ciemnym tle na wszystkich ekranach
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

object Routes {
    const val MAIN = "main"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SHOW_SETTINGS = "show-settings"
    const val RECORDINGS = "recordings"
    const val EPG_TIME = "epg-time"
    fun movie(id: String) = "movie/${Uri.encode(id)}"
    fun series(id: String) = "series/${Uri.encode(id)}"
}

fun NavController.openMovie(id: String) = navigate(Routes.movie(id))
fun NavController.openSeries(id: String) = navigate(Routes.series(id))

@Composable
fun AppRoot() {
    val repo = MojaTvApp.repo
    var signedIn by remember { mutableStateOf(repo.storage.source != null) }

    if (!signedIn) {
        LoginScreen(onDone = { signedIn = true })
        return
    }

    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable(Routes.MAIN) { MainTabs(nav) }
        composable("movie/{id}") { entry ->
            MovieDetailScreen(entry.arguments?.getString("id").orEmpty(), nav)
        }
        composable("series/{id}") { entry ->
            SeriesDetailScreen(entry.arguments?.getString("id").orEmpty(), nav)
        }
        composable(Routes.SEARCH) { SearchScreen(nav) }
        composable(Routes.SETTINGS) {
            SettingsScreen(nav, onSignOut = {
                repo.signOut()
                signedIn = false
            })
        }
        composable(Routes.SHOW_SETTINGS) { ShowSettingsScreen(nav) }
        composable(Routes.RECORDINGS) { RecordingsScreen(nav) }
        composable(Routes.EPG_TIME) { EpgTimeScreen(nav) }
    }
}
