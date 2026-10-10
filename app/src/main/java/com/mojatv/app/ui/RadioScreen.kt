package com.mojatv.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.ContentKind
import com.mojatv.app.data.LoadState
import com.mojatv.app.data.PlayItem
import com.mojatv.app.data.RadioBrowserApi
import com.mojatv.app.data.RadioStation
import com.mojatv.app.data.toPlayItem
import com.mojatv.app.player.RadioController
import com.mojatv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private val radioCountries = listOf(
    "PL" to "Polska",
    "OM" to "Oman",
    "EG" to "Egipt",
    "AE" to "ZEA",
    "GB" to "Wlk. Brytania",
    "US" to "USA",
    "DE" to "Niemcy",
)

@Composable
fun RadioScreen() {
    val repo = MojaTvApp.repo
    val context = LocalContext.current
    val favorites by repo.storage.favorites.collectAsState()
    val catalogState by repo.state.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(repo.storage.radioCountry) }
    var attempt by remember { mutableIntStateOf(0) }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query, country, attempt) {
        val q = query.trim()
        if (q.isNotEmpty()) delay(500)
        loading = true
        error = null
        try {
            stations = if (q.length >= 2) RadioBrowserApi.search(q) else RadioBrowserApi.topByCountry(country)
        } catch (e: Exception) {
            error = "Nie udało się pobrać listy stacji."
        }
        loading = false
    }

    val favRadio = favorites.filter { it.kind == ContentKind.RADIO }
    val favKeys = favorites.map { it.key }.toSet()
    val playlistRadio = (catalogState as? LoadState.Ready)?.catalog?.radioChannels.orEmpty()
        .map { it.toPlayItem().copy(kind = ContentKind.RADIO, key = "radio:pl-${it.id}") }
    val searching = query.trim().length >= 2

    fun play(item: PlayItem) = RadioController.play(context, item)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Szukaj stacji na całym świecie") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Wyczyść") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (!searching) {
            item {
                CategoryChips(radioCountries, country) {
                    country = it
                    repo.storage.radioCountry = it
                }
            }
            if (favRadio.isNotEmpty()) {
                item { SectionTitle("Ulubione") }
                items(favRadio) { st ->
                    ChannelRow(st, null, null, null, true, onClick = { play(st) }, onToggleFavorite = { repo.storage.toggleFavorite(st) })
                }
            }
            if (playlistRadio.isNotEmpty()) {
                item { SectionTitle("Z Twojej playlisty") }
                items(playlistRadio) { st ->
                    ChannelRow(st, null, null, null, st.key in favKeys, onClick = { play(st) }, onToggleFavorite = { repo.storage.toggleFavorite(st) })
                }
            }
        }
        item {
            SectionTitle(
                if (searching) "Wyniki wyszukiwania"
                else "Popularne — ${radioCountries.firstOrNull { it.first == country }?.second ?: country}"
            )
        }
        when {
            loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
            error != null -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { attempt++ }) { Text("${error ?: ""} Spróbuj ponownie") }
                }
            }
            stations.isEmpty() -> item {
                Text("Brak stacji.", color = TextSecondary, modifier = Modifier.padding(16.dp))
            }
            else -> items(stations) { s ->
                val st = s.toPlayItem()
                ChannelRow(st, null, null, null, st.key in favKeys, onClick = { play(st) }, onToggleFavorite = { repo.storage.toggleFavorite(st) })
            }
        }
    }
}
