package com.artykulacja.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artykulacja.app.ui.theme.C
import com.artykulacja.app.ui.theme.Inter
import com.artykulacja.app.ui.theme.Serif
import com.artykulacja.engine.WordScore
import kotlin.math.max
import kotlin.math.min

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = C.Surface,
    border: Color = C.Line,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    if (onClick != null) {
        Surface(modifier = modifier, shape = shape, color = color, border = BorderStroke(1.dp, border), onClick = onClick) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", tint = C.Text) }
        } else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = C.Text, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
fun Eyebrow(text: String, color: Color = C.Gold, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun Pill(text: String, color: Color = C.Text2, bg: Color = C.Surface2, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = color,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp),
        color = if (enabled) C.Gold else C.Surface3, modifier = modifier.height(56.dp)
    ) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, null, tint = Color(0xFF1A1408), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color(0xFF1A1408) else C.Text3)
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp), color = Color.Transparent,
        border = BorderStroke(1.dp, C.Line), modifier = modifier.height(52.dp)
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, null, tint = C.Text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = C.Text)
        }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, desc: String, active: Boolean = false, size: Dp = 52.dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (active) C.GoldSoft else C.Surface2,
        border = BorderStroke(1.dp, if (active) C.Gold else C.Line),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, tint = if (active) C.Gold else C.Text, modifier = Modifier.size(size * 0.44f)) }
    }
}

