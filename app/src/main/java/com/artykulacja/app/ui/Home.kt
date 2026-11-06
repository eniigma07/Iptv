package com.artykulacja.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.ui.theme.C
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(nav: NavHostController, goTab: (Int) -> Unit) {
    val store = app.store
    @Suppress("UNUSED_VARIABLE") val v = store.version
    val s = app.settings
    val course = app.course
    val hour = LocalTime.now().hour
    val hello = when (hour) { in 5..11 -> "Dzień dobry"; in 12..17 -> "Dzień dobry"; else -> "Dobry wieczór" }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("pl")))
    val weakest = store.weakest(3).filter { course.unitForSound(it.first) != null }
    val focusUnit = weakest.firstOrNull()?.let { course.unitForSound(it.first) }
        ?: course.units.firstOrNull { (store.unitDone[it.id] ?: 0) == 0 } ?: course.units.first()
    val pairsUnit = weakest.mapNotNull { course.unitForSound(it.first) }.firstOrNull { it.pairs.isNotEmpty() }
        ?: course.units.firstOrNull { it.pairs.isNotEmpty() && (store.unitDone[it.id] ?: 0) == 0 }
        ?: course.units.filter { it.pairs.isNotEmpty() }[(LocalDate.now().toEpochDay() % course.units.count { it.pairs.isNotEmpty() }).toInt()]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp).padding(top = 10.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = C.Text3, modifier = Modifier.weight(1f))
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, "Ustawienia", tint = C.Text2) }
                }
                Text(if (s.name.isNotBlank()) "$hello," else hello, style = MaterialTheme.typography.displaySmall, color = C.Text)
                if (s.name.isNotBlank()) Text(s.name, style = MaterialTheme.typography.displaySmall.copy(fontStyle = FontStyle.Italic), color = C.Gold)
                Spacer(Modifier.height(18.dp))
                GoalCard(store.todayAttempts(), s.dailyGoal, store.streak(), store.todaySeconds())
            }
        }
        Column(Modifier.padding(horizontal = 22.dp)) {
            if (!store.placementDone) {
                Spacer(Modifier.height(16.dp))
                Panel(Modifier.fillMaxWidth(), color = C.GoldSoft, border = C.GoldDeep, onClick = { nav.navigate("placement_intro") }) {
                    Text("Zrób test poziomu", style = MaterialTheme.typography.titleMedium, color = C.Gold)
                    Spacer(Modifier.height(4.dp))
                    Text("3 minuty i 8 zdań — dopasuję plan do Twoich słabych głosek.", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Plan na dziś", style = MaterialTheme.typography.headlineSmall, color = C.Text)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanCard(Icons.Rounded.RecordVoiceOver, "Głoska dnia: /${focusUnit.target}/", focusUnit.title + " · " + focusUnit.hook) { nav.navigate("unit/${focusUnit.id}") }
                PlanCard(Icons.Rounded.Campaign, "Frazy dnia", "5 zdań senior managera — shadowing z oceną melodii") { nav.navigate("drill/daily/0") }
                PlanCard(Icons.Rounded.CompareArrows, "Pary minimalne", pairsUnit.pairs.take(2).joinToString(" · ") { "${it.first} / ${it.second}" }) { nav.navigate("pairs/${pairsUnit.id}") }
                PlanCard(Icons.Rounded.Straighten, "Akcent wyrazowy", "deVELopment czy DEvelopment? Słowa z biznesu") { nav.navigate("stress") }
            }
            Spacer(Modifier.height(24.dp))
            val avg = store.recentAverage()
            if (avg != null) {
                Panel(Modifier.fillMaxWidth(), onClick = { goTab(3) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(avg, size = 70.dp, stroke = 6.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Eyebrow("Twój poziom", C.Text3)
                            Text(levelName(avg).removePrefix("Poziom: ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = C.Text)
                            if (weakest.isNotEmpty()) Text("Do poprawy: " + weakest.joinToString("  ") { "/${it.first}/" }, style = MaterialTheme.typography.bodySmall, color = C.Text2)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Text3)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GoalCard(done: Int, goal: Int, streak: Int, seconds: Int) {
    Panel(Modifier.fillMaxWidth(), color = C.Surface.copy(alpha = 0.9f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(78.dp)) {
                val frac = (done / goal.toFloat()).coerceIn(0f, 1f)
                Canvas(Modifier.size(78.dp)) {
                    val sw = 7.dp.toPx()
                    val r = Size(size.width - sw, size.height - sw)
                    drawArc(C.Surface3, -90f, 360f, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw, cap = StrokeCap.Round))
                    drawArc(C.Gold, -90f, 360f * frac, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$done", style = MaterialTheme.typography.headlineSmall, color = C.Text)
                    Text("z $goal", style = MaterialTheme.typography.labelMedium, color = C.Text3)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(if (done >= goal) "Cel dzienny osiągnięty" else "Cel dzienny: $goal nagrań", style = MaterialTheme.typography.titleMedium, color = C.Text)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = if (streak > 0) C.Gold else C.Text3, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$streak ${dni(streak)} z rzędu", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
                }
                Text("${seconds / 60} min ćwiczeń dziś", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
            }
        }
    }
}

fun dni(n: Int) = if (n == 1) "dzień" else "dni"

@Composable
fun PlanCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = C.GoldSoft, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.Gold, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = C.Text)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = C.Text2, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Text3, modifier = Modifier.size(20.dp))
        }
    }
}
