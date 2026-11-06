package com.artykulacja.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.data.VoiceInfo
import com.artykulacja.app.notify.Reminder
import com.artykulacja.app.speech.Speech
import com.artykulacja.app.ui.theme.C
import kotlinx.coroutines.launch
import java.util.Locale

private const val SAMPLE = "Hi there. Let's make your English sound effortless, confident and clear."

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = C.Gold, activeTrackColor = C.Gold, inactiveTrackColor = C.Surface3)

@Composable
private fun switchColors() = SwitchDefaults.colors(checkedThumbColor = Color1, checkedTrackColor = C.Gold, uncheckedTrackColor = C.Surface3, uncheckedBorderColor = C.Line)

private val Color1 = androidx.compose.ui.graphics.Color(0xFF1A1408)

@Composable
fun SettingsScreen(nav: NavHostController) {
    val s = app.settings
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmModels by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        s.setReminder(true); Reminder.schedule(ctx, s)
    }
    val voiceName = app.course.voices.firstOrNull { it.id == s.voice }?.name ?: s.voice
    Column(Modifier.fillMaxSize()) {
        TopBar("Ustawienia", { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Section("Głos wzorcowy")
            Panel(Modifier.fillMaxWidth(), onClick = { nav.navigate("voices") }, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(voiceName + if (s.mixVoice.isNotEmpty()) " + ${app.course.voices.firstOrNull { it.id == s.mixVoice }?.name ?: s.mixVoice} ${(s.mixRatio * 100).toInt()}%" else "",
                            style = MaterialTheme.typography.titleMedium, color = C.Text)
                        Text("Wybór głosu i mikser barwy", style = MaterialTheme.typography.bodySmall, color = C.Text2)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Text3)
                }
            }
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                SliderRow("Tempo wzorca", s.speed, 0.8f..1.15f, "${fmt(s.speed)}×") { s.setSpeed(it) }
                SliderRow("Tempo „wolniej”", s.slowSpeed, 0.55f..0.9f, "${fmt(s.slowSpeed)}×") { s.setSlowSpeed(it) }
            }

            Section("Ćwiczenia")
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                SwitchRow("Odtwarzaj wzorzec automatycznie", s.autoPlay) { s.setAutoPlay(it) }
                HorizontalDivider(color = C.Line)
                SwitchRow("Pokazuj zapis fonetyczny", s.showIpa) { s.setShowIpa(it) }
            }
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Wymagania oceny", style = MaterialTheme.typography.titleSmall, color = C.Text)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("Łagodne" to 0.8f, "Standard" to 1.0f, "Wymagające" to 1.3f), s.strictness) { s.setStrictness(it) }
                Spacer(Modifier.height(16.dp))
                Text("Cel dzienny (nagrania)", style = MaterialTheme.typography.titleSmall, color = C.Text)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("10" to 10f, "20" to 20f, "30" to 30f, "50" to 50f), s.dailyGoal.toFloat()) { s.setDailyGoal(it.toInt()) }
            }

            Section("Przypomnienie")
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                SwitchRow("Codzienne przypomnienie", s.reminder) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { s.setReminder(on); Reminder.schedule(ctx, s) }
                }
                if (s.reminder) {
                    HorizontalDivider(color = C.Line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Godzina", style = MaterialTheme.typography.bodyLarge, color = C.Text, modifier = Modifier.weight(1f))
                        Stepper(onStep = { d ->
                            val total = (s.reminderHour * 60 + s.reminderMinute + d * 15 + 1440) % 1440
                            s.setReminder(true, total / 60, total % 60)
                            Reminder.schedule(ctx, s)
                        }) {
                            Text(String.format(Locale.US, "%02d:%02d", s.reminderHour, s.reminderMinute), style = MaterialTheme.typography.titleMedium, color = C.Gold)
                        }
                    }
                }
            }

            Section("Profil")
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Imię", style = MaterialTheme.typography.bodyLarge, color = C.Text, modifier = Modifier.weight(1f))
                    Text(s.name.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = C.Text2)
                }
                HorizontalDivider(color = C.Line)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Test poziomu", style = MaterialTheme.typography.bodyLarge, color = C.Text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("placement_intro") }) { Text("Powtórz", color = C.Gold) }
                }
            }

            Section("Dane")
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Modele offline", style = MaterialTheme.typography.bodyLarge, color = C.Text)
                        Text(String.format(Locale("pl"), "%.0f MB na telefonie", app.models.sizeOnDisk() / 1e6), style = MaterialTheme.typography.bodySmall, color = C.Text3)
                    }
                    TextButton(onClick = { confirmModels = true }) { Text("Pobierz ponownie", color = C.Gold) }
                }
                HorizontalDivider(color = C.Line)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Postępy i historia", style = MaterialTheme.typography.bodyLarge, color = C.Text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirmReset = true }) { Text("Wyzeruj", color = C.Bad) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Artykulacja 1.0 · wszystko działa na telefonie, nagrania nie są nigdzie wysyłane.\nGłos: Kokoro-82M (Apache 2.0). Rozpoznawanie głosek: wav2vec2 L2-ARCTIC (Apache 2.0). Słownik: misaki.",
                style = MaterialTheme.typography.bodySmall, color = C.Text3)
            Spacer(Modifier.height(30.dp))
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Wyzerować postępy?") },
        text = { Text("Historia nagrań, seria i mapa głosek zostaną usunięte. Tej operacji nie można cofnąć.") },
        confirmButton = { TextButton(onClick = { app.store.reset(); confirmReset = false }) { Text("Wyzeruj", color = C.Bad) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Anuluj", color = C.Text2) } },
        containerColor = C.Surface2,
    )
    if (confirmModels) AlertDialog(
        onDismissRequest = { confirmModels = false },
        title = { Text("Pobrać modele ponownie?") },
        text = { Text("Usunę obecne modele (~690 MB) i pobiorę je od nowa. Przydaje się, gdy coś przestało działać.") },
        confirmButton = {
            TextButton(onClick = {
                confirmModels = false
                app.models.delete()
                nav.navigate("download") { popUpTo(0) { inclusive = true } }
            }) { Text("Pobierz", color = C.Gold) }
        },
        dismissButton = { TextButton(onClick = { confirmModels = false }) { Text("Anuluj", color = C.Text2) } },
        containerColor = C.Surface2,
    )
}

