package com.artykulacja.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.speech.Practice
import com.artykulacja.app.speech.Speech
import com.artykulacja.app.ui.theme.C
import com.artykulacja.app.ui.theme.Serif
import com.artykulacja.engine.Prosody
import kotlinx.coroutines.launch

@Composable
fun StressScreen(nav: NavHostController) {
    val engine by app.speech.state.collectAsState()
    val words = remember { app.course.stress.shuffled() }
    var idx by remember { mutableIntStateOf(0) }
    var guess by remember { mutableStateOf<Int?>(null) }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Practice?>(null) }
    var userStress by remember { mutableStateOf<Prosody.StressResult?>(null) }
    var modelStress by remember { mutableStateOf<Prosody.StressResult?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var points by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val level by app.recorder.level.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar("Akcent wyrazowy", { nav.popBackStack() }) {
            Text("$points pkt", style = MaterialTheme.typography.labelLarge, color = C.Gold, modifier = Modifier.padding(end = 16.dp))
        }
        if (engine != Speech.State.Ready) { EngineWait(); return }
        val w = words[idx % words.size]

        fun play(slow: Boolean) = scope.launch { app.player.play(app.speech.speak(w.word, slow, true), 24000, "m") }
        fun go() {
            if (recording) { app.recorder.stop(); return }
            scope.launch {
                app.player.stop(); msg = null
                recording = true
                val audio = fakeMic(DrillItem(w.word, true)) ?: app.recorder.record(maxSec = 6f, silenceSec = 0.8f)
                recording = false; busy = true
                val pr = app.speech.assess(audio, w.word, true)
                if (!pr.assessment.speechFound) { busy = false; msg = "Nie słyszę — spróbuj jeszcze raz."; return@launch }
                val ms = pr.modelStress[0]
                result = pr
                userStress = pr.stress[0]
                modelStress = ms
                busy = false
                val ok = userStress?.let { Prosody.stressMatches(it, ms) } ?: false
                if (ok) points += 2
                val phones = pr.assessment.words.flatMap { ww -> ww.phones.filter { !it.phone.weak }.map { soundKey(it) to it.score } }
                app.store.recordAttempt(w.word, pr.score, phones, "stress")
            }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) go() }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (guess == null) "Gdzie pada akcent?" else "Teraz powiedz to słowo", style = MaterialTheme.typography.titleMedium, color = C.Text2)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                w.syllables.forEachIndexed { i, syl ->
                    val revealed = guess != null
                    val isStress = i == w.stress
                    val bg = when {
                        !revealed -> C.Surface
                        isStress -> C.GoldSoft
                        i == guess -> C.Bad.copy(alpha = 0.14f)
                        else -> C.Surface
                    }
                    val border = when {
                        !revealed -> C.Line
                        isStress -> C.Gold
                        i == guess -> C.Bad
                        else -> C.Line
                    }
                    Surface(
                        onClick = {
                            if (guess == null) {
                                guess = i
                                if (i == w.stress) points++
                                play(false)
                            }
                        },
                        shape = RoundedCornerShape(14.dp), color = bg, border = BorderStroke(1.5.dp, border)
                    ) {
                        Text(
                            if (revealed && isStress) syl.uppercase() else syl,
                            fontFamily = Serif, fontSize = 28.sp,
                            fontWeight = if (revealed && isStress) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (revealed && isStress) C.Gold else C.Text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (guess != null) {
                Text(if (guess == w.stress) "Dobrze!" else "Akcent pada na „${w.syllables[w.stress]}”.",
                    style = MaterialTheme.typography.titleMedium, color = if (guess == w.stress) C.Good else C.Mid)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipButton(Icons.Rounded.VolumeUp, "Wzorzec") { play(false) }
                    ChipButton(Icons.Rounded.SlowMotionVideo, "Wolniej") { play(true) }
                }
            }
            msg?.let { Spacer(Modifier.height(12.dp)); Text(it, color = C.Mid, style = MaterialTheme.typography.bodyMedium) }
            val us = userStress
            val r = result
            if (r != null) {
                Spacer(Modifier.height(22.dp))
                Panel(Modifier.fillMaxWidth()) {
                    val ok = us?.let { Prosody.stressMatches(it, modelStress) } ?: false
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (us == null) "Nie udało się zmierzyć akcentu" else if (ok) "Akcent we właściwym miejscu" else "Akcent przesunięty",
                                style = MaterialTheme.typography.titleMedium, color = if (us == null) C.Text2 else if (ok) C.Good else C.Bad)
                            Text("Wymowa głosek: ${r.score}", style = MaterialTheme.typography.bodySmall, color = C.Text3)
                        }
                        ScoreRing(r.score, size = 56.dp, stroke = 5.dp)
                    }
                    if (us != null) {
                        Spacer(Modifier.height(16.dp))
                        modelStress?.let { ms ->
                            Text("Wzorzec", style = MaterialTheme.typography.labelMedium, color = C.Model)
                            Spacer(Modifier.height(6.dp))
                            StressBars(w.syllables.take(ms.syllables.size), ms.syllables.map { it.prominence }, w.stress, null)
                            Spacer(Modifier.height(14.dp))
                        }
                        Text("Ty", style = MaterialTheme.typography.labelMedium, color = C.Gold)
                        Spacer(Modifier.height(6.dp))
                        StressBars(w.syllables.take(us.syllables.size), us.syllables.map { it.prominence }, w.stress, if (ok) w.stress else (Prosody.shiftedTo(us, modelStress) ?: us.detected))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            RecordButton(recording, busy, level) {
                if (guess == null) { msg = "Najpierw wskaż sylabę z akcentem."; return@RecordButton }
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) go()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowForward, "Dalej", active = result != null) {
                    app.player.stop()
                    idx++; guess = null; result = null; userStress = null; modelStress = null; msg = null
                }
            }
        }
    }
}
