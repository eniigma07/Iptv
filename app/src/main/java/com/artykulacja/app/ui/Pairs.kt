package com.artykulacja.app.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.speech.Speech
import com.artykulacja.app.ui.theme.C
import com.artykulacja.app.ui.theme.Serif
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun PairsPickScreen(nav: NavHostController) {
    val units = app.course.units.filter { it.pairs.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Pary minimalne", { nav.popBackStack() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Słowa różniące się jedną głoską. Najpierw trenujesz ucho, potem wymowę — aplikacja rozpozna, które słowo powiedziałaś.",
                    style = MaterialTheme.typography.bodyMedium, color = C.Text2, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(units) { u ->
                PlanCard(Icons.Rounded.Hearing, u.title, u.pairs.take(3).joinToString(" · ") { "${it.first}/${it.second}" }) { nav.navigate("pairs/${u.id}") }
            }
        }
    }
}

@Composable
fun PairsScreen(nav: NavHostController, unitId: String) {
    val u = app.course.unit(unitId) ?: return
    val engine by app.speech.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Pary: ${u.title}", { nav.popBackStack() })
        TabRow(
            selectedTabIndex = tab, containerColor = C.Bg, contentColor = C.Gold,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = C.Gold) },
            divider = {}
        ) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Słuchaj", style = MaterialTheme.typography.labelLarge) }, selectedContentColor = C.Gold, unselectedContentColor = C.Text3)
            Tab(tab == 1, { tab = 1 }, text = { Text("Mów", style = MaterialTheme.typography.labelLarge) }, selectedContentColor = C.Gold, unselectedContentColor = C.Text3)
        }
        if (engine != Speech.State.Ready) { EngineWait(); return }
        if (tab == 0) ListenQuiz(u.pairs) else SpeakPairs(u.pairs)
    }
}

