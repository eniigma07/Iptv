package com.mojatv.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.EpgProgram
import com.mojatv.app.data.EpgTime
import com.mojatv.app.data.PlayItem
import com.mojatv.app.rec.RecStatus
import com.mojatv.app.rec.RecordingScheduler
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.Background
import com.mojatv.app.ui.theme.SurfaceHigh
import com.mojatv.app.ui.theme.TextSecondary
import java.util.Calendar

/** Zgodność wsteczna: dawne okienko nagrywania = pełny program kanału. */
@Composable
fun RecordDialog(channel: PlayItem, onDismiss: () -> Unit) = ChannelGuide(channel, onDismiss)

/**
 * Pełnoekranowy program kanału na ok. 30 godzin z nagrywaniem:
 * „Nagraj teraz”, nagranie wybranej audycji (kropka przy tytule) albo o wybranej godzinie.
 * onStartedNow — wywoływane, gdy nagrywanie zaczyna się od razu (np. żeby odtwarzacz zwolnił połączenie).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelGuide(channel: PlayItem, onDismiss: () -> Unit, onStartedNow: (() -> Unit)? = null) {
    val context = LocalContext.current
    val repo = MojaTvApp.repo
    val recs by MojaTvApp.instance.recordings.items.collectAsState()
    var programs by remember { mutableStateOf<List<EpgProgram>?>(null) }
    var manual by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(channel.key) { programs = repo.upcomingPrograms(channel) }

    fun afterSchedule(title: String, start: Long, end: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val now = System.currentTimeMillis()
        val startsNow = start <= now + 5000
        val msg = if (startsNow) "Nagrywanie rozpoczęte: $title" else "Zaplanowano: $title, ${EpgTime.dayHm(start)}"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        val others = RecordingScheduler.overlapping(start, end).filter { it.title != title || it.start != start }
        if (others.isNotEmpty()) {
            Toast.makeText(
                context,
                "Uwaga: w tym czasie jest też inne nagranie. Jeśli konto IPTV pozwala na jedno połączenie, jedno z nich się nie uda.",
                Toast.LENGTH_LONG,
            ).show()
        }
        if (startsNow) onStartedNow?.invoke()
    }

    fun scheduledFor(p: EpgProgram) = recs.firstOrNull {
        it.channel.key == channel.key && it.active && it.title == p.title &&
            it.start <= p.start && it.end >= p.end - 60_000
    }

    fun recordProgram(p: EpgProgram) {
        val r = RecordingScheduler.create(context, channel, p.title, p.start, p.end)
        afterSchedule(p.title, r.start, r.end)
    }

    fun recordFor(minutes: Int) {
        val now = System.currentTimeMillis()
        val title = "${channel.title} ${EpgTime.hm(now)}"
        val r = RecordingScheduler.create(context, channel, title, now, now + minutes * 60_000L, addPadding = false)
        afterSchedule(title, r.start, r.end)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding()) {
            // nagłówek
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                Column(Modifier.weight(1f)) {
                    Text(channel.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Program TV · godziny: ${EpgTime.zoneLabel()} · zapas ${repo.storage.recPadBefore}/${repo.storage.recPadAfter} min",
                        color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val now = System.currentTimeMillis()
            val list = programs
            val current = list?.firstOrNull { it.start <= now && it.end > now }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Nagraj teraz", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (current != null) {
                                AssistChip(
                                    onClick = { recordProgram(current) },
                                    label = { Text("Do końca „${current.title.take(22)}”") },
                                )
                            }
                            AssistChip(onClick = { recordFor(30) }, label = { Text("30 min") })
                            AssistChip(onClick = { recordFor(60) }, label = { Text("1 godz.") })
                            AssistChip(onClick = { recordFor(120) }, label = { Text("2 godz.") })
                            AssistChip(onClick = { manual = true }, label = { Text("O wybranej godzinie…") })
                        }
                    }
                }

                when {
                    list == null -> item {
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("  Wczytywanie programu…")
                        }
                    }
                    list.isEmpty() -> item {
                        Text(
                            "Brak programu dla tego kanału. Użyj „Nagraj teraz” albo „O wybranej godzinie”.",
                            color = TextSecondary, modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> {
                        var lastDay = -1
                        list.forEachIndexed { idx, p ->
                            val day = EpgTime.dayKey(p.start)
                            if (day != lastDay) {
                                lastDay = day
                                item(key = "day$day") {
                                    Text(
                                        EpgTime.dayLabel(p.start),
                                        color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth().background(Background)
                                            .padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
                                    )
                                }
                            }
                            item(key = "p${p.start}_$idx") {
                                ProgramRow(
                                    p = p,
                                    isNow = p.start <= now && p.end > now,
                                    expanded = expanded == p.start,
                                    scheduled = scheduledFor(p),
                                    onClick = { expanded = if (expanded == p.start) null else p.start },
                                    onRecord = { recordProgram(p) },
                                    onCancel = { rec ->
                                        RecordingScheduler.cancel(context, rec)
                                        Toast.makeText(context, "Anulowano nagranie", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (manual) {
            ManualRecordDialog(channel, onDone = { title, start, end ->
                manual = false
                afterSchedule(title, start, end)
            }, onCancel = { manual = false })
        }
    }
}

@Composable
private fun ProgramRow(
    p: EpgProgram,
    isNow: Boolean,
    expanded: Boolean,
    scheduled: com.mojatv.app.rec.Recording?,
    onClick: () -> Unit,
    onRecord: () -> Unit,
    onCancel: (com.mojatv.app.rec.Recording) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (isNow) SurfaceHigh else Background)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                EpgTime.hm(p.start),
                color = if (isNow) Accent else TextSecondary,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(54.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    p.title,
                    fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = if (expanded) 4 else 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${EpgTime.hm(p.start)}–${EpgTime.hm(p.end)}" + if (isNow) " · teraz" else "",
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
            if (scheduled != null) {
                IconButton(onClick = { onCancel(scheduled) }) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        if (scheduled.status == RecStatus.RECORDING) "Nagrywa — zatrzymaj" else "Zaplanowane — anuluj",
                        tint = Accent,
                    )
                }
            } else {
                IconButton(onClick = onRecord) {
                    Icon(Icons.Filled.FiberManualRecord, "Nagraj", tint = TextSecondary)
                }
            }
        }
        if (isNow) {
            val now = System.currentTimeMillis()
            LinearProgressIndicator(
                progress = { ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f) },
                modifier = Modifier.padding(start = 54.dp, end = 12.dp, top = 4.dp).fillMaxWidth().height(3.dp),
                color = Accent, trackColor = Background,
            )
        }
        if (expanded && !p.description.isNullOrBlank()) {
            Text(
                p.description, color = TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 54.dp, end = 12.dp, top = 6.dp),
            )
        }
    }
    HorizontalDivider(color = SurfaceHigh)
}

/** Ręczne zaplanowanie: godzina startu (wpisywana) i czas trwania. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ManualRecordDialog(
    channel: PlayItem,
    onDone: (String, Long, Long) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val cal = Calendar.getInstance()
    val state = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true,
    )
    var minutes by remember { mutableLongStateOf(60L) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Nagraj o wybranej godzinie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Start (czas telefonu) — stuknij godzinę lub minuty i wpisz:", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(state = state) }
                Text("Czas nagrania:", color = TextSecondary, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L, 60L, 90L, 120L, 180L).forEach { m ->
                        val label = when (m) {
                            30L -> "30 min"; 60L -> "1 godz."; 90L -> "1,5 godz."; 120L -> "2 godz."; else -> "3 godz."
                        }
                        AssistChip(onClick = { minutes = m }, label = { Text((if (m == minutes) "✓ " else "") + label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = Calendar.getInstance()
                c.set(Calendar.HOUR_OF_DAY, state.hour)
                c.set(Calendar.MINUTE, state.minute)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (c.timeInMillis < System.currentTimeMillis() - 60_000) c.add(Calendar.DAY_OF_MONTH, 1)
                val start = c.timeInMillis
                val end = start + minutes * 60_000L
                val title = "${channel.title} ${EpgTime.hm(start)}"
                RecordingScheduler.create(context, channel, title, start, end, addPadding = false)
                onDone(title, start, end)
            }) { Text("Zaplanuj") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Anuluj") } },
    )
}