private fun fmt(v: Float) = String.format(Locale("pl"), "%.2f", v)

@Composable
private fun Section(t: String) {
    Spacer(Modifier.height(22.dp))
    Eyebrow(t, C.Text3)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = C.Text, modifier = Modifier.weight(1f))
        Text(valueLabel, style = MaterialTheme.typography.titleSmall, color = C.Gold)
    }
    Slider(value = value, onValueChange = onChange, valueRange = range, colors = sliderColors())
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = C.Text, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange, colors = switchColors())
    }
}

@Composable
private fun Segmented(options: List<Pair<String, Float>>, value: Float, onPick: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, v) in options) {
            val sel = kotlin.math.abs(v - value) < 0.01f
            Surface(
                onClick = { onPick(v) }, shape = RoundedCornerShape(12.dp),
                color = if (sel) C.GoldSoft else C.Surface2, border = BorderStroke(1.dp, if (sel) C.Gold else C.Line),
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) C.Gold else C.Text2) }
            }
        }
    }
}

@Composable
private fun Stepper(onStep: (Int) -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(-1) }) { Icon(Icons.Rounded.Remove, "Wcześniej", tint = C.Text2) }
        content()
        IconButton(onClick = { onStep(1) }) { Icon(Icons.Rounded.Add, "Później", tint = C.Text2) }
    }
}

