package com.mojatv.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.SearchResults
import com.mojatv.app.data.toPlayItem
import com.mojatv.app.player.playVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(nav: NavController) {
    val repo = MojaTvApp.repo
    val context = LocalContext.current
    val favorites by repo.storage.favorites.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(300)
        val c = repo.catalog ?: return@LaunchedEffect
        results = withContext(Dispatchers.Default) { repo.search(c, query) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp, top = 8.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Kanały, filmy, seriale…") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Wyczyść") }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }

        val r = results
        when {
            repo.catalog == null -> EmptyState("Playlista jeszcze się wczytuje…")
            query.trim().length < 2 -> EmptyState("Wpisz co najmniej 2 znaki.")
            r == null -> Unit
            r.isEmpty -> EmptyState("Nic nie znaleziono.")
            else -> {
                val favKeys = favorites.map { it.key }.toSet()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (r.live.isNotEmpty()) {
                        item { SectionTitle("Kanały (${r.live.size})") }
                        val liveItems = r.live.map { it.toPlayItem() }
                        itemsIndexed(liveItems) { i, item ->
                            ChannelRow(
                                item, null, null, null, item.key in favKeys,
                                onClick = { context.playVideo(liveItems, i) },
                                onToggleFavorite = { repo.storage.toggleFavorite(item) },
                            )
                        }
                    }
                    if (r.movies.isNotEmpty()) {
                        item { Column {
                            SectionTitle("Filmy (${r.movies.size})")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(r.movies) { m ->
                                    PosterCard(m.name, m.poster, Modifier.width(110.dp)) { nav.openMovie(m.id) }
                                }
                            }
                        } }
                    }
                    if (r.series.isNotEmpty()) {
                        item { Column {
                            SectionTitle("Seriale i show (${r.series.size})")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(r.series) { s ->
                                    PosterCard(s.name, s.poster, Modifier.width(110.dp)) { nav.openSeries(s.id) }
                                }
                            }
                        } }
                    }
                    item { Row(Modifier.fillMaxWidth().padding(24.dp)) {} }
                }
            }
        }
    }
}
