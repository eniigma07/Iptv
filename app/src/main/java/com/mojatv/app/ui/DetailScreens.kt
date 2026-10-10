package com.mojatv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.Episode
import com.mojatv.app.data.MovieDetail
import com.mojatv.app.data.PlayItem
import com.mojatv.app.data.SeriesDetail
import com.mojatv.app.data.toPlayItem
import com.mojatv.app.player.openExternal
import com.mojatv.app.player.playVideo
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.Background
import com.mojatv.app.ui.theme.SurfaceHigh
import com.mojatv.app.ui.theme.TextSecondary

// ---------------- Wspólny nagłówek ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(
    title: String,
    poster: String?,
    backdrop: String?,
    meta: List<String>,
    onBack: () -> Unit,
) = Column {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            NetImage(backdrop ?: poster, title, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Background))
                )
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).background(Color(0x66000000), CircleShape),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz", tint = Color.White)
        }
    }
    Row(Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp)) {
        Box(
            Modifier
                .width(110.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            NetImage(poster, title, Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 16.dp, top = 52.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                meta.forEach { Text(it, color = TextSecondary, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, fontSize = 14.sp)
    }
}

@Composable
private fun FavoriteButton(item: PlayItem) {
    val favorites by MojaTvApp.repo.storage.favorites.collectAsState()
    val isFav = favorites.any { it.key == item.key }
    IconButton(onClick = { MojaTvApp.repo.storage.toggleFavorite(item) }) {
        Icon(
            if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
            "Ulubione",
            tint = if (isFav) Accent else TextSecondary,
        )
    }
}

// ---------------- Film ----------------

@Composable
fun MovieDetailScreen(id: String, nav: NavController) {
    val repo = MojaTvApp.repo
    val context = LocalContext.current
    val movie = remember(id) { repo.findMovie(id) }
    val progressMap by repo.storage.progress.collectAsState()

    if (movie == null) {
        EmptyState("Tego filmu nie ma już w playliście.") {
            OutlinedButton(onClick = { nav.popBackStack() }) { Text("Wróć") }
        }
        return
    }

    var detail by remember { mutableStateOf<MovieDetail?>(null) }
    LaunchedEffect(movie) { detail = repo.movieDetail(movie) }

    val item = movie.toPlayItem()
    val progress = progressMap[item.key]?.takeIf { !it.finished && it.position > 30_000 }
    val d = detail

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                title = movie.name,
                poster = movie.poster,
                backdrop = d?.backdrop,
                meta = listOfNotNull(
                    d?.year,
                    d?.duration,
                    (d?.rating ?: movie.rating)?.let { formatRating(it) },
                ),
                onBack = { nav.popBackStack() },
            )
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).offset(y = (-32).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { context.playVideo(item) }) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text(if (progress != null) "Wznów ${formatClock(progress.position)}" else "Odtwórz")
                }
                if (progress != null) {
                    OutlinedButton(onClick = { context.playVideo(item, startPosition = 0) }) {
                        Icon(Icons.Filled.Replay, null)
                        Text("Od początku")
                    }
                }
                FavoriteButton(item)
                IconButton(onClick = { context.openExternal(item) }) {
                    Icon(Icons.Filled.OpenInNew, "Zewnętrzny odtwarzacz", tint = TextSecondary)
                }
            }
        }
        if (progress != null) {
            item {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-24).dp),
                    color = Accent,
                    trackColor = SurfaceHigh,
                )
            }
        }
        if (d == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        } else {
            item {
                Column {
                    if (!d.plot.isNullOrBlank()) {
                        Text(d.plot, modifier = Modifier.padding(horizontal = 16.dp), lineHeight = 21.sp)
                    }
                    InfoBlock("Gatunek", d.genre)
                    InfoBlock("Reżyseria", d.director)
                    InfoBlock("Obsada", d.cast)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

fun formatClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ---------------- Serial ----------------

@Composable
fun SeriesDetailScreen(id: String, nav: NavController) {
    val repo = MojaTvApp.repo
    val context = LocalContext.current
    val series = remember(id) { repo.findSeries(id) }
    val progressMap by repo.storage.progress.collectAsState()

    if (series == null) {
        EmptyState("Tego serialu nie ma już w playliście.") {
            OutlinedButton(onClick = { nav.popBackStack() }) { Text("Wróć") }
        }
        return
    }

    var detail by remember { mutableStateOf<SeriesDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(series, attempt) {
        error = null
        try {
            detail = repo.seriesDetail(series)
        } catch (e: Exception) {
            error = "Nie udało się pobrać odcinków."
        }
    }

    val d = detail
    // ostatnio oglądany odcinek tego serialu
    val lastWatched = progressMap.values
        .filter { it.item.parentId == series.id }
        .maxByOrNull { it.updatedAt }
    val allEpisodes = d?.seasons?.values?.flatten().orEmpty()
    val lastEpisode = lastWatched?.let { lw -> allEpisodes.firstOrNull { "episode:${it.id}" == lw.item.key } }

    var season by rememberSaveable { mutableIntStateOf(-1) }
    val seasons = d?.seasons?.keys?.toList().orEmpty()
    if (season == -1 && seasons.isNotEmpty()) season = lastEpisode?.season ?: seasons.first()
    val episodes = d?.seasons?.get(season).orEmpty()

    fun playEpisode(list: List<Episode>, index: Int, from: Long = -1) {
        context.playVideo(list.map { it.toPlayItem(series) }, index, from)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                title = series.name,
                poster = series.poster,
                backdrop = d?.backdrop,
                meta = listOfNotNull(
                    d?.year ?: series.year,
                    if (seasons.isNotEmpty()) "Sezony: ${seasons.size}" else null,
                    series.rating?.let { formatRating(it) },
                ),
                onBack = { nav.popBackStack() },
            )
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).offset(y = (-32).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = allEpisodes.isNotEmpty(),
                    onClick = {
                        val target = if (lastEpisode != null && lastWatched?.finished == false) lastEpisode
                        else if (lastEpisode != null) {
                            // poprzedni skończony — następny odcinek
                            val list = d?.seasons?.get(lastEpisode.season).orEmpty()
                            val idx = list.indexOf(lastEpisode)
                            list.getOrNull(idx + 1) ?: d?.seasons?.get(lastEpisode.season + 1)?.firstOrNull() ?: lastEpisode
                        } else allEpisodes.firstOrNull()
                        if (target != null) {
                            val list = d?.seasons?.get(target.season).orEmpty()
                            playEpisode(list, list.indexOf(target).coerceAtLeast(0))
                        }
                    },
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text(
                        when {
                            lastEpisode == null -> "Odtwórz"
                            else -> "Kontynuuj S${lastEpisode.season}E${lastEpisode.number}"
                        }
                    )
                }
                FavoriteButton(series.toPlayItem())
            }
        }
        if (d != null) {
            item {
                Column {
                    if (!d.plot.isNullOrBlank()) {
                        Text(
                            d.plot, modifier = Modifier.padding(horizontal = 16.dp),
                            maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp,
                        )
                    }
                    InfoBlock("Gatunek", d.genre)
                    InfoBlock("Obsada", d.cast)
                }
            }
        }
        when {
            error != null -> item {
                EmptyState(error ?: "") {
                    Button(onClick = { attempt++ }) { Text("Spróbuj ponownie") }
                }
            }
            d == null -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
            seasons.isEmpty() -> item { EmptyState("Brak odcinków.") }
            else -> {
                if (seasons.size > 1) {
                    item {
                        ScrollableTabRow(
                            selectedTabIndex = seasons.indexOf(season).coerceAtLeast(0),
                            edgePadding = 16.dp,
                            containerColor = Background,
                        ) {
                            seasons.forEach { s ->
                                Tab(selected = s == season, onClick = { season = s }, text = { Text("Sezon $s") })
                            }
                        }
                    }
                }
                itemsIndexed(episodes) { i, ep ->
                    val p = progressMap["episode:${ep.id}"]
                    EpisodeRow(ep, p?.fraction?.takeIf { it > 0f }) { playEpisode(episodes, i) }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, progress: Float?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .width(130.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceHigh)
        ) {
            NetImage(ep.image, ep.number.toString(), Modifier.fillMaxSize())
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Accent,
                    trackColor = Color(0x66000000),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${ep.number}. ${ep.title}", fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ep.duration != null) Text(ep.duration, color = TextSecondary, fontSize = 12.sp)
            if (ep.plot != null) {
                Text(ep.plot, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
