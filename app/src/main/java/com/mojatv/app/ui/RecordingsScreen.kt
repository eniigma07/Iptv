package com.mojatv.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.ContentKind
import com.mojatv.app.data.EpgTime
import com.mojatv.app.data.PlayItem
import com.mojatv.app.player.playVideo
import com.mojatv.app.rec.RecStatus
import com.mojatv.app.rec.Recording
import com.mojatv.app.rec.RecordingScheduler
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.Background
import com.mojatv.app.ui.theme.SurfaceHigh
import com.mojatv.app.ui.theme.TextSecondary
import java.io.File

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(nav: NavController) {
    val context = LocalContext.current
    val storage = MojaTvApp.repo.storage
    val recs by MojaTvApp.instance.recordings.items.collectAsState()
    var toDelete by remember { mutableStateOf<Recording?>(null) }
    var padBefore by remember { mutableIntStateOf(storage.recPadBefore) }
    var padAfter by remember { mutableIntStateOf(storage.recPadAfter) }
    var small by remember { mutableStateOf(storage.recSmall) }
    var batteryOk by remember { mutableStateOf(RecordingScheduler.ignoresBatteryOptimizations(context)) }
    var exactOk by remember { mutableStateOf(RecordingScheduler.canScheduleExact(context)) }

    // po powrocie z ustawień systemowych odświeżamy stan zgód
    LifecycleResumeEffect(Unit) {
        batteryOk = RecordingScheduler.ignoresBatteryOptimizations(context)
        exactOk = RecordingScheduler.canScheduleExact(context)
        onPauseOrDispose { }
    }

    val recording = recs.filter { it.status == RecStatus.RECORDING }
    val scheduled = recs.filter { it.status == RecStatus.SCHEDULED }.sortedBy { it.start }
    val finished = recs.filter { !it.active }.sortedByDescending { it.start }

    fun missing() = Toast.makeText(context, "Nie znaleziono pliku nagrania (mógł zostać usunięty).", Toast.LENGTH_LONG).show()

    fun play(r: Recording) {
        val uri = r.playUri ?: return missing()
        if (r.contentUri == null && r.filePath?.let { File(it).exists() } != true) return missing()
        context.playVideo(
            PlayItem(
                key = "rec:${r.id}",
                kind = ContentKind.MOVIE,
                title = r.title,
                url = uri,
                image = r.channel.image,
                subtitle = r.channel.title + " · " + EpgTime.dayHm(r.start),
            )
        )
    }

    /** Uri, które można przekazać innej aplikacji (content://). */
    fun shareUri(r: Recording): Uri? {
        r.contentUri?.let { return Uri.parse(it) }
        val f = r.filePath?.let { File(it) }?.takeIf { it.exists() } ?: return null
        return runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", f) }.getOrNull()
    }

    fun openExternal(r: Recording) {
        val uri = shareUri(r) ?: return missing()
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(Intent.createChooser(intent, "Otwórz w…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "Brak innego odtwarzacza. Zainstaluj np. VLC.", Toast.LENGTH_LONG).show() }
    }

    fun share(r: Recording) {
        val uri = shareUri(r) ?: return missing()
        val intent = Intent(Intent.ACTION_SEND)
            .setType("video/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, r.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, "Udostępnij nagranie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Nagrania") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            if (!batteryOk || !exactOk) {
                item {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth()
                            .background(Color(0xFF3A2A12), RoundedCornerShape(12.dp)).padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFC069))
                            Text("  Pozwól nagrywać w tle", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Bez tego Android może uśpić aplikację i nagranie nie zacznie się o czasie.",
                            fontSize = 13.sp, color = Color(0xFFE6D3B3), modifier = Modifier.padding(vertical = 6.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!batteryOk) Button(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            }) { Text("Bateria: bez ograniczeń") }
                            if (!exactOk && android.os.Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            }) { Text("Dokładne budziki") }
                        }
                    }
                }
            }

            if (recs.isEmpty()) {
                item {
                    Text(
                        "Nie masz jeszcze nagrań.\n\nŻeby nagrać program: na liście kanałów przytrzymaj kanał palcem " +
                            "albo w odtwarzaczu kanału stuknij czerwoną kropkę ●. Możesz nagrać teraz albo wybrać audycję z programu TV.",
                        color = TextSecondary, modifier = Modifier.padding(24.dp),
                    )
                }
            }

            if (recording.isNotEmpty()) {
                item { SectionTitle("Teraz nagrywane") }
                items(recording, key = { it.id }) { r ->
                    RecRow(r, onClick = null) {
                        IconButton(onClick = { RecordingScheduler.cancel(context, r) }) {
                            Icon(Icons.Filled.Stop, "Zatrzymaj", tint = Accent)
                        }
                    }
                }
            }
            if (scheduled.isNotEmpty()) {
                item { SectionTitle("Zaplanowane") }
                items(scheduled, key = { it.id }) { r ->
                    RecRow(r, onClick = null) {
                        IconButton(onClick = { RecordingScheduler.delete(context, r) }) {
                            Icon(Icons.Filled.Delete, "Anuluj", tint = TextSecondary)
                        }
                    }
                }
            }
            if (finished.isNotEmpty()) {
                item { SectionTitle("Gotowe") }
                items(finished, key = { it.id }) { r ->
                    RecRow(r, onClick = if (r.status == RecStatus.DONE) ({ play(r) }) else null) {
                        if (r.status == RecStatus.DONE) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Więcej", tint = TextSecondary) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Odtwórz") },
                                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                                        onClick = { menu = false; play(r) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Otwórz w innym odtwarzaczu") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                        onClick = { menu = false; openExternal(r) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Udostępnij") },
                                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                                        onClick = { menu = false; share(r) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Usuń") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick = { menu = false; toDelete = r },
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { toDelete = r }) { Icon(Icons.Filled.Delete, "Usuń", tint = TextSecondary) }
                        }
                    }
                }
            }

            item { Column {
                SectionTitle("Ustawienia nagrywania")
                Row(
                    Modifier.fillMaxWidth().clickable { small = !small; storage.recSmall = small }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Zmniejszaj nagrania")
                        Text(
                            if (small) "720p, ok. 600 MB na godzinę. Telefon zmniejsza nagranie na bieżąco, kawałkami po 10 minut."
                            else "Oryginalna jakość kanału — nawet kilka GB na godzinę.",
                            color = TextSecondary, fontSize = 12.sp,
                        )
                    }
                    Switch(checked = small, onCheckedChange = { small = it; storage.recSmall = it })
                }
                Stepper("Zacznij wcześniej", padBefore) {
                    padBefore = it.coerceIn(0, 30); storage.recPadBefore = padBefore
                }
                Stepper("Skończ później", padAfter) {
                    padAfter = it.coerceIn(0, 60); storage.recPadAfter = padAfter
                }
                Text(
                    if (android.os.Build.VERSION.SDK_INT >= 29)
                        "Gdzie są nagrania: w pamięci telefonu, folder Filmy → MojaTV (widać je w Galerii i w aplikacji Moje pliki). " +
                            "Podczas nagrywania plik jest najpierw w folderze aplikacji, a po zakończeniu trafia do Filmy/MojaTV."
                    else
                        "Gdzie są nagrania: Android/data/${context.packageName}/files/Movies/Nagrania",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val free = MojaTvApp.instance.recordings.freeSpace() / (1024 * 1024 * 1024.0)
                Text(
                    "Wolne miejsce: %.1f GB.".format(free),
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "Nagranie trwa w tle przy zamkniętej aplikacji. Telefon musi mieć wtedy internet i nie może być wyłączony.",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
                )
            } }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Usunąć nagranie?") },
            text = { Text("„${r.title}” zostanie usunięte z telefonu.") },
            confirmButton = {
                TextButton(onClick = {
                    RecordingScheduler.delete(context, r)
                    toDelete = null
                }) { Text("Usuń", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun RecRow(r: Recording, onClick: (() -> Unit)?, action: @Composable () -> Unit) {
    val icon = when (r.status) {
        RecStatus.RECORDING -> Icons.Filled.FiberManualRecord
        RecStatus.SCHEDULED -> Icons.Filled.Schedule
        RecStatus.DONE -> Icons.Filled.PlayArrow
        else -> Icons.Filled.Warning
    }
    val tint = when (r.status) {
        RecStatus.RECORDING -> Accent
        RecStatus.DONE -> Color.White
        else -> TextSecondary
    }
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(SurfaceHigh, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(r.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${r.channel.title} · ${EpgTime.dayHm(r.start)}–${EpgTime.hm(r.end)}",
                color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val status = when (r.status) {
                RecStatus.RECORDING ->
                    if (System.currentTimeMillis() >= r.end) "Kończenie i zmniejszanie nagrania…"
                    else "Nagrywa · pobrano ${r.bytes / (1024 * 1024)} MB"
                RecStatus.SCHEDULED -> "Zaplanowane"
                RecStatus.DONE -> "${r.bytes / (1024 * 1024)} MB · stuknij, aby obejrzeć"
                RecStatus.FAILED -> "Nieudane" + (r.error?.let { ": $it" } ?: "")
                RecStatus.CANCELLED -> "Anulowane"
            }
            Text(status, color = if (r.status == RecStatus.FAILED) Color(0xFFFF8A8D) else TextSecondary, fontSize = 12.sp, maxLines = 2)
        }
        action()
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(value - 1) }) { Text("−", fontSize = 20.sp) }
        Text("$value min", modifier = Modifier.padding(horizontal = 4.dp))
        TextButton(onClick = { onChange(value + 1) }) { Text("+", fontSize = 20.sp) }
    }
    Spacer(Modifier.height(2.dp))
}
