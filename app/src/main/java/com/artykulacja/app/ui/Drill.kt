package com.artykulacja.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.speech.Practice
import com.artykulacja.app.speech.Speech
import com.artykulacja.app.ui.theme.C
import com.artykulacja.app.ui.theme.Serif
import com.artykulacja.engine.PhoneScore
import com.artykulacja.engine.Units
import com.artykulacja.engine.WordScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DrillItem(val text: String, val isolated: Boolean)

data class DrillSpec(
    val title: String,
    val eyebrow: String,
    val items: List<DrillItem>,
    val unitId: String? = null,
    val placement: Boolean = false,
    val source: String = "",
)

fun splitSentences(text: String): List<String> =
    text.replace("\n", " ").split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.any(Char::isLetter) }

fun drillSpec(kind: String, arg: String): DrillSpec {
    val c = app.course
    fun item(t: String) = DrillItem(t, !t.trim().contains(' '))
    return when (kind) {
        "unit_words" -> c.unit(arg)!!.let { u -> DrillSpec(u.title, "Słowa · /${u.target}/", u.words.map { DrillItem(it, true) }, u.id, source = "unit") }
        "unit_sent" -> c.unit(arg)!!.let { u -> DrillSpec(u.title, "Zdania · /${u.target}/", u.sentences.map { DrillItem(it, false) }, u.id, source = "unit") }
        "phrases" -> c.phrases[arg.toInt()].let { p -> DrillSpec(p.name, "Shadowing biznesowy", p.items.map { DrillItem(it, false) }, source = "phrases") }
        "daily" -> {
            val all = c.phrases.flatMap { it.items }
            val start = (LocalDate.now().toEpochDay() * 5 % all.size).toInt()
            DrillSpec("Frazy dnia", "Dzisiejsze zdania", (0 until 5).map { DrillItem(all[(start + it) % all.size], false) }, source = "daily")
        }
        "mywords" -> DrillSpec("Moje słowa", "Twoja lista", app.store.myWords.map { item(it) }, source = "mywords")
        "free" -> DrillSpec("Twój tekst", "Wolny tekst", splitSentences(arg).map { item(it) }, source = "free")
        "texts" -> DrillSpec("Powtórka", "Najsłabsze z sesji", arg.split('\n').filter { it.isNotBlank() }.map { item(it) }, source = "repeat")
        "weak" -> {
            val u = c.unitForSound(arg)
            if (u != null) DrillSpec("Głoska /$arg/", u.title, (u.words.take(6).map { DrillItem(it, true) } + u.sentences.take(4).map { DrillItem(it, false) }), u.id, source = "weak")
            else drillSpec("daily", "")
        }
        "placement" -> DrillSpec("Test poziomu", "Przeczytaj na głos", c.placement.map { DrillItem(it, false) }, placement = true, source = "placement")
        else -> DrillSpec("Ćwiczenie", "", listOf(item(arg)))
    }
}

/** Klucz głoski do statystyk opanowania. */
fun soundKey(p: PhoneScore): String = when (p.phone.ipa) {
    "ɝ" -> "ɚ"
    else -> p.phone.ipa
}

fun heardLabel(h: String): String = when (h) {
    "∅" -> "prawie niesłyszalna"
    "e" -> "polskie „e”"
    "o" -> "polskie „o”"
    "a" -> "polskie „a”"
    else -> "/$h/"
}

data class Issue(val word: WordScore, val phone: PhoneScore, val title: String, val text: String)

fun issuesOf(pr: Practice, limit: Int = 3): List<Issue> {
    val c = app.course
    val out = ArrayList<Issue>()
    val all = pr.assessment.words.flatMap { w -> w.phones.map { w to it } }
        .filter { (w, p) -> w.known && !p.phone.weak && p.score < 0.62f }
        .sortedBy { it.second.score }
    val seen = HashSet<String>()
    for ((w, p) in all) {
        val key = soundKey(p) + (p.heard ?: "")
        if (!seen.add(key)) continue
        val info = c.sound(soundKey(p)) ?: c.sound(p.phone.unit)
        val sub = p.heard?.let { c.subTip(p.phone.unit, it) }
        val title = "/${p.phone.ipa}/ w „${w.text}”" + (p.heard?.let { " — " + heardLabel(it) } ?: "")
        val text = sub ?: info?.how ?: "Posłuchaj wzorca i spróbuj jeszcze raz, wolniej."
        out += Issue(w, p, title, text)
        if (out.size >= limit) break
    }
    return out
}

