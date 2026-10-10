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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.Http
import com.mojatv.app.data.LoadState
import com.mojatv.app.data.SourceType
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.Background
import com.mojatv.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, onSignOut: () -> Unit) {
    val repo = MojaTvApp.repo
    val storage = repo.storage
    val context = LocalContext.current
    val account by repo.account.collectAsState()
    val state by repo.state.collectAsState()
    val source = storage.source

    var liveFormat by remember { mutableStateOf(storage.liveFormat) }
    var epgEnabled by remember { mutableStateOf(storage.epgEnabled) }
    var hideAdult by remember { mutableStateOf(storage.hideAdult) }
    var userAgent by remember { mutableStateOf(storage.userAgent) }
    var epgUrl by remember { mutableStateOf(source?.epgUrl ?: "") }
    var confirmSignOut by remember { mutableStateOf(false) }

    fun reload() {
        repo.load(force = true)
        Toast.makeText(context, "Odświeżam playlistę…", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // ---- Konto ----
            Group("Źródło")
            if (source != null) {
                Line("Typ", if (source.type == SourceType.XTREAM) "Xtream Codes" else "Playlista M3U")
                Line(
                    "Adres",
                    if (source.type == SourceType.XTREAM) "${source.server} · ${source.username}"
                    else source.m3uUrl.take(60),
                )
            }
            account?.let { a ->
                Line("Status konta", a.status ?: "—")
                a.expiresAt?.let {
                    Line("Ważne do", SimpleDateFormat("d MMMM yyyy", Locale("pl")).format(Date(it)))
                }
                Line("Połączenia", "${a.activeConnections ?: "?"} / ${a.maxConnections ?: "?"}")
                if (a.isTrial) Line("Konto", "Testowe")
            }
            (state as? LoadState.Ready)?.catalog?.let { c ->
                Line(
                    "Zawartość",
                    "${c.live.size} kanałów · ${c.movies.size} filmów · ${c.series.size} seriali",
                )
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { reload() }) { Text("Odśwież playlistę") }
                OutlinedButton(onClick = { confirmSignOut = true }) { Text("Zmień źródło") }
            }

            // ---- Odtwarzanie ----
            Group("Odtwarzanie")
            if (source?.type == SourceType.XTREAM) {
                Text(
                    "Format kanałów na żywo",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Medium,
                )
                listOf("ts" to "MPEG-TS (zalecany)", "m3u8" to "HLS (.m3u8)").forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (liveFormat != value) {
                                    liveFormat = value
                                    storage.liveFormat = value
                                    reload()
                                }
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = liveFormat == value, onClick = null)
                        Text(label)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("User-Agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Niektórzy dostawcy wymagają konkretnego User-Agenta. Domyślny: ${Http.DEFAULT_USER_AGENT}",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val ua = userAgent.trim().ifBlank { Http.DEFAULT_USER_AGENT }
                        userAgent = ua
                        storage.userAgent = ua
                        Http.userAgent = ua
                        Toast.makeText(context, "Zapisano", Toast.LENGTH_SHORT).show()
                    }) { Text("Zapisz") }
                    TextButton(onClick = {
                        userAgent = Http.DEFAULT_USER_AGENT
                        storage.userAgent = Http.DEFAULT_USER_AGENT
                        Http.userAgent = Http.DEFAULT_USER_AGENT
                    }) { Text("Przywróć domyślny") }
                }
            }

            // ---- Przewodnik TV ----
            Group("Program TV (EPG)")
            SwitchLine("Wczytuj program TV", epgEnabled) {
                epgEnabled = it
                storage.epgEnabled = it
                reload()
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("Własny link EPG (XMLTV)") },
                    placeholder = { Text("puste = z playlisty / serwera") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    val s = storage.source
                    if (s != null) {
                        storage.source = s.copy(epgUrl = epgUrl.trim())
                        reload()
                    }
                }) { Text("Zapisz link EPG") }
            }

            // ---- Treści ----
            Group("Treści")
            SwitchLine("Ukryj kategorie dla dorosłych", hideAdult) {
                hideAdult = it
                storage.hideAdult = it
                reload()
            }
            NavLine("Kategorie w zakładce Show") { nav.navigate(Routes.SHOW_SETTINGS) }
            NavLine("Wyczyść historię oglądania") {
                storage.clearHistory()
                Toast.makeText(context, "Historia wyczyszczona", Toast.LENGTH_SHORT).show()
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "MojaTV 1.0 · aplikacja do odtwarzania Twoich własnych playlist",
                color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Zmienić źródło?") },
            text = { Text("Wylogujesz się z obecnej playlisty. Ulubione i historia zostaną zachowane.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Zmień", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title.uppercase(),
        color = Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(0.4f))
        Text(value, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavLine(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary)
    }
}

// ---------------- Wybór kategorii Show ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowSettingsScreen(nav: NavController) {
    val repo = MojaTvApp.repo
    val catalog = repo.catalog
    var selected by remember {
        mutableStateOf(catalog?.let { c -> repo.storage.showCategories ?: repo.autoShowKeys(c) } ?: emptySet())
    }
    var isAuto by remember { mutableStateOf(repo.storage.showCategories == null) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Kategorie Show") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                actions = {
                    if (!isAuto && catalog != null) {
                        TextButton(onClick = {
                            repo.storage.showCategories = null
                            selected = repo.autoShowKeys(catalog)
                            isAuto = true
                        }) { Text("Automatycznie") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        if (catalog == null) {
            Column(Modifier.padding(padding)) { EmptyState("Playlista jeszcze się wczytuje…") }
            return@Scaffold
        }
        val candidates = remember(catalog) { repo.allShowCandidates(catalog) }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    if (isAuto) "Tryb automatyczny: zaznaczone są kategorie rozpoznane po nazwie. Zmiana dowolnego pola przełącza na wybór ręczny."
                    else "Wybór ręczny.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(candidates) { cat ->
                val checked = cat.key in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (checked) selected - cat.key else selected + cat.key
                            repo.storage.showCategories = selected
                            isAuto = false
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(cat.name)
                        Text(
                            if (cat.isSeries) "Seriale" else "Filmy",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}
