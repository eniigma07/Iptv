package com.mojatv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.ContentKind
import com.mojatv.app.player.RadioController
import com.mojatv.app.player.playVideo
import com.mojatv.app.ui.theme.TextSecondary

@Composable
fun HomeScreen(nav: NavController, onOpenTab: (Int) -> Unit) {
    val repo = MojaTvApp.repo
    val context = LocalContext.current
    val favorites by repo.storage.favorites.collectAsState()
    val recent by repo.storage.recent.collectAsState()
    val progress by repo.storage.progress.collectAsState()
    val epg by repo.epg.collectAsState()

    val continueWatching = progress.values
        .filter { !it.finished && it.position > 30_000 }
        .sortedByDescending { it.updatedAt }
        .take(20)
    val favChannels = favorites.filter { it.kind == ContentKind.LIVE }
    val favVod = favorites.filter { it.kind == ContentKind.MOVIE || it.kind == ContentKind.SERIES }
    val favRadio = favorites.filter { it.kind == ContentKind.RADIO }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        HorizontalSection("Kontynuuj oglądanie", continueWatching) { _, p ->
            WideCard(
                title = p.item.title,
                subtitle = p.item.subtitle ?: "Pozostało ${formatDuration(p.duration - p.position)}",
                image = p.item.image,
                progress = p.fraction,
                onClick = { context.playVideo(p.item) },
            )
        }

        HorizontalSection("Ulubione kanały", favChannels) { i, item ->
            val (now, _) = repo.nowAndNext(item.epgId)
            WideCard(
                title = item.title,
                subtitle = now?.title,
                image = item.image,
                width = 150.dp,
                fitImage = true,
                onClick = { context.playVideo(favChannels, i) },
            )
        }

        HorizontalSection("Ostatnio oglądane kanały", recent) { i, item ->
            val (now, _) = repo.nowAndNext(item.epgId)
            WideCard(
                title = item.title,
                subtitle = now?.title,
                image = item.image,
                width = 150.dp,
                fitImage = true,
                onClick = {
                    if (item.kind == ContentKind.RADIO) RadioController.play(context, item)
                    else context.playVideo(recent, i)
                },
            )
        }

        HorizontalSection("Ulubione filmy i seriale", favVod) { _, item ->
            Column(Modifier.width(110.dp)) {
                PosterCard(item.title, item.image) {
                    val id = item.key.substringAfter(':')
                    if (item.kind == ContentKind.MOVIE) nav.openMovie(id) else nav.openSeries(id)
                }
            }
        }

        HorizontalSection("Ulubione stacje radiowe", favRadio) { _, item ->
            WideCard(
                title = item.title,
                subtitle = item.subtitle,
                image = item.image,
                width = 130.dp,
                fitImage = true,
                onClick = { RadioController.play(context, item) },
            )
        }

        if (continueWatching.isEmpty() && favorites.isEmpty() && recent.isEmpty()) {
            Spacer(Modifier.height(48.dp))
            Text(
                "Tu pojawią się Twoje ulubione kanały, ostatnio oglądane i „kontynuuj oglądanie”.\n" +
                    "Gwiazdką dodajesz do ulubionych.",
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenTab(1) }) { Text("Telewizja") }
                OutlinedButton(onClick = { onOpenTab(2) }) { Text("Filmy") }
                OutlinedButton(onClick = { onOpenTab(3) }) { Text("Seriale") }
            }
        }
        // odświeżenie EPG powoduje ponowne narysowanie kart kanałów
        if (epg.isEmpty()) Unit
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h godz. $m min" else "$m min"
}
