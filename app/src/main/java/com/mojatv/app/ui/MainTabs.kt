package com.mojatv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.player.RadioController
import com.mojatv.app.ui.theme.Background
import com.mojatv.app.ui.theme.SurfaceHigh
import com.mojatv.app.ui.theme.TextSecondary

private data class TabDef(val title: String, val icon: ImageVector)

private val tabs = listOf(
    TabDef("Start", Icons.Filled.Home),
    TabDef("TV", Icons.Filled.LiveTv),
    TabDef("Filmy", Icons.Filled.Movie),
    TabDef("Seriale", Icons.Filled.VideoLibrary),
    TabDef("Show", Icons.Filled.TheaterComedy),
    TabDef("Radio", Icons.Filled.Radio),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabs(nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val epgStatus by MojaTvApp.repo.epgStatus.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (tab == 0) "MojaTV" else tabs[tab].title, fontWeight = FontWeight.Bold)
                        if (tab == 1 && epgStatus != null) {
                            Text(epgStatus ?: "", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Filled.Search, "Szukaj") }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "Ustawienia") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        bottomBar = {
            Column {
                RadioMiniPlayer()
                NavigationBar(containerColor = SurfaceHigh) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, t.title) },
                            label = { Text(t.title, fontSize = 10.sp, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val openSettings = { nav.navigate(Routes.SETTINGS) }
            when (tab) {
                0 -> HomeScreen(nav, onOpenTab = { tab = it })
                1 -> CatalogGate(openSettings) { LiveScreen(it) }
                2 -> CatalogGate(openSettings) { MoviesScreen(it, nav) }
                3 -> CatalogGate(openSettings) { SeriesScreen(it, nav) }
                4 -> CatalogGate(openSettings) { ShowScreen(it, nav) }
                5 -> RadioScreen()
            }
        }
    }
}

@Composable
fun RadioMiniPlayer() {
    val state by RadioController.state.collectAsState()
    val s = state ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))) {
            NetImage(s.item.image, s.item.title, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(s.item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = s.error ?: s.streamTitle ?: if (s.isBuffering) "Łączenie…" else "Radio"
            Text(line, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (s.isBuffering) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { RadioController.togglePause() }) {
                Icon(if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Odtwarzaj/pauza")
            }
        }
        IconButton(onClick = { RadioController.stop() }) { Icon(Icons.Filled.Close, "Zatrzymaj") }
    }
}

/** Pomocnik: kliknięcie w element z obsługą ripple (używany w listach). */
fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