/** Tylko w wersji testowej: zamiast mikrofonu użyj wzorca (do automatycznych zrzutów ekranu na emulatorze). */
suspend fun fakeMic(item: DrillItem): FloatArray? {
    if (!com.artykulacja.app.BuildConfig.DEBUG) return null
    if (!java.io.File(app.filesDir, "debug_fake_mic").exists()) return null
    val a = app.speech.speak(item.text, false, item.isolated, "am_michael")
    kotlinx.coroutines.delay(1200)
    return com.artykulacja.engine.Dsp.resample(a, 24000, 16000)
}

class DrillState(val spec: DrillSpec, private val scope: CoroutineScope) {
    enum class Phase { Idle, Recording, Analyzing, Done }

    var index by mutableIntStateOf(0)
    var phase by mutableStateOf(Phase.Idle)
    var result by mutableStateOf<Practice?>(null)
    var message by mutableStateOf<String?>(null)
    var words by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var finished by mutableStateOf(false)
    var continuous by mutableStateOf(false)
    var modelLoading by mutableStateOf(false)
    val best = mutableStateListOf<Int?>().apply { repeat(spec.items.size) { add(null) } }
    private var job: Job? = null

    val item get() = spec.items[index]

    fun prepare() {
        result = null; message = null; phase = Phase.Idle
        val tg = app.speech.target(item.text, item.isolated)
        words = tg.words.map { (t, ph) -> t.text to (if (t.known) Units.displayIpa(ph) else "") }
        // wstępne przygotowanie wzorca bieżącego i następnego elementu
        scope.launch {
            runCatching { app.speech.speak(item.text, false, item.isolated) }
            spec.items.getOrNull(index + 1)?.let { n -> runCatching { app.speech.speak(n.text, false, n.isolated) } }
        }
    }

    fun playModel(slow: Boolean) {
        scope.launch {
            modelLoading = true
            val a = runCatching { app.speech.speak(item.text, slow, item.isolated) }.getOrNull()
            modelLoading = false
            if (a != null) app.player.play(a, 24000, if (slow) "slow" else "model")
        }
    }

    fun playUser() {
        val r = result ?: return
        scope.launch { app.player.play(r.audio16k, 16000, "user") }
    }

    fun playWordModel(w: String) {
        scope.launch { app.player.play(app.speech.speak(w, false, true), 24000, "wmodel") }
    }

    fun playWordUser(ws: WordScore) {
        val r = result ?: return
        scope.launch {
            app.player.play(r.audio16k, 16000, "wuser", ((ws.startSec - 0.05f) * 16000).toInt(), ((ws.endSec + 0.08f) * 16000).toInt())
        }
    }

    fun toggleRecord() {
        if (phase == Phase.Recording) { app.recorder.stop(); return }
        if (phase == Phase.Analyzing) return
        job = scope.launch { recordOnce() }
    }

    suspend fun recordOnce() {
        app.player.stop()
        message = null
        phase = Phase.Recording
        val audio = fakeMic(item) ?: app.recorder.record()
        phase = Phase.Analyzing
        val pr = runCatching { app.speech.assess(audio, item.text, item.isolated) }.getOrElse {
            message = "Coś poszło nie tak: ${it.message}"; phase = Phase.Idle; return
        }
        if (!pr.assessment.speechFound) {
            message = "Nie słyszę Cię wyraźnie — spróbuj jeszcze raz, trochę głośniej."
            phase = Phase.Idle
            return
        }
        result = pr
        phase = Phase.Done
        val phones = pr.assessment.words.filter { it.known }.flatMap { w -> w.phones.filter { !it.phone.weak }.map { soundKey(it) to it.score } }
        app.store.recordAttempt(item.text, pr.score, phones, spec.source)
        best[index] = maxOf(best[index] ?: 0, pr.score)
    }

    fun next() {
        app.player.stop()
        if (index < spec.items.size - 1) index++ else finish()
    }

    fun prev() {
        if (index > 0) { app.player.stop(); index-- }
    }

    private fun finish() {
        continuous = false
        finished = true
        val scores = best.filterNotNull()
        val avg = if (scores.isEmpty()) 0 else scores.average().toInt()
        if (spec.placement) app.store.setPlacement(avg)
        if (spec.unitId != null && scores.size >= spec.items.size / 2) app.store.completeUnit(spec.unitId)
    }

