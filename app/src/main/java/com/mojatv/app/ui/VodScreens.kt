package com.mojatv.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.Catalog
import com.mojatv.app.data.ContentKind
import com.mojatv.app.data.Movie
import com.mojatv.app.data.Series

/** Element siatki: film albo serial. */
sealed interface MediaEntry {
    val title: String
    val image: String?
    val key: String

    data class M(val movie: Movie) : MediaEntry {
        override val title get() = movie.name
        override val image get() = movie.poster
        override val key get() = "movie:${movie.id}"
    }

    data class S(val series: Series) : MediaEntry {
        override val title get() = series.name
        override val image get() = series.poster
        override val key get() = "series:${series.id}"
    }
}

@Composable
fun MediaGrid(
    entries: List<MediaEntry>,
    nav: NavController,
    stateKey: Any,
    emptyText: String,
) {
    val progress by MojaTvApp.repo.storage.progress.collectAsState()
    if (entries.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    val gridState = remember(stateKey) { LazyGridState() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(entries) { _, e ->
            val p = progress[e.key]?.takeIf { !it.finished }?.fraction
            val badge = when (e) {
                is MediaEntry.M -> e.movie.rating
                is MediaEntry.S -> e.series.rating ?: e.series.year
            }?.let { formatRating(it) }
            PosterCard(e.title, e.image, badge = badge, progress = p) {
                when (e) {
                    is MediaEntry.M -> nav.openMovie(e.movie.id)
                    is MediaEntry.S -> nav.openSeries(e.series.id)
                }
            }
        }
    }
}

fun formatRating(r: String): String? {
    val d = r.replace(',', '.').toDoubleOrNull() ?: return r.take(4)
    if (d <= 0) return null
    return "★ " + String.format(java.util.Locale.US, "%.1f", if (d > 10) d / 10 else d)
}

@Composable
fun MoviesScreen(c: Catalog, nav: NavController) {
    val favorites by MojaTvApp.repo.storage.favorites.collectAsState()
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val options = remember(c) {
        listOf(ALL to "Wszystkie", FAV to "★ Ulubione") + c.movieCategories.map { it.id to it.name }
    }
    val entries = remember(selected, c, favorites) {
        when (selected) {
            ALL -> c.movies
            FAV -> {
                val keys = favorites.filter { it.kind == ContentKind.MOVIE }.map { it.key.substringAfter(':') }.toSet()
                c.movies.filter { it.id in keys }
            }
            else -> c.movies.filter { it.categoryId == selected }
        }.map { MediaEntry.M(it) }
    }
    Column(Modifier.fillMaxSize()) {
        CategoryChips(options, selected) { selected = it }
        MediaGrid(entries, nav, selected, if (selected == FAV) "Brak ulubionych filmów." else "Brak filmów w tej kategorii.")
    }
}

@Composable
fun SeriesScreen(c: Catalog, nav: NavController) {
    val favorites by MojaTvApp.repo.storage.favorites.collectAsState()
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val options = remember(c) {
        listOf(ALL to "Wszystkie", FAV to "★ Ulubione") + c.seriesCategories.map { it.id to it.name }
    }
    val entries = remember(selected, c, favorites) {
        when (selected) {
            ALL -> c.series
            FAV -> {
                val keys = favorites.filter { it.kind == ContentKind.SERIES }.map { it.key.substringAfter(':') }.toSet()
                c.series.filter { it.id in keys }
            }
            else -> c.series.filter { it.categoryId == selected }
        }.map { MediaEntry.S(it) }
    }
    Column(Modifier.fillMaxSize()) {
        CategoryChips(options, selected) { selected = it }
        MediaGrid(entries, nav, selected, if (selected == FAV) "Brak ulubionych seriali." else "Brak seriali w tej kategorii.")
    }
}

@Composable
fun ShowScreen(c: Catalog, nav: NavController) {
    val repo = MojaTvApp.repo
    // odczyt przy każdym wejściu, żeby zmiany z ustawień były widoczne od razu
    val showCats = remember(c) { repo.showCategories(c) }
    var selected by rememberSaveable { mutableStateOf(ALL) }

    if (showCats.isEmpty()) {
        EmptyState(
            "Nie znaleziono automatycznie kategorii z programami typu show.\nWybierz je ręcznie z listy kategorii Twojej playlisty.",
        ) {
            Button(onClick = { nav.navigate(Routes.SHOW_SETTINGS) }) { Text("Wybierz kategorie Show") }
        }
        return
    }

    val options = remember(showCats) {
        listOf(ALL to "Wszystkie") + showCats.map { it.key to it.name }
    }
    val entries = remember(selected, showCats, c) {
        val keys = if (selected == ALL) showCats.map { it.key }.toSet() else setOf(selected)
        val movieCats = keys.filter { it.startsWith("movie:") }.map { it.removePrefix("movie:") }.toSet()
        val seriesCats = keys.filter { it.startsWith("series:") }.map { it.removePrefix("series:") }.toSet()
        c.series.filter { it.categoryId in seriesCats }.map { MediaEntry.S(it) } +
            c.movies.filter { it.categoryId in movieCats }.map { MediaEntry.M(it) }
    }
    Column(Modifier.fillMaxSize()) {
        CategoryChips(options, selected) { selected = it }
        MediaGrid(entries, nav, selected, "Brak pozycji w tej kategorii.")
    }
}
