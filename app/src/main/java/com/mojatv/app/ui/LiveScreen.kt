package com.mojatv.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.Catalog
import com.mojatv.app.data.ContentKind
import com.mojatv.app.data.toPlayItem
import com.mojatv.app.player.playVideo
import com.mojatv.app.ui.theme.SurfaceHigh
import kotlinx.coroutines.delay

const val ALL = "__all__"
const val FAV = "__fav__"

@Composable
fun LiveScreen(c: Catalog) {
    val repo = MojaTvApp.repo
    val context = LocalContext.current
    val favorites by repo.storage.favorites.collectAsState()
    val epg by repo.epg.collectAsState()

    var selected by rememberSaveable { mutableStateOf(ALL) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val options = remember(c) {
        listOf(FAV to "★ Ulubione", ALL to "Wszystkie") + c.liveCategories.map { it.id to it.name }
    }
    val items = remember(selected, c, favorites) {
        when (selected) {
            FAV -> favorites.filter { it.kind == ContentKind.LIVE }
            ALL -> c.live.map { it.toPlayItem() }
            else -> c.live.filter { it.categoryId == selected }.map { it.toPlayItem() }
        }
    }
    val favoriteKeys = remember(favorites) { favorites.map { it.key }.toSet() }
    var recordFor by remember { mutableStateOf<com.mojatv.app.data.PlayItem?>(null) }
    recordFor?.let { RecordDialog(it) { recordFor = null } }

    Column(Modifier.fillMaxSize()) {
        CategoryChips(options, selected) { selected = it }
        if (items.isEmpty()) {
            EmptyState(if (selected == FAV) "Brak ulubionych kanałów. Dodaj je gwiazdką." else "Brak kanałów w tej kategorii.")
            return@Column
        }
        val listState = remember(selected) { androidx.compose.foundation.lazy.LazyListState() }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(items) { i, item ->
                val (cur, next) = if (epg.isEmpty()) null to null else repo.nowAndNext(item.epgId, now)
                ChannelRow(
                    item = item,
                    number = i + 1,
                    now = cur,
                    next = next,
                    isFavorite = item.key in favoriteKeys,
                    onClick = { context.playVideo(items, i) },
                    onToggleFavorite = { repo.storage.toggleFavorite(item) },
                    onLongClick = { recordFor = item },
                    onGuide = { recordFor = item },
                )
                HorizontalDivider(color = SurfaceHigh, thickness = 0.5.dp)
            }
        }
    }
}