/** Duży przycisk nagrywania z pulsującą obwódką i poziomem głośności. */
@Composable
fun RecordButton(recording: Boolean, busy: Boolean, level: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "p")
    val p by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val lv by animateFloatAsState(level, tween(80), label = "lv")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
        if (recording) {
            Box(Modifier.size(84.dp).scale(1f + lv * 0.35f).clip(CircleShape).background(C.Bad.copy(alpha = 0.16f)))
            Box(Modifier.size(84.dp).scale(p).clip(CircleShape).border(2.dp, C.Bad.copy(alpha = 0.35f), CircleShape))
        }
        Surface(
            onClick = onClick, enabled = !busy, shape = CircleShape,
            color = if (recording) C.Bad else C.Gold, modifier = Modifier.size(80.dp),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    busy -> CircularProgressIndicator(color = Color(0xFF1A1408), strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                    recording -> Icon(Icons.Rounded.Stop, "Zatrzymaj", tint = Color.White, modifier = Modifier.size(36.dp))
                    else -> Icon(Icons.Rounded.Mic, "Nagraj", tint = Color(0xFF1A1408), modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

/** Pierścień z wynikiem 0..100. */
@Composable
fun ScoreRing(score: Int, size: Dp = 96.dp, stroke: Dp = 8.dp, label: String? = null) {
    val anim by animateFloatAsState(score / 100f, tween(900, easing = FastOutSlowInEasing), label = "ring")
    val col = C.score(score / 100f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val r = Size(this.size.width - sw, this.size.height - sw)
            drawArc(C.Surface3, -90f, 360f, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(col, -90f, 360f * anim, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(anim * 100).toInt()}", fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.34f).sp, color = C.Text)
            if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = C.Text3)
        }
    }
}

fun scoreLabel(score: Int) = when {
    score >= 90 -> "Jak native speaker"
    score >= 80 -> "Bardzo dobrze"
    score >= 65 -> "Dobrze"
    score >= 50 -> "Prawie"
    else -> "Ćwicz dalej"
}

@Composable
fun LinearMeter(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Gold, height: Dp = 6.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "m")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(C.Surface3)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

/**
 * Tekst ćwiczenia: każde słowo z zapisem fonetycznym. Po ocenie słowa i głoski są pokolorowane.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordFlow(
    words: List<Pair<String, String>>,      // (słowo, IPA) — przed oceną
    scored: List<WordScore>?,
    showIpa: Boolean,
    big: Boolean,
    onWord: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(if (big) 10.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        words.forEachIndexed { i, (w, ipa) ->
            val ws = scored?.getOrNull(i)
            val col = if (ws == null || !ws.known) C.Text else C.score(ws.score)
            Column(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (ws != null && ws.known) C.scoreSoft(ws.score) else Color.Transparent)
                    .clickable(enabled = ws != null) { onWord(i) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    w, color = col, fontFamily = Serif, fontWeight = FontWeight.Normal,
                    fontSize = if (big) 30.sp else 23.sp, lineHeight = if (big) 36.sp else 29.sp
                )
                if (showIpa && ipa.isNotEmpty()) {
                    val txt = if (ws != null && ws.known) buildAnnotatedString {
                        append("/")
                        for (p in ws.phones) {
                            withStyle(SpanStyle(color = C.score(p.score), fontWeight = if (p.phone.stress == 1) FontWeight.Bold else FontWeight.Normal,
                                textDecoration = if (p.phone.stress == 1 && ws.phones.count { it.phone.vowel } > 1) TextDecoration.Underline else null)) {
                                append(p.phone.ipa)
                            }
                        }
                        append("/")
                    } else buildAnnotatedString { withStyle(SpanStyle(color = C.Text3)) { append("/$ipa/") } }
                    Text(txt, fontFamily = Inter, fontSize = if (big) 15.sp else 13.sp, color = C.Text3)
                }
            }
        }
    }
}

/** Wykres intonacji: wzorzec vs Ty. */
@Composable
fun PitchChart(model: FloatArray?, user: FloatArray?, words: List<String>, perWord: Int = 10, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val all = listOfNotNull(model, user).flatMap { a -> a.filter { !it.isNaN() } }
            if (all.isEmpty()) return@Canvas
            val lo = min(-6f, all.min()) - 1f
            val hi = max(6f, all.max()) + 1f
            val n = max(model?.size ?: 0, user?.size ?: 0)
            if (n < 2) return@Canvas
            val w = size.width; val h = size.height
            // separatory słów
            for (k in 1 until words.size) {
                val x = w * k * perWord / n
                drawLine(C.Line, Offset(x, 0f), Offset(x, h), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            fun curve(a: FloatArray, col: Color, width: Float) {
                var path: Path? = null
                for (i in a.indices) {
                    val v = a[i]
                    if (v.isNaN()) { path?.let { drawPath(it, col, style = Stroke(width, cap = StrokeCap.Round)) }; path = null; continue }
                    val x = w * (i + 0.5f) / n
                    val y = h - (v - lo) / (hi - lo) * h
                    if (path == null) path = Path().apply { moveTo(x, y) } else path!!.lineTo(x, y)
                }
                path?.let { drawPath(it, col, style = Stroke(width, cap = StrokeCap.Round)) }
            }
            model?.let { curve(it, C.Model.copy(alpha = 0.85f), 5f) }
            user?.let { curve(it, C.Gold, 5f) }
        }
        Row(Modifier.fillMaxWidth()) {
            words.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = C.Text3, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(C.Model)); Spacer(Modifier.width(6.dp))
            Text("Wzorzec", style = MaterialTheme.typography.bodySmall, color = C.Text2)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(C.Gold)); Spacer(Modifier.width(6.dp))
            Text("Ty", style = MaterialTheme.typography.bodySmall, color = C.Text2)
        }
    }
}

/** Słupki wyróżnienia sylab (akcent). */
@Composable
fun StressBars(labels: List<String>, values: List<Float>, expected: Int, detected: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { i, l ->
            val v = values.getOrNull(i) ?: 0f
            val col = when {
                detected == null -> if (i == expected) C.Gold else C.Surface3
                i == detected && i == expected -> C.Good
                i == detected -> C.Bad
                i == expected -> C.Gold.copy(alpha = 0.5f)
                else -> C.Surface3
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(34.dp).height((18 + 52 * v).dp).clip(RoundedCornerShape(8.dp)).background(col))
                Spacer(Modifier.height(6.dp))
                Text(l, style = MaterialTheme.typography.bodyMedium, color = if (i == expected) C.Text else C.Text2,
                    fontWeight = if (i == expected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun Loading(text: String) {
    val t = rememberInfiniteTransition(label = "l")
    val a by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = C.Gold, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = C.Text2.copy(alpha = a), textAlign = TextAlign.Center)
    }
}

@Composable
fun GradientHeader(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(C.GoldSoft.copy(alpha = 0.55f), C.Bg))
        ),
        content = content
    )
}