    /** Tryb ciągły: wzorzec → sygnał → nagranie → ocena → dalej. */
    fun runContinuous() {
        job?.cancel()
        job = scope.launch {
            while (continuous && !finished) {
                val a = runCatching { app.speech.speak(item.text, false, item.isolated) }.getOrNull() ?: break
                app.player.play(a, 24000, "model")
                if (!continuous) break
                delay(250)
                app.player.beep()
                recordOnce()
                if (!continuous) break
                delay(if ((result?.score ?: 0) >= 80) 1800 else 3200)
                if (!continuous) break
                if (result != null && (result?.score ?: 0) >= 70) next()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillScreen(nav: NavHostController, kind: String, arg: String) {
    val engine by app.speech.state.collectAsState()
    if (engine != Speech.State.Ready) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Ćwiczenie", { nav.popBackStack() })
            Loading(if (engine is Speech.State.Failed) "Silnik mowy nie działa: ${(engine as Speech.State.Failed).message}" else "Przygotowuję silnik mowy…")
        }
        return
    }
    val spec = remember(kind, arg) { drillSpec(kind, arg) }
    if (spec.items.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(spec.title, { nav.popBackStack() })
            Text("Brak elementów do ćwiczenia.", color = C.Text2, modifier = Modifier.padding(24.dp))
        }
        return
    }
    val scope = rememberCoroutineScope()
    val st = remember(spec) { DrillState(spec, scope) }
    val ctx = LocalContext.current
    val settings = app.settings
    val level by app.recorder.level.collectAsState()
    val playing by app.player.playing.collectAsState()
    var sheetWord by remember { mutableStateOf<Int?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) st.toggleRecord() else st.message = "Bez dostępu do mikrofonu nie ocenię wymowy. Włącz go w ustawieniach telefonu."
    }
    fun record() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) st.toggleRecord()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(st.index) {
        st.prepare()
        if (settings.autoPlay && !spec.placement && !st.continuous) {
            delay(250)
            st.playModel(false)
        }
    }
    DisposableEffect(Unit) {
        val t0 = System.currentTimeMillis()
        onDispose {
            app.player.stop()
            app.recorder.stop()
            app.store.addSeconds(((System.currentTimeMillis() - t0) / 1000).toInt().coerceAtMost(1800))
        }
    }

    if (st.finished) {
        if (spec.placement) {
            LaunchedEffect(Unit) { nav.navigate("results") { popUpTo("placement_intro") { inclusive = true } } }
            Loading("Liczę wynik…")
            return
        }
        DrillSummary(nav, st)
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(spec.title, { nav.popBackStack() }) {
            if (!spec.placement) {
                IconButton(onClick = {
                    st.continuous = !st.continuous
                    if (st.continuous) st.runContinuous() else { app.recorder.stop(); app.player.stop() }
                }) { Icon(Icons.Rounded.AllInclusive, "Tryb ciągły", tint = if (st.continuous) C.Gold else C.Text3) }
            }
            Text("${st.index + 1} / ${spec.items.size}", style = MaterialTheme.typography.labelLarge, color = C.Text2, modifier = Modifier.padding(end = 14.dp))
        }
        LinearMeter((st.index + (if (st.phase == DrillState.Phase.Done) 1 else 0)) / spec.items.size.toFloat(), Modifier.padding(horizontal = 20.dp), height = 4.dp)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(spec.eyebrow)
                if (st.continuous) { Spacer(Modifier.width(10.dp)); Pill("Tryb ciągły", C.Gold, C.GoldSoft) }
            }
            Spacer(Modifier.height(14.dp))
            WordFlow(st.words, st.result?.assessment?.words, settings.showIpa, big = st.item.isolated) { sheetWord = it }
            Spacer(Modifier.height(16.dp))
            if (!spec.placement) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipButton(Icons.Rounded.VolumeUp, "Posłuchaj", playing == "model" || st.modelLoading) { st.playModel(false) }
                    ChipButton(Icons.Rounded.SlowMotionVideo, "Wolniej", playing == "slow") { st.playModel(true) }
                    if (st.result != null) ChipButton(Icons.Rounded.Headphones, "Ty", playing == "user") { st.playUser() }
                }
            }
            st.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = C.Mid)
            }
            val r = st.result
            AnimatedVisibility(r != null, enter = fadeIn(), exit = fadeOut()) {
                if (r != null) ResultPanel(st, r) { sheetWord = it }
            }
            if (r == null && st.phase == DrillState.Phase.Idle && st.index == 0 && !spec.placement) {
                Spacer(Modifier.height(24.dp))
                Text("Posłuchaj wzorca, a potem naciśnij mikrofon i powiedz to samo. Dotknij słowa po ocenie, żeby zobaczyć szczegóły.",
                    style = MaterialTheme.typography.bodySmall, color = C.Text3)
            }
            Spacer(Modifier.height(24.dp))
        }

        // dolny pasek sterowania
        HorizontalDivider(color = C.Line)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (st.index > 0) RoundIcon(Icons.Rounded.Replay, "Poprzednie") { st.prev() }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RecordButton(st.phase == DrillState.Phase.Recording, st.phase == DrillState.Phase.Analyzing, level) { record() }
                Text(
                    when (st.phase) {
                        DrillState.Phase.Recording -> "Słucham…"
                        DrillState.Phase.Analyzing -> "Analizuję…"
                        DrillState.Phase.Done -> "Jeszcze raz?"
                        else -> "Dotknij i mów"
                    },
                    style = MaterialTheme.typography.labelMedium, color = C.Text3
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowForward, "Dalej", active = st.phase == DrillState.Phase.Done) { st.next() }
            }
        }
    }

    val sw = sheetWord
    val res = st.result
    if (sw != null && res != null) {
        val w = res.assessment.words.getOrNull(sw)
        if (w != null) {
            ModalBottomSheet(onDismissRequest = { sheetWord = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.Surface) {
                WordDetail(st, w, res.stress[sw], res.stressOk(sw), res.stressShift(sw))
            }
        }
    }
}

