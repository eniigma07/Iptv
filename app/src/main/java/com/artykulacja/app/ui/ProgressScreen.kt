package com.artykulacja.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.ui.theme.C
import com.artykulacja.app.ui.theme.Serif
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val trackedSounds = listOf(
    "θ", "ð", "w", "v", "ɹ", "ŋ", "ʃ", "ʒ", "dʒ", "tʃ", "h", "l",
    "i", "ɪ", "ɛ", "æ", "ʌ", "ɑ", "ɔ", "ʊ", "u", "ɚ", "ə", "eɪ", "oʊ", "aɪ", "aʊ", "z", "d", "b", "ɡ"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(nav: NavHostController) {
    val store = app.store
    @Suppress("UNUSED_VARIABLE") val v = store.version
    val totalAttempts = store.attempts.size
    val weekSec = (0 until 7).sumOf { store.days[LocalDate.now().minusDays(it.toLong()).toString()]?.get(1) ?: 0 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Column(Modifier.statusBarsPadding().padding(top = 22.dp, bottom = 16.dp)) {
            Eyebrow("Postępy")
            Spacer(Modifier.height(8.dp))
            Text("Twój akcent w liczbach", style = MaterialTheme.typography.headlineLarge, color = C.Text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${store.streak()}", "dni z rzędu", Modifier.weight(1f))
            Stat("${store.bestStreak()}", "rekord serii", Modifier.weight(1f))
            Stat("${weekSec / 60}", "min w tym tyg.", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("$totalAttempts", "nagrań", Modifier.weight(1f))
            Stat(store.recentAverage()?.toString() ?: "—", "średnia (30)", Modifier.weight(1f))
            Stat(if (store.placementDone) "${store.placementScore}" else "—", "test startowy", Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))

        Panel(Modifier.fillMaxWidth()) {
            Eyebrow("Wynik dzienny · 14 dni", C.Text3)
            Spacer(Modifier.height(12.dp))
            TrendChart(store.dailyAverages(14))
        }
        Spacer(Modifier.height(14.dp))

        Panel(Modifier.fillMaxWidth()) {
            Eyebrow("Kalendarz ćwiczeń", C.Text3)
            Spacer(Modifier.height(12.dp))
            Heatmap(store.days)
        }
        Spacer(Modifier.height(14.dp))

        Panel(Modifier.fillMaxWidth()) {
            Eyebrow("Mapa głosek", C.Text3)
            Spacer(Modifier.height(4.dp))
            Text("Dotknij głoski, żeby ją przećwiczyć.", style = MaterialTheme.typography.bodySmall, color = C.Text3)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in trackedSounds) {
                    val m = store.mastery[s]
                    val known = m != null && m.second >= 3
                    val frac = (m?.first ?: 0f) / 100f
                    Column(
                        Modifier.width(62.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (known) C.scoreSoft(frac) else C.Surface2)
                            .clickable { nav.navigate("drill/weak/${enc(s)}") }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(s, fontFamily = Serif, fontSize = 20.sp, color = if (known) C.score(frac) else C.Text3)
                        Text(if (known) "${m!!.first.toInt()}" else "—", style = MaterialTheme.typography.labelMedium, color = C.Text3)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        val recent = store.attempts.takeLast(12).reversed()
        if (recent.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Eyebrow("Ostatnie nagrania", C.Text3)
                Spacer(Modifier.height(6.dp))
                recent.forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate("drill/text/${enc(a.text)}") }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.text, style = MaterialTheme.typography.bodyMedium, color = C.Text, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("${a.score}", style = MaterialTheme.typography.titleSmall, color = C.score(a.score / 100f), textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                    }
                    if (i < recent.size - 1) HorizontalDivider(color = C.Line)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Panel(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = C.Text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = C.Text3, maxLines = 1)
    }
}

@Composable
private fun TrendChart(data: List<Pair<LocalDate, Float?>>) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val w = size.width; val h = size.height
            for (g in listOf(50f, 75f, 100f)) {
                val y = h - (g - 30f) / 70f * h
                drawLine(C.Line, Offset(0f, y), Offset(w, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }
            val pts = data.mapIndexedNotNull { i, (_, v) -> v?.let { Offset(w * (i + 0.5f) / data.size, h - ((it - 30f) / 70f).coerceIn(0f, 1f) * h) } }
            if (pts.size >= 2) {
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (p in pts.drop(1)) lineTo(p.x, p.y) }
                drawPath(path, C.Gold, style = Stroke(5f, cap = StrokeCap.Round))
            }
            for (p in pts) drawCircle(C.Gold, 7f, p)
        }
        Row(Modifier.fillMaxWidth()) {
            data.forEachIndexed { i, (d, _) ->
                Text(if (i % 2 == 0) d.dayOfMonth.toString() else "", style = MaterialTheme.typography.labelMedium, color = C.Text3,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Heatmap(days: Map<String, IntArray>) {
    val today = LocalDate.now()
    val weeks = 14
    val start = today.minusDays((weeks * 7 - 1).toLong()).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    val max = (days.values.maxOfOrNull { it[0] } ?: 1).coerceAtLeast(1)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 1..7) Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (d % 2 == 1) Text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale("pl")), fontSize = 9.sp, color = C.Text3)
            }
        }
        var day = start
        while (!day.isAfter(today)) {
            val col = day
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until 7) {
                    val d = col.plusDays(k.toLong())
                    val n = days[d.toString()]?.get(0) ?: 0
                    val c = when {
                        d.isAfter(today) -> C.Bg
                        n == 0 -> C.Surface3
                        else -> C.Gold.copy(alpha = 0.3f + 0.7f * (n.toFloat() / max))
                    }
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
            }
            day = day.plusDays(7)
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(today.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("pl"))), style = MaterialTheme.typography.labelMedium, color = C.Text3)
}
