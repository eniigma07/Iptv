package com.artykulacja.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.data.CourseUnit
import com.artykulacja.app.speech.Speech
import com.artykulacja.app.ui.theme.C
import com.artykulacja.app.ui.theme.Serif
import kotlinx.coroutines.launch

/** Opanowanie głoski docelowej lekcji (0..100) albo null, jeśli jeszcze nie ćwiczona. */
fun unitMastery(u: CourseUnit): Float? {
    val m = app.store.mastery[u.target] ?: return null
    return if (m.second >= 3) m.first else null
}

@Composable
fun CourseScreen(nav: NavHostController) {
    val store = app.store
    @Suppress("UNUSED_VARIABLE") val v = store.version
    val units = app.course.units
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 22.dp, bottom = 18.dp)) {
                Eyebrow("Kurs wymowy")
                Spacer(Modifier.height(8.dp))
                Text("Od polskiego akcentu do pewnego głosu", style = MaterialTheme.typography.headlineLarge, color = C.Text)
                Spacer(Modifier.height(8.dp))
                Text("Lekcje ułożone według tego, co najbardziej zdradza Polaków w angielskim. Każda: zasada, słowa, pary minimalne, zdania z pracy.",
                    style = MaterialTheme.typography.bodyMedium, color = C.Text2)
            }
        }
        itemsIndexed(units) { i, u ->
            val done = store.unitDone[u.id] ?: 0
            val m = unitMastery(u)
            Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { nav.navigate("unit/${u.id}") }, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (m != null) C.scoreSoft(m / 100f) else C.Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(u.target, fontFamily = Serif, fontSize = 22.sp, color = if (m != null) C.score(m / 100f) else C.Gold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${u.title}", style = MaterialTheme.typography.titleMedium, color = C.Text)
                        Text(u.hook, style = MaterialTheme.typography.bodySmall, color = C.Text2)
                        if (m != null) {
                            Spacer(Modifier.height(8.dp))
                            LinearMeter(m / 100f, color = C.score(m / 100f), height = 4.dp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (done > 0) Icon(Icons.Rounded.CheckCircle, "Ukończone", tint = C.Good, modifier = Modifier.size(22.dp))
                    else Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Text3, modifier = Modifier.size(20.dp))
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Eyebrow("Rytm i melodia", C.Text3)
            Spacer(Modifier.height(10.dp))
            PlanCard(Icons.Rounded.VolumeUp, "Akcent wyrazowy", "${app.course.stress.size} słów z biznesu") { nav.navigate("stress") }
            Spacer(Modifier.height(10.dp))
            PlanCard(Icons.Rounded.VolumeUp, "Shadowing: język spotkań", "${app.course.phrases.sumOf { it.items.size }} fraz senior managera") { nav.navigate("phrases") }
        }
    }
}

@Composable
fun UnitScreen(nav: NavHostController, id: String) {
    val u = app.course.unit(id) ?: return
    val store = app.store
    @Suppress("UNUSED_VARIABLE") val v = store.version
    val scope = rememberCoroutineScope()
    val info = app.course.sound(u.target)
    Column(Modifier.fillMaxSize()) {
        TopBar(u.title, { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = C.GoldSoft, modifier = Modifier.size(84.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(u.target, fontFamily = Serif, fontSize = 38.sp, color = C.Gold) }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(info?.name ?: u.title, style = MaterialTheme.typography.headlineSmall, color = C.Text)
                    Text(u.hook, style = MaterialTheme.typography.bodyMedium, color = C.Text2)
                    Spacer(Modifier.height(8.dp))
                    ChipButton(Icons.Rounded.VolumeUp, "Posłuchaj: ${info?.example ?: u.words.first()}") {
                        scope.launch {
                            if (app.speech.state.value == Speech.State.Ready)
                                app.player.play(app.speech.speak(info?.example ?: u.words.first(), false, true), 24000, "ex")
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (info != null) {
                Panel(Modifier.fillMaxWidth(), color = C.Surface2) {
                    Eyebrow("Jak to zrobić", C.Gold)
                    Spacer(Modifier.height(6.dp))
                    Text(info.how, style = MaterialTheme.typography.bodyLarge, color = C.Text)
                }
                Spacer(Modifier.height(12.dp))
            }
            u.lesson.forEach {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(C.Gold))
                    Spacer(Modifier.width(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = C.Text2)
                }
            }
            Spacer(Modifier.height(22.dp))
            Eyebrow("Ćwiczenia", C.Text3)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepCard(1, "Słowa", "${u.words.size} słów · wymowa każdej głoski") { nav.navigate("drill/unit_words/${u.id}") }
                if (u.pairs.isNotEmpty()) StepCard(2, "Pary minimalne", "${u.pairs.size} par · słuch i wymowa") { nav.navigate("pairs/${u.id}") }
                StepCard(if (u.pairs.isNotEmpty()) 3 else 2, "Zdania z pracy", "${u.sentences.size} zdań · głoski, melodia, akcent") { nav.navigate("drill/unit_sent/${u.id}") }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, sub: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = C.Surface3, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.titleSmall, color = C.Text, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = C.Text)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = C.Text2)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Text3, modifier = Modifier.size(20.dp))
        }
    }
}