@Composable
fun ChipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (active) C.GoldSoft else C.Surface2,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) C.Gold else C.Line)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) C.Gold else C.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) C.Gold else C.Text)
        }
    }
}

@Composable
private fun ResultPanel(st: DrillState, r: Practice, onWord: (Int) -> Unit) {
    Column {
        Spacer(Modifier.height(20.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(r.score, size = 92.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(scoreLabel(r.score), style = MaterialTheme.typography.headlineSmall, color = C.Text)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.intonation?.let { Pill("Melodia ${(it * 100).toInt()}%", C.Model, C.Model.copy(alpha = 0.12f)) }
                        r.wpm?.let { Pill("$it sł./min", C.Text2) }
                    }
                }
            }
        }
        val issues = issuesOf(r)
        if (issues.isEmpty() && r.score >= 80) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth(), color = C.Good.copy(alpha = 0.08f), border = C.Good.copy(alpha = 0.3f)) {
                Text("Brzmi naturalnie. Spróbuj tego samego trochę szybciej i z pewnością siebie — tak jak na spotkaniu.",
                    style = MaterialTheme.typography.bodyMedium, color = C.Text)
            }
        }
        for (iss in issues) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp), onClick = { onWord(r.assessment.words.indexOf(iss.word)) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = C.score(iss.phone.score), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(iss.title, style = MaterialTheme.typography.titleSmall, color = C.Text)
                        Spacer(Modifier.height(4.dp))
                        Text(iss.text, style = MaterialTheme.typography.bodyMedium, color = C.Text2)
                    }
                }
            }
        }
        // akcent wyrazowy — pokaż słowa z błędnym akcentem
        val wrongStress = r.stress.filter { r.stressOk(it.key) == false }
        if (wrongStress.isNotEmpty()) {
            val (wi, sr) = wrongStress.entries.first()
            val w = r.assessment.words[wi]
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Eyebrow("Akcent wyrazowy", C.Text3)
                Spacer(Modifier.height(6.dp))
                val moved = r.stressShift(wi) ?: sr.detected
                Text("„${w.text}” — akcent na sylabie ${sr.expected + 1}., u Ciebie mocniejsza wyszła ${moved + 1}.",
                    style = MaterialTheme.typography.bodyMedium, color = C.Text2)
                Spacer(Modifier.height(12.dp))
                StressBars(sr.syllables.map { it.phone.phone.ipa }, sr.syllables.map { it.prominence }, sr.expected, moved)
            }
        }
        if (r.modelContour != null && r.userContour != null && r.assessment.words.size >= 3) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Melodia zdania", C.Text3)
                    Spacer(Modifier.weight(1f))
                    r.intonation?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = C.Model) }
                }
                Spacer(Modifier.height(10.dp))
                PitchChart(r.modelContour, r.userContour, r.assessment.words.map { it.text })
                Spacer(Modifier.height(6.dp))
                Text(intonationTip(r.intonation), style = MaterialTheme.typography.bodySmall, color = C.Text3)
            }
        }
    }
}

