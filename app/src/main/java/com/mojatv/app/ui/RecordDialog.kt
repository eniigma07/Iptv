package com.mojatv.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.core.content.ContextCompat
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.EpgProgram
import com.mojatv.app.data.EpgTime
import com.mojatv.app.data.PlayItem
import com.mojatv.app.rec.RecStatus
import com.mojatv.app.rec.RecordingScheduler
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.TextSecondary
import java.util.Calendar

/** Okno nagrywania kanału: nagraj teraz, wybierz audycję z programu albo zaplanuj ręcznie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordDialog(channel: PlayItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = MojaTvApp.repo
    val recs by MojaTvApp.instance.recordings.items.collectAsState()
    var programs by remember { mutableStateOf<List<EpgProgram>?>(null) }
    var manual by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(channel.key) { programs = repo.upcomingPrograms(channel) }

    fun afterSchedule(title: String, start: Long, end: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val others = RecordingScheduler.overlapping(start, end).filter { it.title != title || it.start != start }
        val msg = if (start <= System.currentTimeMillis() + 5000) "Nagrywanie rozpoczęte: $title"
        else "Zaplanowano: $title, ${EpgTime.dayHm(start)}"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        if (others.isNotEmpty()) {
            Toast.makeText(
                context,
                "Uwaga: w tym czasie jest też inne nagranie. Jeśli konto IPTV pozwala na jedno połączenie, jedno z nich się nie uda.",
                Toast.LENGTH_LONG,
            ).show()
        }
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
        onDismiss()
    }

    if (manual) {
        ManualRecordDialog(channel, onDone = { title, start, end ->
            manual = false
            afterSchedule(title, start, end)
        }, onCancel = { manual = false })
        return
    }

    val now = System.currentTimeMillis()
    val current = programs?.firstOrNull { it.start <= now && it.end > now }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nagraj: ${channel.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text("Nagraj teraz", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current != null) {
                        AssistChip(
                            onClick = { recordProgram(current); onDismiss() },
                            label = { Text("Do końca „${current.title.take(18)}”") },
                        )
                    }
                    AssistChip(onClick = { recordFor(30) }, label = { Text("30 min") })
                    AssistChip(onClick = { recordFor(60) }, label = { Text("1 godz.") })
                    AssistChip(onClick = { recordFor(120) }, label = { Text("2 godz.") })
                    AssistChip(onClick = { manual = true }, label = { Text("O wybranej godzinie…") })
                }
                Spacer(Modifier.size(12.dp))
                Text("Z programu TV", fontWeight = FontWeight.SemiBold)
                Text(
                    "Godziny w ${EpgTime.zoneLabel()}. Zapas: ${repo.storage.recPadBefore} min przed, ${repo.storage.recPadAfter} min po.",
                    color = TextSecondary, fontSize = 12.sp,
                )
                val list = programs
                when {
                    list == null -> Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Wczytywanie programu…", fontSize = 14.sp)
                    }
                    list.isEmpty() -> Text(
                        "Brak programu dla tego kanału. Użyj „Nagraj teraz” albo „O wybranej godzinie”.",
                        color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(list) { p ->
                            val existing = scheduledFor(p)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        EpgTime.dayHm(p.start) + "–" + EpgTime.hm(p.end),
                                        color = if (p.start <= now) Accent else TextSecondary, fontSize = 12.sp,
                                    )
                                    Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (existing != null) {
                                    IconButton(onClick = {
                                        RecordingScheduler.cancel(context, existing)
                                        Toast.makeText(context, "Anulowano nagranie", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            if (existing.status == RecStatus.RECORDING) "Nagrywa — zatrzymaj" else "Zaplanowane — anuluj",
                                            tint = Accent,
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { recordProgram(p) }) {
                                        Icon(Icons.Filled.FiberManualRecord, "Nagraj", tint = TextSecondary)
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}

/** Ręczne zaplanowanie: godzina startu i czas trwania. */
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
        initialMinute = (cal.get(Calendar.MINUTE) / 5 * 5 + 5) % 60,
        is24Hour = true,
    )
    var minutes by remember { mutableLongStateOf(60L) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Nagraj o wybranej godzinie") },
        text = {
            Column {
                Text("Start (czas telefonu):", color = TextSecondary, fontSize = 13.sp)
                TimePicker(state = state)
                Text("Czas nagrania:", color = TextSecondary, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L, 60L, 90L, 120L, 180L).forEach { m ->
                        AssistChip(
                            onClick = { minutes = m },
                            label = { Text((if (m == minutes) "✓ " else "") + if (m < 60) "$m min" else "${m / 60}${if (m % 60 != 0L) ",5" else ""} godz.") },
                        )
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
