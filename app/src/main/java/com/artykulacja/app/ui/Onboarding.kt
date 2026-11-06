package com.artykulacja.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.speech.Speech
import com.artykulacja.app.ui.theme.C
import com.artykulacja.app.ui.theme.Serif
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WelcomeScreen(nav: NavHostController) {
    var name by remember { mutableStateOf(app.settings.name) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Eyebrow("Artykulacja")
        Spacer(Modifier.height(14.dp))
        Text("Brzmij jak", style = MaterialTheme.typography.displayLarge, color = C.Text)
        Text("lider.", style = MaterialTheme.typography.displayLarge.copy(fontStyle = FontStyle.Italic, fontFamily = Serif), color = C.Gold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Codzienny trening wymowy amerykańskiej dla polskiej menedżerki: głoski, akcent, melodia zdania i język spotkań zarządu.",
            style = MaterialTheme.typography.bodyLarge, color = C.Text2
        )
        Spacer(Modifier.height(30.dp))
        Feature(Icons.Rounded.GraphicEq, "Ocena każdej głoski", "Kolor pokazuje, co brzmi naturalnie, a co po polsku — i co zrobić z językiem.")
        Feature(Icons.Rounded.Insights, "Melodia i akcent", "Twoja intonacja na tle wzorca native speakera.")
        Feature(Icons.Rounded.WifiOff, "W pełni offline", "Nagrania nie opuszczają telefonu. Działa w samolocie.")
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            label = { Text("Jak masz na imię?") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line, focusedLabelColor = C.Gold, cursorColor = C.Gold),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Zaczynamy", Modifier.fillMaxWidth()) {
            app.settings.setName(name.trim())
            nav.navigate(if (app.models.isReady()) "pickvoice" else "download")
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 9.dp)) {
        Surface(shape = CircleShape, color = C.GoldSoft, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.Gold, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = C.Text)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = C.Text2)
        }
    }
}

private fun mb(b: Long) = String.format(Locale("pl"), "%.0f MB", b / 1_000_000.0)