@Composable
private fun ListenQuiz(pairs: List<Pair<String, String>>) {
    val scope = rememberCoroutineScope()
    var round by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf<Int?>(null) }
    val rounds = 10
    val q = remember(round) {
        val p = pairs[Random.nextInt(pairs.size)]
        val flip = Random.nextBoolean()
        val options = if (flip) listOf(p.second, p.first) else listOf(p.first, p.second)
        options to Random.nextInt(2)
    }
    val (options, answer) = q
    fun play() = scope.launch { app.player.play(app.speech.speak(options[answer], false, true), 24000, "quiz") }
    LaunchedEffect(round) { answered = null; if (round < rounds) play() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (round >= rounds) {
            Spacer(Modifier.height(30.dp))
            ScoreRing(correct * 100 / rounds, size = 130.dp)
            Spacer(Modifier.height(12.dp))
            Text("$correct z $rounds poprawnie", style = MaterialTheme.typography.headlineSmall, color = C.Text)
            Spacer(Modifier.height(6.dp))
            Text(if (correct >= 9) "Świetne ucho! Przejdź do zakładki „Mów”." else "Ucho uczy się szybciej niż usta. Powtórz rundę.",
                style = MaterialTheme.typography.bodyMedium, color = C.Text2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Jeszcze raz", Modifier.fillMaxWidth()) { correct = 0; round = 0 }
            return@Column
        }
        Text("Runda ${round + 1} z $rounds", style = MaterialTheme.typography.labelMedium, color = C.Text3)
        Spacer(Modifier.height(10.dp))
        LinearMeter(round / rounds.toFloat(), height = 4.dp)
        Spacer(Modifier.height(30.dp))
        Text("Które słowo słyszysz?", style = MaterialTheme.typography.headlineSmall, color = C.Text)
        Spacer(Modifier.height(18.dp))
        ChipButton(Icons.Rounded.VolumeUp, "Odtwórz ponownie") { play() }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            options.forEachIndexed { i, w ->
                val state = answered
                val col = when {
                    state == null -> C.Surface
                    i == answer -> C.Good.copy(alpha = 0.16f)
                    i == state -> C.Bad.copy(alpha = 0.16f)
                    else -> C.Surface
                }
                val border = when {
                    state == null -> C.Line
                    i == answer -> C.Good
                    i == state -> C.Bad
                    else -> C.Line
                }
                Surface(
                    onClick = {
                        if (answered == null) {
                            answered = i
                            if (i == answer) correct++
                            scope.launch {
                                kotlinx.coroutines.delay(if (i == answer) 900 else 1800)
                                round++
                            }
                        }
                    },
                    shape = RoundedCornerShape(22.dp), color = col, border = BorderStroke(1.5.dp, border),
                    modifier = Modifier.weight(1f).height(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(w, fontFamily = Serif, fontSize = 30.sp, color = C.Text)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Wynik: $correct", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
    }
}

@Composable
private fun SpeakPairs(pairs: List<Pair<String, String>>) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val words = remember(pairs) { pairs.flatMap { listOf(it.first to it.second, it.second to it.first) }.shuffled() }
    var idx by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<Float?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    val level by app.recorder.level.collectAsState()
    val (target, other) = words[idx % words.size]

    fun go() {
        if (recording) { app.recorder.stop(); return }
        scope.launch {
            app.player.stop()
            verdict = null; msg = null
            recording = true
            val audio = fakeMic(DrillItem(target, true)) ?: app.recorder.record(maxSec = 5f, silenceSec = 0.8f)
            recording = false
            busy = true
            val p = app.speech.comparePair(audio, target, other)
            busy = false
            if (p == null) { msg = "Nie słyszę — spróbuj jeszcze raz."; return@launch }
            verdict = p
            tries++
            if (p >= 0.5f) hits++
            app.store.recordAttempt(target, (p * 100).toInt(), emptyList(), "pairs")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) go() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Powiedz:", style = MaterialTheme.typography.labelMedium, color = C.Text3)
            Spacer(Modifier.height(10.dp))
            Text(target, fontFamily = Serif, fontSize = 52.sp, color = C.Text)
            Text("/${app.speech.target(target, true).words.firstOrNull()?.second?.let { com.artykulacja.engine.Units.displayIpa(it) } ?: ""}/",
                style = MaterialTheme.typography.bodyLarge, color = C.Text3)
            Spacer(Modifier.height(6.dp))
            Text("a nie: $other", style = MaterialTheme.typography.bodyMedium, color = C.Text3)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipButton(Icons.Rounded.VolumeUp, target) { scope.launch { app.player.play(app.speech.speak(target, false, true), 24000, "a") } }
                ChipButton(Icons.Rounded.VolumeUp, other) { scope.launch { app.player.play(app.speech.speak(other, false, true), 24000, "b") } }
            }
            Spacer(Modifier.height(26.dp))
            verdict?.let { p ->
                val ok = p >= 0.5f
                Panel(Modifier.fillMaxWidth(), color = (if (ok) C.Good else C.Bad).copy(alpha = 0.10f), border = (if (ok) C.Good else C.Bad).copy(alpha = 0.4f)) {
                    Text(if (ok) "Słyszę „$target” ✓" else "Brzmiało jak „$other”", style = MaterialTheme.typography.titleLarge, color = if (ok) C.Good else C.Bad)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(other, style = MaterialTheme.typography.bodySmall, color = C.Text3, modifier = Modifier.width(70.dp))
                        LinearMeter(p, Modifier.weight(1f), color = if (ok) C.Good else C.Bad)
                        Text(target, style = MaterialTheme.typography.bodySmall, color = C.Text3, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
                    }
                }
            }
            msg?.let { Text(it, color = C.Mid, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(14.dp))
            if (tries > 0) Text("Trafienia: $hits / $tries", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            RecordButton(recording, busy, level) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) go()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowForward, "Dalej", active = verdict != null) {
                    idx++; verdict = null; msg = null
                }
            }
        }
    }
}

@Suppress("unused")
private val micIcon = Icons.Rounded.Mic