fun intonationTip(s: Float?): String = when {
    s == null -> ""
    s >= 0.8f -> "Melodia bardzo zbliżona do wzorca."
    s >= 0.65f -> "Dobrze. Podkreśl mocniej słowa kluczowe — wyżej i dłużej."
    else -> "Polska melodia jest płaska. W angielskim głos wyraźnie idzie w górę na ważnych słowach i opada na końcu zdania."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordDetail(st: DrillState, w: WordScore, stress: com.artykulacja.engine.Prosody.StressResult?, stressOk: Boolean?, shifted: Int?) {
    val c = app.course
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.text, fontFamily = Serif, fontSize = 34.sp, color = C.Text)
                Text("/${w.ipa}/", style = MaterialTheme.typography.bodyLarge, color = C.Text3)
            }
            ScoreRing((w.score * 100).toInt(), size = 64.dp, stroke = 6.dp)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipButton(Icons.Rounded.PlayArrow, "Wzorzec") { st.playWordModel(w.text) }
            ChipButton(Icons.Rounded.Headphones, "Ty") { st.playWordUser(w) }
        }
        Spacer(Modifier.height(18.dp))
        Eyebrow("Głoski", C.Text3)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in w.phones) {
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(C.scoreSoft(p.score)).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(p.phone.ipa, fontSize = 22.sp, color = C.score(p.score), fontWeight = if (p.phone.stress == 1) FontWeight.Bold else FontWeight.Normal)
                    Text("${(p.score * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = C.Text3)
                }
            }
        }
        val bad = w.phones.filter { it.score < 0.62f && !it.phone.weak }
        for (p in bad) {
            Spacer(Modifier.height(14.dp))
            val info = c.sound(soundKey(p)) ?: c.sound(p.phone.unit)
            Text("/${p.phone.ipa}/ ${info?.name ?: ""}" + (p.heard?.let { " — słyszę ${heardLabel(it)}" } ?: ""),
                style = MaterialTheme.typography.titleSmall, color = C.Text)
            Spacer(Modifier.height(4.dp))
            Text(p.heard?.let { c.subTip(p.phone.unit, it) } ?: info?.how ?: "", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
        }
        if (stress != null) {
            Spacer(Modifier.height(18.dp))
            Eyebrow("Akcent", C.Text3)
            Spacer(Modifier.height(10.dp))
            val ok = stressOk ?: stress.correct
            StressBars(stress.syllables.map { it.phone.phone.ipa }, stress.syllables.map { it.prominence }, stress.expected, if (ok) stress.expected else (shifted ?: stress.detected))
            Spacer(Modifier.height(8.dp))
            Text(if (ok) "Akcent we właściwym miejscu." else "Akcent powinien paść na sylabę ${stress.expected + 1}. (złota).",
                style = MaterialTheme.typography.bodySmall, color = if (ok) C.Good else C.Mid)
        }
    }
}

@Composable
private fun DrillSummary(nav: NavHostController, st: DrillState) {
    val scores = st.best
    val done = scores.filterNotNull()
    val avg = if (done.isEmpty()) 0 else done.average().toInt()
    val weak = st.spec.items.indices.filter { (scores[it] ?: 0) < 80 }.map { st.spec.items[it].text }
    Column(Modifier.fillMaxSize()) {
        TopBar(st.spec.title, { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            Eyebrow("Sesja ukończona")
            Spacer(Modifier.height(14.dp))
            ScoreRing(avg, size = 140.dp, stroke = 10.dp)
            Spacer(Modifier.height(10.dp))
            Text(scoreLabel(avg), style = MaterialTheme.typography.headlineMedium, color = C.Text)
            Text("${done.size} z ${scores.size} nagranych · seria ${app.store.streak()} dni", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
            Spacer(Modifier.height(22.dp))
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)) {
                st.spec.items.forEachIndexed { i, it ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it.text, style = MaterialTheme.typography.bodyMedium, color = C.Text, modifier = Modifier.weight(1f))
                        val s = scores[i]
                        Text(s?.toString() ?: "—", style = MaterialTheme.typography.titleSmall, color = if (s == null) C.Text3 else C.score(s / 100f),
                            textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                    }
                    if (i < st.spec.items.size - 1) HorizontalDivider(color = C.Line)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.navigationBarsPadding().padding(20.dp)) {
            if (weak.isNotEmpty()) {
                PrimaryButton("Powtórz najsłabsze (${weak.size})", Modifier.fillMaxWidth()) {
                    nav.navigate("drill/texts/${enc(weak.joinToString("\n"))}") { popUpTo("main") }
                }
                Spacer(Modifier.height(10.dp))
                GhostButton("Zakończ", Modifier.fillMaxWidth()) { nav.popBackStack() }
            } else {
                PrimaryButton("Zakończ", Modifier.fillMaxWidth()) { nav.popBackStack() }
            }
        }
    }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