@Composable
fun VoicesScreen(nav: NavHostController, onboarding: Boolean) {
    val s = app.settings
    val engine by app.speech.state.collectAsState()
    val playing by app.player.playing.collectAsState()
    val scope = rememberCoroutineScope()
    var loadingVoice by remember { mutableStateOf<String?>(null) }
    val voices = app.course.voices
    val samplePh = remember(engine) { if (engine == Speech.State.Ready) app.speech.phonemes(SAMPLE) else "" }

    fun preview(id: String) {
        if (engine != Speech.State.Ready) return
        scope.launch {
            loadingVoice = id
            val a = app.speech.speakPhonemes(samplePh, id)
            loadingVoice = null
            app.player.play(a, 24000, "v:$id")
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (onboarding) "Wybierz głos" else "Głos wzorcowy", if (onboarding) null else ({ nav.popBackStack() }))
        if (engine != Speech.State.Ready) { EngineWait(); return }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Tym głosem będziesz słyszeć wzorce. Posłuchaj kilku i wybierz ten, który najchętniej naśladujesz. Zmienisz go w każdej chwili.",
                    style = MaterialTheme.typography.bodyMedium, color = C.Text2, modifier = Modifier.padding(bottom = 6.dp))
            }
            item { Eyebrow("Kobiece", C.Text3) }
            items(voices.filter { it.female }) { v -> VoiceRow(v, s.voice == v.id, playing == "v:${v.id}" || loadingVoice == v.id, { preview(v.id) }) { s.setVoice(v.id); preview(v.id) } }
            item { Spacer(Modifier.height(6.dp)); Eyebrow("Męskie", C.Text3) }
            items(voices.filter { !it.female }) { v -> VoiceRow(v, s.voice == v.id, playing == "v:${v.id}" || loadingVoice == v.id, { preview(v.id) }) { s.setVoice(v.id); preview(v.id) } }
            item {
                Spacer(Modifier.height(14.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Eyebrow("Mikser barwy", C.Gold)
                    Spacer(Modifier.height(6.dp))
                    Text("Domieszaj drugi głos, żeby dostroić barwę — np. ciepło Heart z aksamitem Bella.", style = MaterialTheme.typography.bodySmall, color = C.Text2)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(VoiceInfo("", "Brak", "", false)) + voices.filter { it.id != s.voice }) { v ->
                            val sel = s.mixVoice == v.id
                            Surface(onClick = { s.setMix(v.id, if (v.id.isEmpty()) 0f else s.mixRatio.takeIf { it > 0.05f } ?: 0.3f) },
                                shape = RoundedCornerShape(50), color = if (sel) C.GoldSoft else C.Surface2, border = BorderStroke(1.dp, if (sel) C.Gold else C.Line)) {
                                Text(v.name, style = MaterialTheme.typography.labelLarge, color = if (sel) C.Gold else C.Text2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                    if (s.mixVoice.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SliderRow("Udział drugiego głosu", s.mixRatio, 0.05f..0.7f, "${(s.mixRatio * 100).toInt()}%") { s.setMix(s.mixVoice, it) }
                        GhostButton("Posłuchaj mieszanki", Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) { preview(s.voiceSpec) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (onboarding) {
            PrimaryButton("Dalej", Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) { nav.navigate("placement_intro") }
        }
    }
}

@Composable
private fun VoiceRow(v: VoiceInfo, selected: Boolean, active: Boolean, onPlay: () -> Unit, onPick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onPick, color = if (selected) C.GoldSoft else C.Surface, border = if (selected) C.Gold else C.Line,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onPlay, shape = CircleShape, color = if (active) C.Gold else C.Surface3, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, "Odsłuchaj", tint = if (active) Color1 else C.Text) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium, color = C.Text)
                    if (v.top) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Star, "Najwyższa jakość", tint = C.Gold, modifier = Modifier.size(14.dp)) }
                }
                Text(v.desc, style = MaterialTheme.typography.bodySmall, color = C.Text2)
            }
            if (selected) Icon(Icons.Rounded.Check, "Wybrany", tint = C.Gold)
        }
    }
}
