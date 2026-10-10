package com.mojatv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.Catalog
import com.mojatv.app.data.EpgProgram
import com.mojatv.app.data.LoadState
import com.mojatv.app.data.PlayItem
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.SurfaceHigh
import com.mojatv.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pokazuje treść dopiero, gdy katalog jest wczytany; obsługuje ładowanie i błędy. */
@Composable
fun CatalogGate(onOpenSettings: () -> Unit, content: @Composable (Catalog) -> Unit) {
    val repo = MojaTvApp.repo
    val state by repo.state.collectAsState()
    when (val s = state) {
        is LoadState.Ready -> content(s.catalog)
        is LoadState.Loading, LoadState.Idle -> {
            LaunchedEffect(s) { if (s is LoadState.Idle) repo.load() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        (s as? LoadState.Loading)?.message ?: "Wczytywanie…",
                        color = TextSecondary,
                    )
                }
            }
        }
        is LoadState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { repo.load(force = true) }) { Text("Spróbuj ponownie") }
                    OutlinedButton(onClick = onOpenSettings) { Text("Ustawienia") }
                }
            }
        }
    }
}

@Composable
fun CategoryChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        itemsIndexed(options) { _, (id, name) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(name, maxLines = 1) },
            )
        }
    }
}

/** Obrazek z sieci z zastępczym inicjałem, gdy brak logo. */
@Composable
fun NetImage(
    url: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(SurfaceHigh), contentAlignment = Alignment.Center) {
            Text(
                fallbackText.trim().take(2).uppercase(),
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
    if (url.isNullOrBlank()) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

@Composable
fun PosterCard(
    title: String,
    image: String?,
    modifier: Modifier = Modifier,
    badge: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            NetImage(image, title, Modifier.fillMaxSize())
            if (badge != null) {
                Text(
                    badge,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Accent,
                    trackColor = Color(0x66000000),
                )
            }
        }
        Text(
            title,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Karta pozioma 16:9 (kontynuuj oglądanie, kanały na ekranie Start). */
@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    image: String?,
    width: Dp = 200.dp,
    progress: Float? = null,
    fitImage: Boolean = false,
    onClick: () -> Unit,
) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceHigh)
        ) {
            NetImage(
                image, title,
                Modifier.fillMaxSize().padding(if (fitImage) 16.dp else 0.dp),
                contentScale = if (fitImage) ContentScale.Fit else ContentScale.Crop,
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Accent,
                    trackColor = Color(0x66000000),
                )
            }
        }
        Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (subtitle != null) {
            Text(subtitle, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun formatTime(ms: Long): String = timeFormat.format(Date(ms))

/** Wiersz kanału z logo, aktualnym programem i gwiazdką ulubionych. */
@Composable
fun ChannelRow(
    item: PlayItem,
    number: Int?,
    now: EpgProgram?,
    next: EpgProgram?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                number.toString(),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier
                .size(width = 72.dp, height = 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            NetImage(item.image, item.title, Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text(
                    "${formatTime(now.start)}  ${now.title}",
                    fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { now.progress() },
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(2.dp),
                    color = Accent,
                    trackColor = SurfaceHigh,
                )
            } else if (item.subtitle != null) {
                Text(item.subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (next != null) {
                Text(
                    "${formatTime(next.start)}  ${next.title}",
                    fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Ulubione",
                tint = if (isFavorite) Accent else TextSecondary,
            )
        }
    }
}

@Composable
fun EmptyState(text: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = TextSecondary, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun <T> HorizontalSection(
    title: String,
    items: List<T>,
    itemContent: @Composable (Int, T) -> Unit,
) {
    if (items.isEmpty()) return
    SectionTitle(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items) { i, item -> itemContent(i, item) }
    }
}