@Composable
fun DownloadScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val p by app.models.progress.collectAsState()
    val engine by app.speech.state.collectAsState()
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(p.finished) {
        if (p.finished) {
            app.startEngine()
        }
    }
    LaunchedEffect(engine) {
        if (engine == Speech.State.Ready) nav.navigate("pickvoice") { popUpTo("download") { inclusive = true } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(26.dp)) {
        Spacer(Modifier.height(40.dp))
        Surface(shape = CircleShape, color = C.GoldSoft, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CloudDownload, null, tint = C.Gold, modifier = Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(22.dp))
        Text("Głos i słuch aplikacji", style = MaterialTheme.typography.headlineLarge, color = C.Text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Jednorazowo pobierzemy dwa modele: neuronowy głos amerykański (wzorzec) i model, który słyszy każdą Twoją głoskę. " +
                "Około 690 MB — najlepiej przez Wi-Fi. Potem wszystko działa bez internetu.",
            style = MaterialTheme.typography.bodyLarge, color = C.Text2
        )
        Spacer(Modifier.height(30.dp))
        if (started || p.done > 0) {
            Panel(Modifier.fillMaxWidth()) {
                val frac = if (p.total > 0) p.done.toFloat() / p.total else 0f
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, color = C.Text)
                    Spacer(Modifier.weight(1f))
                    Text(if (p.total > 0) "${mb(p.done)} / ${mb(p.total)}" else "Łączę…", style = MaterialTheme.typography.bodyMedium, color = C.Text2)
                }
                Spacer(Modifier.height(12.dp))
                LinearMeter(frac)
                Spacer(Modifier.height(10.dp))
                val status = when {
                    p.error != null -> "Błąd: ${p.error}"
                    p.finished && engine == Speech.State.Loading -> "Przygotowuję silnik mowy…"
                    p.finished -> "Gotowe"
                    else -> p.file
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = if (p.error != null) C.Bad else C.Text3)
                if (engine is Speech.State.Failed) {
                    Text("Nie udało się uruchomić modeli: ${(engine as Speech.State.Failed).message}", style = MaterialTheme.typography.bodySmall, color = C.Bad)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (!started || p.error != null) {
            PrimaryButton(if (p.error != null) "Spróbuj ponownie" else "Pobierz (≈690 MB)", Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) {
                started = true
                scope.launch { app.models.download() }
            }
        } else {
            Text("Możesz zablokować ekran — pobieranie wznowi się, jeśli zostanie przerwane.", style = MaterialTheme.typography.bodySmall, color = C.Text3)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun PlacementIntro(nav: NavHostController) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(26.dp)) {
        Spacer(Modifier.height(40.dp))
        Surface(shape = CircleShape, color = C.GoldSoft, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.RecordVoiceOver, null, tint = C.Gold, modifier = Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(22.dp))
        Text("Test poziomu", style = MaterialTheme.typography.headlineLarge, color = C.Text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Przeczytaj na głos 8 zdań z codziennej pracy. Mów naturalnie, jak na spotkaniu — nie wolniej niż zwykle. " +
                "Na tej podstawie ułożę Twój plan i pokażę, które głoski zdradzają polski akcent.",
            style = MaterialTheme.typography.bodyLarge, color = C.Text2
        )
        Spacer(Modifier.height(18.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text("Wskazówka", style = MaterialTheme.typography.titleSmall, color = C.Gold)
            Spacer(Modifier.height(4.dp))
            Text("Trzymaj telefon ok. 20 cm od ust, w cichym miejscu. Nagrywanie kończy się samo po krótkiej ciszy.",
                style = MaterialTheme.typography.bodyMedium, color = C.Text2)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Rozpocznij test (3 min)", Modifier.fillMaxWidth()) { nav.navigate("drill/placement/0") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            app.settings.setOnboarded(true)
            nav.goMain()
        }, modifier = Modifier.fillMaxWidth()) { Text("Pomiń na razie", color = C.Text2) }
    }
}

@Composable
fun PlacementResults(nav: NavHostController) {
    val store = app.store
    val course = app.course
    val score = store.placementScore
    val weak = store.weakest(4)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Eyebrow("Twój wynik")
        Spacer(Modifier.height(16.dp))
        ScoreRing(score, size = 150.dp, stroke = 11.dp)
        Spacer(Modifier.height(12.dp))
        Text(levelName(score), style = MaterialTheme.typography.headlineMedium, color = C.Text)
        Spacer(Modifier.height(6.dp))
        Text(levelText(score), style = MaterialTheme.typography.bodyMedium, color = C.Text2, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(26.dp))
        if (weak.isNotEmpty()) {
            Text("Nad tym popracujemy najpierw", style = MaterialTheme.typography.titleMedium, color = C.Text, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                weak.forEach { (key, m) ->
                    val info = course.sound(key)
                    Panel(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("/$key/", style = MaterialTheme.typography.headlineSmall, color = C.score(m / 100f), modifier = Modifier.width(70.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info?.name ?: key, style = MaterialTheme.typography.titleSmall, color = C.Text)
                                Text(info?.example?.let { "jak w „$it”" } ?: "", style = MaterialTheme.typography.bodySmall, color = C.Text3)
                            }
                            Text("${m.toInt()}%", style = MaterialTheme.typography.titleMedium, color = C.score(m / 100f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Przejdź do planu", Modifier.fillMaxWidth()) {
            app.settings.setOnboarded(true)
            nav.goMain()
        }
    }
}

fun levelName(score: Int) = when {
    score >= 90 -> "Poziom: native-like"
    score >= 80 -> "Poziom: bardzo wyraźny"
    score >= 65 -> "Poziom: wyraźny, z akcentem"
    score >= 50 -> "Poziom: zrozumiały"
    else -> "Poziom: początek drogi"
}

fun levelText(score: Int) = when {
    score >= 90 -> "Brzmisz bardzo naturalnie. Szlifujemy melodię i niuanse."
    score >= 80 -> "Mówisz wyraźnie. Kilka głosek wciąż zdradza polski akcent — to je wyeliminujemy."
    score >= 65 -> "Rozmówcy rozumieją Cię bez trudu, ale akcent jest wyraźny. Systematyczny trening da szybki efekt."
    score >= 50 -> "Część głosek brzmi po polsku i może utrudniać zrozumienie. Zaczniemy od najważniejszych."
    else -> "Zaczniemy od podstawowych głosek i rytmu. Postępy przyjdą szybko."
}
