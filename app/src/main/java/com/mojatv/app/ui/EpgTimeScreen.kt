package com.mojatv.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.ContentKind
import com.mojatv.app.data.EpgProgram
import com.mojatv.app.data.EpgTime
import com.mojatv.app.data.PlayItem
import com.mojatv.app.data.toPlayItem
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.Background
import com.mojatv.app.ui.theme.SurfaceHigh
import com.mojatv.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Ustawienie i podgląd korekty godzin programu TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgTimeScreen(nav: NavController) {
    val repo = MojaTvApp.repo
    val storage = repo.storage
    var mode by remember { mutableStateOf(storage.epgMode) }
    var shift by remember { mutableIntStateOf(storage.epgShiftMinutes) }
    var showPolish by remember { mutableStateOf(storage.epgShowPolish) }
    var version by remember { mutableIntStateOf(0) }

    // kanały do podglądu: najpierw ulubione, potem pierwsze z listy
    val channels = remember {
        val fav = repo.storage.favorites.value.filter { it.kind == ContentKind.LIVE }
        val first = repo.catalog?.live?.take(30)?.map { it.toPlayItem() }.orEmpty()
        (fav + first).distinctBy { it.key }.take(30)
    }
    var channelIdx by remember { mutableIntStateOf(0) }
    var programs by remember { mutableStateOf<List<EpgProgram>?>(null) }
    val channel: PlayItem? = channels.getOrNull(channelIdx)

    LaunchedEffect(channel, version) {
        programs = null
        programs = channel?.let { repo.programsFor(it) } ?: emptyList()
    }

    fun apply(newMode: String? = null, newShift: Int? = null, newPolish: Boolean? = null) {
        repo.setEpgCorrection(newMode, newShift, newPolish)
        newMode?.let { mode = it }
        newShift?.let { shift = it }
        newPolish?.let { showPolish = it }
        version++
    }

    val now = System.currentTimeMillis()
    val phoneNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val plNow = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Europe/Warsaw") }
        .format(Date(now))

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Godziny w programie TV") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                "Jeśli godziny w programie nie zgadzają się z tym, co faktycznie leci, ustaw korektę poniżej " +
                    "i sprawdź w podglądzie. Teraz jest $phoneNow w telefonie i $plNow w Polsce.",
                color = TextSecondary, fontSize = 14.sp,
            )

            Section("Skąd są godziny w programie")
            Choice(
                "Jak podaje serwer (bez zmian)",
                "Dobre, gdy serwer poprawnie oznacza strefę czasową.",
                mode == "auto",
            ) { apply(newMode = "auto") }
            Choice(
                "Godziny w programie to czas polski",
                "Np. „Fakty 19:00” = 19:00 w Polsce. Zmiana czasu letni/zimowy uwzględniana automatycznie.",
                mode == "pl",
            ) { apply(newMode = "pl") }

            Section("Dodatkowe przesunięcie")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { apply(newShift = (shift - 30).coerceAtLeast(-720)) }) { Text("− 30 min") }
                Text(
                    shiftLabel(shift),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                OutlinedButton(onClick = { apply(newShift = (shift + 30).coerceAtMost(720)) }) { Text("+ 30 min") }
            }
            if (shift != 0) TextButton(onClick = { apply(newShift = 0) }) { Text("Wyzeruj przesunięcie") }

            Section("Pokazuj godziny w")
            Choice("Czasie telefonu", "Teraz: $phoneNow", !showPolish) { apply(newPolish = false) }
            Choice("Czasie polskim", "Teraz: $plNow — jak w polskiej gazecie z programem", showPolish) { apply(newPolish = true) }

            Section("Podgląd")
            if (channel == null) {
                Text("Playlista jeszcze się wczytuje…", color = TextSecondary)
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceHigh), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(channel.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (channels.size > 1) {
                                TextButton(onClick = { channelIdx = (channelIdx + 1) % channels.size }) { Text("Inny kanał") }
                            }
                        }
                        val list = programs
                        when {
                            list == null -> Text("Wczytywanie…", color = TextSecondary)
                            list.isEmpty() -> Text("Brak programu dla tego kanału.", color = TextSecondary)
                            else -> list.take(5).forEach { p ->
                                val isNow = p.start <= now && p.end > now
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "${EpgTime.hm(p.start)}–${EpgTime.hm(p.end)}",
                                        color = if (isNow) Accent else TextSecondary,
                                        modifier = Modifier.width(110.dp),
                                    )
                                    Text(p.title + if (isNow) "  (teraz)" else "", fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Włącz ten kanał i sprawdź, czy to, co leci, zgadza się z pozycją „teraz”.",
                            color = TextSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun shiftLabel(min: Int): String {
    if (min == 0) return "bez przesunięcia"
    val sign = if (min > 0) "+" else "−"
    val a = kotlin.math.abs(min)
    val h = a / 60
    val m = a % 60
    return sign + (if (h > 0) "$h godz. " else "") + (if (m > 0) "$m min" else "")
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}
